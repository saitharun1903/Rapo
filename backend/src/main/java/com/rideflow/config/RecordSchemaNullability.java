package com.rideflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/**
 * Makes the generated schemas say what the JSON actually contains. Jackson writes every record component,
 * nulls included, so each property is marked required; components annotated with JSpecify {@link Nullable}
 * are marked as allowing {@code null}. Without this, springdoc marks nothing required and a typed client would
 * have to treat every field as possibly missing.
 *
 * <p>Schemas are matched to records by name (the {@code @Schema} name or the simple class name), so two
 * records that would produce the same schema name fail here instead of silently sharing one schema.
 */
final class RecordSchemaNullability implements OpenApiCustomizer {

    private static final String NULL_TYPE = "null";

    private final Map<String, Class<?>> recordsBySchemaName;
    private final Map<String, List<Class<?>>> duplicates;

    RecordSchemaNullability(String... basePackages) {
        Map<String, List<Class<?>>> byName = new HashMap<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) ->
                Record.class.getName().equals(reader.getClassMetadata().getSuperClassName()));
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), getClass().getClassLoader());
                byName.computeIfAbsent(schemaName(type), name -> new ArrayList<>()).add(type);
            }
        }
        this.recordsBySchemaName = new HashMap<>();
        this.duplicates = new HashMap<>();
        byName.forEach((name, types) -> {
            if (types.size() == 1) {
                recordsBySchemaName.put(name, types.getFirst());
            } else {
                duplicates.put(name, types);
            }
        });
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().getSchemas().forEach((name, schema) -> {
            if (duplicates.containsKey(name)) {
                throw new IllegalStateException("Records " + duplicates.get(name) + " all map to schema '" + name
                        + "'; give them distinct @Schema(name = ...)");
            }
            Class<?> type = recordsBySchemaName.get(name);
            if (type != null && schema.getProperties() != null) {
                apply(type, schema);
            }
        });
    }

    private static void apply(Class<?> type, Schema<?> schema) {
        for (RecordComponent component : type.getRecordComponents()) {
            Schema<?> property = schema.getProperties().get(component.getName());
            if (property == null) {
                continue;
            }
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                schema.getProperties().put(component.getName(), allowNull(property));
            } else if (schema.getRequired() == null || !schema.getRequired().contains(component.getName())) {
                schema.addRequiredItem(component.getName());
            }
        }
    }

    private static Schema<?> allowNull(Schema<?> property) {
        if (property.get$ref() != null) {
            return new JsonSchema().anyOf(List.of(new JsonSchema().$ref(property.get$ref()),
                    new JsonSchema().types(Set.of(NULL_TYPE))));
        }
        Set<String> types = new LinkedHashSet<>();
        if (property.getTypes() != null) {
            types.addAll(property.getTypes());
        } else if (property.getType() != null) {
            types.add(property.getType());
        }
        types.add(NULL_TYPE);
        property.setTypes(types);
        return property;
    }

    private static String schemaName(Class<?> type) {
        io.swagger.v3.oas.annotations.media.Schema annotation =
                type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        return annotation != null && !annotation.name().isBlank() ? annotation.name() : type.getSimpleName();
    }
}

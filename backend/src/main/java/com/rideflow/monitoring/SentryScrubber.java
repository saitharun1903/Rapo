package com.rideflow.monitoring;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Last stop before an event or breadcrumb leaves for Sentry, on top of {@code send-default-pii: false}. Requests
 * keep their method, path and a few harmless headers: cookies, bodies, query strings and every other header
 * (Authorization above all) are dropped. The user is reduced to its id. Email addresses, JWTs and bearer
 * tokens are masked wherever free text can carry them: messages, their arguments, exception messages and
 * breadcrumbs.
 */
@Component
public class SentryScrubber implements SentryOptions.BeforeSendCallback, SentryOptions.BeforeBreadcrumbCallback {

    static final String REDACTED = "[redacted]";
    private static final String URL_KEY = "url";

    private static final Set<String> KEPT_HEADERS = Set.of(
            "accept", "content-type", "user-agent", RequestIdFilter.HEADER.toLowerCase(Locale.ROOT));
    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"),
            // A JWT: three base64url segments, the first a JSON header ("eyJ" is base64 for '{"').
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*"),
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        Request request = event.getRequest();
        if (request != null) {
            event.setRequest(scrub(request));
        }
        User user = event.getUser();
        if (user != null) {
            User idOnly = new User();
            idOnly.setId(user.getId());
            event.setUser(idOnly);
        }
        Message message = event.getMessage();
        if (message != null) {
            message.setMessage(mask(message.getMessage()));
            message.setFormatted(mask(message.getFormatted()));
            if (message.getParams() != null) {
                message.setParams(message.getParams().stream().map(SentryScrubber::mask).toList());
            }
        }
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            exceptions.forEach(exception -> exception.setValue(mask(exception.getValue())));
        }
        List<Breadcrumb> breadcrumbs = event.getBreadcrumbs();
        if (breadcrumbs != null) {
            breadcrumbs.forEach(SentryScrubber::scrub);
        }
        return event;
    }

    @Override
    public Breadcrumb execute(Breadcrumb breadcrumb, Hint hint) {
        scrub(breadcrumb);
        return breadcrumb;
    }

    private static Request scrub(Request request) {
        Request kept = new Request();
        kept.setMethod(request.getMethod());
        kept.setUrl(withoutQuery(request.getUrl()));
        if (request.getHeaders() != null) {
            Map<String, String> headers = new LinkedHashMap<>();
            request.getHeaders().forEach((name, value) -> {
                if (KEPT_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    headers.put(name, value);
                }
            });
            kept.setHeaders(headers);
        }
        return kept;
    }

    private static void scrub(Breadcrumb breadcrumb) {
        breadcrumb.setMessage(mask(breadcrumb.getMessage()));
        // HTTP breadcrumbs carry the called URL under "url".
        breadcrumb.getData().replaceAll((key, value) -> value instanceof String text && URL_KEY.equals(key)
                ? mask(withoutQuery(text))
                : scrubValue(value));
    }

    /** Masks every string, however deeply nested in lists and maps. */
    private static Object scrubValue(Object value) {
        return switch (value) {
            case String text -> mask(text);
            case Collection<?> items -> items.stream().map(SentryScrubber::scrubValue).toList();
            case Map<?, ?> entries -> {
                Map<Object, Object> scrubbed = new LinkedHashMap<>();
                entries.forEach((key, item) -> scrubbed.put(key, scrubValue(item)));
                yield scrubbed;
            }
            case null, default -> value;
        };
    }

    private static String withoutQuery(String url) {
        if (url == null) {
            return null;
        }
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    static String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = text;
        for (Pattern secret : SECRETS) {
            masked = secret.matcher(masked).replaceAll(REDACTED);
        }
        return masked;
    }
}

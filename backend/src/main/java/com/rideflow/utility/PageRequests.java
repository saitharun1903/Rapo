package com.rideflow.utility;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} from {@code page}, {@code size} and {@code sort=field,dir} parameters. Sort
 * fields must be in an explicit allow-list mapping API names to entity properties, so clients can
 * never sort by arbitrary (or unindexed) columns.
 */
public final class PageRequests {

    private static final String SORT_SEPARATOR = ",";

    private PageRequests() {
    }

    public static Pageable of(int page, int size, String sort, Map<String, String> allowedSortFields) {
        return PageRequest.of(page, size, parseSort(sort, allowedSortFields));
    }

    private static Sort parseSort(String sort, Map<String, String> allowedSortFields) {
        String[] parts = sort.split(SORT_SEPARATOR, -1);
        String property = allowedSortFields.get(parts[0].trim());
        if (property == null || parts.length > 2) {
            throw invalid(sort, allowedSortFields);
        }
        if (parts.length == 1) {
            return Sort.by(Sort.Direction.ASC, property);
        }
        return Sort.Direction.fromOptionalString(parts[1].trim().toUpperCase(Locale.ROOT))
                .map(direction -> Sort.by(direction, property))
                .orElseThrow(() -> invalid(sort, allowedSortFields));
    }

    private static RideFlowException invalid(String sort, Map<String, String> allowedSortFields) {
        return new RideFlowException(ErrorCode.INVALID_SORT_FIELD,
                "Unsupported sort '" + sort + "'. Allowed fields: " + allowedSortFields.keySet()
                        + " with direction asc or desc");
    }
}

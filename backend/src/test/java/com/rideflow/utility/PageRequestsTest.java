package com.rideflow.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    private static final Map<String, String> ALLOWED = Map.of("requestedAt", "requestedAt", "name", "user.fullName");

    @Test
    void mapsApiFieldToEntityPropertyWithDirection() {
        Pageable pageable = PageRequests.of(2, 25, "name,desc", ALLOWED);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "user.fullName"));
    }

    @Test
    void defaultsToAscendingWhenDirectionOmitted() {
        assertThat(PageRequests.of(0, 10, "requestedAt", ALLOWED).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "requestedAt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password,asc", "requestedAt,sideways", "requestedAt,asc,extra", ",asc", "user.fullName"})
    void rejectsFieldsOrDirectionsOutsideAllowList(String sort) {
        assertThatThrownBy(() -> PageRequests.of(0, 10, sort, ALLOWED))
                .isInstanceOf(RideFlowException.class)
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
    }
}

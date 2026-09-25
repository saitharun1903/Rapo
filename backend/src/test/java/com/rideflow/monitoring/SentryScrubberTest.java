package com.rideflow.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SentryScrubberTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";
    private static final String EMAIL = "ananya@rideflow.example.com";

    private final SentryScrubber scrubber = new SentryScrubber();

    @Test
    void requestsKeepMethodPathAndHarmlessHeadersOnly() {
        Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://api.example.com/api/auth/refresh?token=secret");
        request.setQueryString("token=secret");
        request.setCookies("rf_refresh=opaque");
        request.setData("{\"password\":\"hunter2\"}");
        request.setHeaders(Map.of("Authorization", "Bearer " + JWT, "Cookie", "rf_refresh=opaque",
                "User-Agent", "Mozilla/5.0", "X-Request-Id", "abc12345", "X-Forwarded-For", "203.0.113.9"));
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        Request sent = scrubber.execute(event, new Hint()).getRequest();

        assertThat(sent.getMethod()).isEqualTo("POST");
        assertThat(sent.getUrl()).isEqualTo("https://api.example.com/api/auth/refresh");
        assertThat(sent.getQueryString()).isNull();
        assertThat(sent.getCookies()).isNull();
        assertThat(sent.getData()).isNull();
        assertThat(sent.getHeaders()).containsOnlyKeys("User-Agent", "X-Request-Id");
    }

    @Test
    void theUserIsReducedToItsId() {
        User user = new User();
        user.setId("3aa83a29-7844-492c-9790-ce7c0d8052ff");
        user.setEmail(EMAIL);
        user.setIpAddress("203.0.113.9");
        user.setUsername("ananya");
        SentryEvent event = new SentryEvent();
        event.setUser(user);

        User sent = scrubber.execute(event, new Hint()).getUser();

        assertThat(sent.getId()).isEqualTo(user.getId());
        assertThat(sent.getEmail()).isNull();
        assertThat(sent.getIpAddress()).isNull();
        assertThat(sent.getUsername()).isNull();
    }

    @Test
    void emailsAndTokensAreMaskedInMessagesExceptionsAndBreadcrumbs() {
        Message message = new Message();
        message.setMessage("Login failed for {} with {}");
        message.setFormatted("Login failed for " + EMAIL + " with Bearer " + JWT);
        message.setParams(List.of(EMAIL, "Bearer " + JWT));
        SentryException exception = new SentryException();
        exception.setValue("Duplicate key (email)=(" + EMAIL + ")");
        Breadcrumb breadcrumb = new Breadcrumb("Registered " + EMAIL);
        breadcrumb.setData("url", "https://nominatim.example.org/search?q=" + EMAIL);
        breadcrumb.setData("status_code", 200);
        breadcrumb.setData("arguments", List.of("Refresh failed for " + EMAIL, Map.of("token", JWT)));
        SentryEvent event = new SentryEvent();
        event.setMessage(message);
        event.setExceptions(List.of(exception));
        event.setBreadcrumbs(List.of(breadcrumb));

        SentryEvent sent = scrubber.execute(event, new Hint());

        assertThat(sent.getMessage().getMessage()).isEqualTo("Login failed for {} with {}");
        assertThat(sent.getMessage().getFormatted()).isEqualTo("Login failed for [redacted] with [redacted]");
        assertThat(sent.getMessage().getParams()).containsExactly("[redacted]", "[redacted]");
        assertThat(sent.getExceptions().getFirst().getValue()).isEqualTo("Duplicate key (email)=([redacted])");
        Breadcrumb sentBreadcrumb = sent.getBreadcrumbs().getFirst();
        assertThat(sentBreadcrumb.getMessage()).isEqualTo("Registered [redacted]");
        assertThat(sentBreadcrumb.getData("url")).isEqualTo("https://nominatim.example.org/search");
        assertThat(sentBreadcrumb.getData("status_code")).isEqualTo(200);
        assertThat(sentBreadcrumb.getData("arguments"))
                .isEqualTo(List.of("Refresh failed for [redacted]", Map.of("token", "[redacted]")));
    }

    @Test
    void breadcrumbsRecordedOutsideEventsAreScrubbedToo() {
        Breadcrumb breadcrumb = new Breadcrumb("Token " + JWT + " rejected");

        assertThat(scrubber.execute(breadcrumb, new Hint()).getMessage()).isEqualTo("Token [redacted] rejected");
    }

    @Test
    void ordinaryTextIsLeftAlone() {
        String text = "Ride 7ec03945-094a-48d0-b030-e3731c05ae42 completed: 5230 m, fare 212.00 INR";

        assertThat(SentryScrubber.mask(text)).isEqualTo(text);
        assertThat(SentryScrubber.mask(null)).isNull();
    }
}

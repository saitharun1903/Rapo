package com.rideflow.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StompErrorFrameHandlerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final StompErrorFrameHandler handler = new StompErrorFrameHandler(jsonMapper);

    private static Message<byte[]> clientFrame(String destination, String receipt) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setReceipt(receipt);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private JsonNode body(Message<byte[]> error) {
        return jsonMapper.readTree(new String(error.getPayload(), StandardCharsets.UTF_8));
    }

    @Test
    void domainErrorsKeepTheirCodeAndMessageAndEchoTheReceipt() {
        // Interceptor exceptions reach the handler wrapped by the channel.
        MessageDeliveryException wrapped = new MessageDeliveryException(clientFrame("/topic/admin/activity", "r-1"),
                "Failed to send", new RideFlowException(ErrorCode.FORBIDDEN, "Not allowed to subscribe to /topic/admin/activity"));

        Message<byte[]> error = handler.handleClientMessageProcessingError(clientFrame("/topic/admin/activity", "r-1"), wrapped);

        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(error, StompHeaderAccessor.class);
        assertThat(headers.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(headers.getMessage()).isEqualTo("FORBIDDEN");
        assertThat(headers.getReceiptId()).isEqualTo("r-1");
        assertThat(body(error).get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(body(error).get("destination").asString()).isEqualTo("/topic/admin/activity");
    }

    @Test
    void unexpectedErrorsNeverLeakInternalDetails() {
        IllegalStateException internal = new IllegalStateException("connection pool exhausted at db-host:5432");

        Message<byte[]> error = handler.handleClientMessageProcessingError(clientFrame("/user/queue/rides", null), internal);

        String payload = new String(error.getPayload(), StandardCharsets.UTF_8);
        assertThat(body(error).get("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(payload).doesNotContain("db-host").doesNotContain("IllegalStateException");
        assertThat(MessageHeaderAccessor.getAccessor(error, StompHeaderAccessor.class).getMessage())
                .isEqualTo("INTERNAL_ERROR");
    }
}

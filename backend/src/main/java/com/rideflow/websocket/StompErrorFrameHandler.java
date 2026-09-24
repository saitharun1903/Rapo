package com.rideflow.websocket;

import com.rideflow.dto.realtime.StompErrorMessage;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the ERROR frame sent when a client frame is rejected (the connection is then closed). The default
 * handler copies the exception message into the frame; this one sends only the stable error code and a
 * client-safe message, like the REST {@code ApiError}, and logs unexpected failures server-side.
 */
@Component
public class StompErrorFrameHandler extends StompSubProtocolErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(StompErrorFrameHandler.class);
    private static final String UNEXPECTED_MESSAGE = "Unexpected server error";

    private final JsonMapper jsonMapper;

    public StompErrorFrameHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        StompHeaderAccessor client = clientMessage == null ? null
                : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
        String destination = client == null ? null : client.getDestination();

        RideFlowException known = findCause(ex);
        StompErrorMessage error;
        if (known != null) {
            log.debug("STOMP frame rejected with {}: {}", known.code(), known.getMessage());
            error = new StompErrorMessage(known.code().name(), known.getMessage(), destination, List.of());
        } else {
            log.error("Unexpected error processing STOMP frame for {}", destination, ex);
            error = new StompErrorMessage(ErrorCode.INTERNAL_ERROR.name(), UNEXPECTED_MESSAGE, destination, List.of());
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(error.code());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        if (client != null && client.getReceipt() != null) {
            accessor.setReceiptId(client.getReceipt());
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(jsonMapper.writeValueAsBytes(error), accessor.getMessageHeaders());
    }

    private static RideFlowException findCause(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof RideFlowException domain) {
                return domain;
            }
        }
        return null;
    }
}

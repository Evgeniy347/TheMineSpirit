package com.theminespirit.neoforge.board;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TicketIsolation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketIsolation.class);
    private static final Map<String, String> LAST_ERRORS = new ConcurrentHashMap<>();

    private TicketIsolation() {}

    static boolean post(String ticketId, BooleanSupplier post) {
        try {
            boolean posted = post.getAsBoolean();
            LAST_ERRORS.remove(ticketId);
            return posted;
        } catch (RuntimeException failure) {
            String message = String.valueOf(failure.getMessage());
            if (!message.equals(LAST_ERRORS.put(ticketId, message))) {
                LOGGER.error(
                        "[incident] задание графа {} не поставлено, остальные идут: {}", ticketId, message, failure);
            }
            return false;
        }
    }
}

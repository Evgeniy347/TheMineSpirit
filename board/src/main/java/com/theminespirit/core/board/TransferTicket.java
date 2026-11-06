package com.theminespirit.core.board;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class TransferTicket {
    public static final String TO = "to=";
    public static final String ITEM = "item=";
    public static final String COUNT = "count=";
    public static final String FROM = "from=";
    public static final String WAREHOUSE = "warehouse";

    private TransferTicket() {}

    public static List<String> modifiers(UUID to, String item, int count, @Nullable UUID from) {
        return List.of(TO + to, ITEM + item, COUNT + count, FROM + (from == null ? WAREHOUSE : from.toString()));
    }

    public static TicketTarget restore(BoardTicket ticket, TicketTarget target) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM || target.transferTo() != null) {
            return target;
        }
        String to = value(ticket, TO);
        String item = value(ticket, ITEM);
        String count = value(ticket, COUNT);
        if (to == null || item == null || count == null) {
            throw new IllegalStateException("Передача " + ticket.id() + " без получателя ни в цели, ни в метках "
                    + ticket.modifiers() + ": задание создано мимо TransferTicket.modifiers, проверьте постановку");
        }
        String from = value(ticket, FROM);
        UUID source = from == null || WAREHOUSE.equals(from) ? null : UUID.fromString(from);
        return target.withTransfer(source, UUID.fromString(to), item, Integer.parseInt(count));
    }

    public static Optional<UUID> recipient(BoardTicket ticket) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM) {
            return Optional.empty();
        }
        String to = value(ticket, TO);
        return to == null ? Optional.empty() : Optional.of(UUID.fromString(to));
    }

    private static @Nullable String value(BoardTicket ticket, String prefix) {
        for (String modifier : ticket.modifiers()) {
            if (modifier.startsWith(prefix)) {
                return modifier.substring(prefix.length());
            }
        }
        return null;
    }
}

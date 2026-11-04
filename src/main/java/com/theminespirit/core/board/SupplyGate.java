package com.theminespirit.core.board;

import com.theminespirit.core.mining.LeftOreRules;
import java.util.Objects;
import java.util.Set;

public final class SupplyGate {

    private SupplyGate() {}

    public static boolean retains(BoardTicket ticket, Set<String> liveCauses) {
        Objects.requireNonNull(ticket, "Задание не может быть null: удержание считается по причине и id");
        Objects.requireNonNull(liveCauses, "Живые причины не могут быть null: пустой набор задают пустым");
        if (isToolTierSupply(ticket.id())) {
            return false;
        }
        return BoardTicket.ALWAYS_CAUSE.equals(ticket.cause()) || liveCauses.contains(ticket.cause());
    }

    public static boolean isToolTierSupply(String ticketId) {
        Objects.requireNonNull(ticketId, "Id задания не может быть null: маркер кирки ищется в его предмете");
        int colon = ticketId.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String prefix = ticketId.substring(0, colon);
        return ("supply".equals(prefix) || "supply_gather".equals(prefix))
                && LeftOreRules.isToolTier(ticketId.substring(colon + 1));
    }
}

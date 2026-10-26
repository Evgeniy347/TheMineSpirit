package com.theminespirit.core.board;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ToolFallback {
    private ToolFallback() {}

    public static Set<String> allowed(
            Set<String> ownCraftable, Collection<BoardTicket> openToolTickets, long now, long stallTicks) {
        Objects.requireNonNull(ownCraftable, "Скрафчиваемое из инвентаря не может быть null: пустое — пустым");
        Objects.requireNonNull(openToolTickets, "Открытые заказы набора не могут быть null: пустые — пустым списком");
        Set<String> allowed = new HashSet<>();
        for (String item : ownCraftable) {
            if (stalledAbove(item, openToolTickets, now, stallTicks)) {
                allowed.add(item);
            }
        }
        return Set.copyOf(allowed);
    }

    private static boolean stalledAbove(String item, Collection<BoardTicket> tickets, long now, long stallTicks) {
        Optional<String> kind = ToolsConfig.kindOf(item);
        Optional<String> tier = ToolsConfig.tierOf(item);
        if (kind.isEmpty() || tier.isEmpty()) {
            return false;
        }
        int rank = ToolsConfig.rank(tier.orElseThrow());
        for (BoardTicket ticket : tickets) {
            if (!ToolSet.isTool(ticket.boardCase()) || now - ticket.createdAt() < stallTicks) {
                continue;
            }
            String ordered = ToolSet.itemId(ticket.boardCase());
            boolean sameKind = kind.equals(ToolsConfig.kindOf(ordered));
            boolean higher = ToolsConfig.tierOf(ordered).map(ToolsConfig::rank).orElse(-1) > rank;
            if (sameKind && higher) {
                return true;
            }
        }
        return false;
    }
}

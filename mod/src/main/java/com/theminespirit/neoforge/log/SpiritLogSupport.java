package com.theminespirit.neoforge.log;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.log.LogRecord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

final class SpiritLogSupport {
    private SpiritLogSupport() {}

    static String ticketOf(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return "";
        }
        return BoardRegistry.peek(level)
                .map(service -> service.board().assignedTo(spirit.getUUID()))
                .filter(tickets -> !tickets.isEmpty())
                .map(tickets -> tickets.get(0).id())
                .orElse("");
    }

    static int attempts(ServerLevel level, String ticket) {
        return BoardRegistry.peek(level)
                .map(service -> service.board().ticketFailures(ticket))
                .orElse(0);
    }

    static String graphSource(BoardTicket ticket) {
        return graphSource(ticket.cause());
    }

    static String graphSource(String cause) {
        return cause.startsWith("graph:") ? cause : "";
    }

    static String reason(BoardTicket ticket) {
        return ticket.modifiers().stream()
                .filter(modifier -> modifier.startsWith("reason="))
                .map(modifier -> modifier.substring("reason=".length()))
                .findFirst()
                .orElse("");
    }

    static void spirit(ServerLevel level, @Nullable String holder, LogRecord.Builder record) {
        if (holder == null) {
            return;
        }
        if (!holder.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            record.put("spirit", holder);
            return;
        }
        Entity entity = level.getEntity(UUID.fromString(holder));
        if (entity instanceof SpiritEntity spirit) {
            record.spirit(spirit.displayName(), holder);
        } else {
            record.put("spirit_id", holder);
        }
    }
}

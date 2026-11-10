package com.theminespirit.neoforge.log;

import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.log.BlockAction;
import com.theminespirit.core.log.LogCause;
import com.theminespirit.core.log.LogJson;
import com.theminespirit.core.log.LogRecord;
import com.theminespirit.core.log.LogType;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class SpiritLog {
    private static final long REFUSAL_DEDUPE_TICKS = 200;
    private static final int MAX_DEDUPE_KEYS = 4096;
    private static final String REASON = "reason";
    private static final Map<String, Long> REFUSALS = new ConcurrentHashMap<>();

    private SpiritLog() {}

    public record BlockChange(
            String actorType,
            String actor,
            String action,
            BlockState before,
            BlockState after,
            BlockPos pos,
            String basis,
            LogCause cause,
            String reason) {}

    public static void block(ServerLevel level, @Nullable SpiritEntity spirit, BlockChange change) {
        LogRecord.Builder record = ModLog.record(LogType.BLOCK_CHANGE, System.currentTimeMillis(), level.getGameTime());
        if (record == null) {
            return;
        }
        record.put("actor_type", change.actorType())
                .put("actor", change.actor())
                .put("action", BlockAction.of(change.cause(), change.action()))
                .put("block_before", change.before().toString())
                .put("block_after", change.after().toString())
                .at(change.pos().getX(), change.pos().getY(), change.pos().getZ())
                .put("basis", change.basis())
                .cause(change.cause())
                .put(REASON, change.reason());
        if (spirit != null) {
            record.put("ticket", SpiritLogSupport.ticketOf(spirit));
        }
        ModLog.append(record);
    }

    public static void blockRefused(
            ServerLevel level,
            @Nullable SpiritEntity spirit,
            BlockPos pos,
            String action,
            String basis,
            String reason) {
        String key = level.dimension().location() + "|" + pos + "|" + (spirit == null ? "мир" : spirit.getUUID()) + "|"
                + action + "|" + reason;
        long now = level.getGameTime();
        Long seen = REFUSALS.get(key);
        if (seen != null && now - seen < REFUSAL_DEDUPE_TICKS) {
            return;
        }
        if (REFUSALS.size() > MAX_DEDUPE_KEYS) {
            REFUSALS.clear();
        }
        REFUSALS.put(key, now);
        BlockState state = level.getBlockState(pos);
        block(
                level,
                spirit,
                new BlockChange(
                        spirit == null ? "world" : "spirit",
                        spirit == null ? "мир" : spirit.displayName(),
                        action,
                        state,
                        state,
                        pos,
                        basis,
                        LogCause.REFUSE,
                        reason));
    }

    public static void playerBlock(
            ServerLevel level,
            String actor,
            String action,
            BlockState before,
            BlockState after,
            BlockPos pos,
            String basis) {
        block(level, null, new BlockChange("player", actor, action, before, after, pos, basis, LogCause.BREAK, ""));
    }

    public static void taskAssigned(ServerLevel level, BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        LogRecord.Builder record = ModLog.record(LogType.TASK_EVENT, System.currentTimeMillis(), level.getGameTime());
        if (record == null) {
            return;
        }
        record.ticket(ticket.id())
                .put("board_case", ticket.boardCase().name())
                .put("event", "ASSIGNED")
                .spirit(spirit.displayName(), spirit.getUUID().toString())
                .put("priority", ticket.priority())
                .put("attempts", SpiritLogSupport.attempts(level, ticket.id()))
                .cause(LogCause.CHOSEN)
                .put(REASON, "")
                .put("source_node", SpiritLogSupport.graphSource(ticket))
                .put("target_x", target.target().x())
                .put("target_y", target.target().y())
                .put("target_z", target.target().z());
        ModLog.append(record);
    }

    public static void taskEntry(ServerLevel level, BoardLogEntry entry) {
        if (entry.event() != BoardLog.Event.BLOCKED && entry.event() != BoardLog.Event.UNBLOCKED) {
            return;
        }
        LogRecord.Builder record = ModLog.record(LogType.TASK_EVENT, System.currentTimeMillis(), entry.tick());
        if (record == null) {
            return;
        }
        record.ticket(entry.ticketId())
                .put("board_case", entry.boardCase().name())
                .put("event", entry.event().name())
                .put("priority", entry.effectivePriority())
                .cause(entry.event() == BoardLog.Event.BLOCKED ? LogCause.BLOCKED : LogCause.UNBLOCKED)
                .put(REASON, String.join("; ", entry.modifiers()))
                .put("source_node", SpiritLogSupport.graphSource(entry.cause()));
        SpiritLogSupport.spirit(level, entry.spirit(), record);
        ModLog.append(record);
    }

    public static void taskTransition(
            ServerLevel level, BoardLog.Event event, BoardTicket ticket, @Nullable UUID holder, long tick) {
        if (event == BoardLog.Event.ASSIGNED
                || event == BoardLog.Event.BLOCKED
                || event == BoardLog.Event.UNBLOCKED
                || event == BoardLog.Event.NOTE) {
            return;
        }
        LogRecord.Builder record = ModLog.record(LogType.TASK_EVENT, System.currentTimeMillis(), tick);
        if (record == null) {
            return;
        }
        record.ticket(ticket.id())
                .put("board_case", ticket.boardCase().name())
                .put("event", taskEvent(event))
                .put("priority", ticket.priority())
                .put("attempts", SpiritLogSupport.attempts(level, ticket.id()))
                .cause(taskCause(event))
                .put(REASON, SpiritLogSupport.reason(ticket))
                .put("missing", LogJson.writeValue(ticket.requiredItems()))
                .put("source_node", SpiritLogSupport.graphSource(ticket));
        if (holder != null) {
            SpiritLogSupport.spirit(level, holder.toString(), record);
        }
        ModLog.append(record);
    }

    private static String taskEvent(BoardLog.Event event) {
        return event == BoardLog.Event.SUBMITTED ? "POSTED" : event.name();
    }

    private static LogCause taskCause(BoardLog.Event event) {
        return switch (event) {
            case DONE -> LogCause.DONE;
            case FAILED -> LogCause.FAILED;
            case CANCELLED -> LogCause.CANCELLED;
            case REOPENED -> LogCause.REOPENED;
            case DETACHED -> LogCause.DETACHED;
            case SUBMITTED, POSTED, ASSIGNED -> LogCause.CHOSEN;
            case BLOCKED -> LogCause.BLOCKED;
            case UNBLOCKED -> LogCause.UNBLOCKED;
            case NOTE -> LogCause.UNKNOWN;
        };
    }
}

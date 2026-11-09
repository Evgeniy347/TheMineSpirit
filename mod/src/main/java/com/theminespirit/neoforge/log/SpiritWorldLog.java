package com.theminespirit.neoforge.log;

import com.theminespirit.core.log.LogCause;
import com.theminespirit.core.log.LogJson;
import com.theminespirit.core.log.LogRecord;
import com.theminespirit.core.log.LogType;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class SpiritWorldLog {
    private SpiritWorldLog() {}

    public static void death(ServerLevel level, SpiritEntity spirit, String cause, String killer) {
        LogRecord.Builder record = ModLog.record(LogType.DEATH, System.currentTimeMillis(), level.getGameTime());
        if (record == null) {
            return;
        }
        BlockPos at = spirit.blockPosition();
        Map<String, Integer> inventory = new HashMap<>(IncidentSnapshots.inventoryOf(spirit));
        record.put("event", "death")
                .spirit(spirit.displayName(), spirit.getUUID().toString())
                .at(at.getX(), at.getY(), at.getZ())
                .cause(LogCause.DEATH)
                .put("reason", cause)
                .put("killer", killer)
                .put("inventory", LogJson.writeValue(inventory))
                .put("ticket", SpiritLogSupport.ticketOf(spirit))
                .put("script", spirit.currentTaskScript().orElse(""))
                .put("recovered", false);
        ModLog.append(record);
    }

    public static void deathRespawn(ServerLevel level, SpiritEntity spirit, BlockCoord death, BlockCoord born) {
        LogRecord.Builder record = ModLog.record(LogType.DEATH, System.currentTimeMillis(), level.getGameTime());
        if (record == null) {
            return;
        }
        record.put("event", "respawn")
                .spirit(spirit.displayName(), spirit.getUUID().toString())
                .at(death.x(), death.y(), death.z())
                .cause(LogCause.RESPAWN)
                .put("killer", "")
                .put("respawn_x", born.x())
                .put("respawn_y", born.y())
                .put("respawn_z", born.z())
                .put("respawn_ts", System.currentTimeMillis())
                .put("recovered", false)
                .put("script", spirit.currentTaskScript().orElse(""));
        ModLog.append(record);
    }

    public static void graphDecision(
            String node,
            String spirit,
            String spiritId,
            int priority,
            boolean feasible,
            String next,
            String why,
            long tick) {
        LogRecord.Builder record = ModLog.record(LogType.GRAPH_DECISION, System.currentTimeMillis(), tick);
        if (record == null) {
            return;
        }
        record.put("node", node)
                .spirit(spirit, spiritId)
                .put("priority", priority)
                .put("feasible", feasible)
                .put("chosen", true)
                .put("next_node", next)
                .put("why", why)
                .cause(feasible ? LogCause.CHOSEN : LogCause.INFEASIBLE);
        ModLog.append(record);
    }

    public static void route(
            SpiritEntity spirit,
            String purpose,
            BlockCoord from,
            BlockCoord to,
            String status,
            int nodes,
            long millis,
            @Nullable BlockCoord end,
            String reason) {
        LogRecord.Builder record = ModLog.record(
                LogType.ROUTE, System.currentTimeMillis(), spirit.level().getGameTime());
        if (record == null) {
            return;
        }
        record.spirit(spirit.displayName(), spirit.getUUID().toString())
                .put("purpose", purpose)
                .put("from_x", from.x())
                .put("from_y", from.y())
                .put("from_z", from.z())
                .put("to_x", to.x())
                .put("to_y", to.y())
                .put("to_z", to.z())
                .put("status", status)
                .put("nodes", nodes)
                .put("ms", millis)
                .put("reason", reason)
                .cause(routeCause(status));
        if (end != null) {
            record.put("end_x", end.x()).put("end_y", end.y()).put("end_z", end.z());
        }
        ModLog.append(record);
    }

    public static void stuck(
            ServerLevel level,
            UUID spiritId,
            String event,
            @Nullable BlockCoord at,
            String method,
            @Nullable String helper,
            String reason,
            LogCause cause,
            long sinceTick) {
        LogRecord.Builder record = ModLog.record(LogType.STUCK, System.currentTimeMillis(), level.getGameTime());
        if (record == null) {
            return;
        }
        Entity entity = level.getEntity(spiritId);
        String name = entity instanceof SpiritEntity spirit ? spirit.displayName() : spiritId.toString();
        record.spirit(name, spiritId.toString())
                .put("event", event)
                .put("method", method)
                .put("helper", helper)
                .put("reason", reason)
                .cause(cause)
                .put("since_tick", sinceTick);
        if (at != null) {
            record.at(at.x(), at.y(), at.z());
        }
        ModLog.append(record);
    }

    public static void itemMove(
            SpiritEntity spirit, String action, String item, int count, @Nullable BlockPos container) {
        LogRecord.Builder record = ModLog.record(
                LogType.ITEM_MOVE, System.currentTimeMillis(), spirit.level().getGameTime());
        if (record == null) {
            return;
        }
        record.spirit(spirit.displayName(), spirit.getUUID().toString())
                .put("action", action)
                .put("item", item)
                .put("count", count)
                .put("ticket", SpiritLogSupport.ticketOf(spirit));
        if (container != null) {
            record.put("container_x", container.getX())
                    .put("container_y", container.getY())
                    .put("container_z", container.getZ());
        }
        ModLog.append(record);
    }

    public static void sample(SpiritEntity spirit) {
        LogRecord.Builder record = ModLog.record(
                LogType.SPIRIT_SAMPLE,
                System.currentTimeMillis(),
                spirit.level().getGameTime());
        if (record == null) {
            return;
        }
        BlockPos at = spirit.blockPosition();
        record.spirit(spirit.displayName(), spirit.getUUID().toString())
                .at(at.getX(), at.getY(), at.getZ())
                .put("state", spirit.taskStatus().map(Enum::name).orElse("idle"))
                .put("script", spirit.currentTaskScript().orElse(""))
                .put("ticket", SpiritLogSupport.ticketOf(spirit))
                .put("food", spirit.hunger().foodLevel())
                .put("health", spirit.getHealth());
        ModLog.append(record);
    }

    public static void health(
            MinecraftServer server,
            double mspt,
            double worstMspt,
            int sourceFailures,
            int pending,
            int active,
            int blocked,
            long boardMs,
            long pruneMs,
            int tickets,
            String event) {
        LogRecord.Builder record = ModLog.record(
                LogType.SERVER_HEALTH,
                System.currentTimeMillis(),
                server.overworld().getGameTime());
        if (record == null) {
            return;
        }
        record.put("mspt", mspt)
                .put("worst_mspt", worstMspt)
                .put("players", server.getPlayerCount())
                .put("source_failures", sourceFailures)
                .put("pending", pending)
                .put("active", active)
                .put("blocked", blocked)
                .put("board_ms", boardMs)
                .put("prune_ms", pruneMs)
                .put("tickets", tickets)
                .put("event", event);
        ModLog.append(record);
    }

    private static LogCause routeCause(String status) {
        if ("FOUND".equals(status)) {
            return LogCause.CHOSEN;
        }
        if ("PARTIAL".equals(status)) {
            return LogCause.NO_PATH;
        }
        return LogCause.TIMEOUT;
    }
}

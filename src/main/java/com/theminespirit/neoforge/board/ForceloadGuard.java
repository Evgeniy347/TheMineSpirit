package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ForceloadWatch;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.WorkZoneRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class ForceloadGuard {
    private static final int STALL_TICKS = 200;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForceloadWatch WATCH = new ForceloadWatch();
    private static final Map<UUID, Boolean> LAST_OUTSIDE = new ConcurrentHashMap<>();
    private static final Map<UUID, Stall> STALLS = new ConcurrentHashMap<>();

    private ForceloadGuard() {}

    static void tick(ServerLevel level, List<SpiritEntity> spirits, Board board, Map<String, TicketTarget> targets) {
        var forced = level.getForcedChunks();
        LongPredicate isForced = key -> forced.contains(key) || WorkZoneRegistry.holds(level, key);
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            String taskId = null;
            BlockCoord goal = null;
            for (BoardTicket ticket : board.assignedTo(spirit.getUUID())) {
                TicketTarget target = targets.get(ticket.id());
                if (target != null) {
                    taskId = ticket.id();
                    goal = target.target();
                    break;
                }
            }
            BlockCoord position = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
            boolean outside = !isForced.test(chunkKey(position));
            Boolean before = LAST_OUTSIDE.put(spirit.getUUID(), outside);
            if (before != null && before != outside) {
                LOGGER.info(
                        "[board] дух {} {} forceload в {}, задание {}",
                        spirit.displayName(),
                        outside ? "вышел за" : "вернулся в",
                        position,
                        taskId == null ? "-" : taskId);
            }
            WATCH.check(spirit.getUUID(), spirit.displayName(), position, taskId, goal, isForced)
                    .ifPresent(text -> LOGGER.warn("[board] {}", text));
            ForceloadRescue.tick(level, spirit, position, isForced);
            stalled(level, spirit, feet, position);
        }
    }

    private static void stalled(ServerLevel level, SpiritEntity spirit, BlockPos feet, BlockCoord position) {
        long now = level.getGameTime();
        Stall previous = STALLS.get(spirit.getUUID());
        if (previous == null || previous.tickCount() != spirit.tickCount) {
            STALLS.put(spirit.getUUID(), new Stall(spirit.tickCount, now, false));
            return;
        }
        if (!previous.reported() && now - previous.since() >= STALL_TICKS) {
            LOGGER.warn(
                    "[board] дух {} не тикает {} тиков в {}, чанк [{}, {}] загружен: {}",
                    spirit.displayName(),
                    now - previous.since(),
                    position,
                    position.x() >> 4,
                    position.z() >> 4,
                    level.isLoaded(feet) ? "да" : "нет");
            STALLS.put(spirit.getUUID(), new Stall(previous.tickCount(), previous.since(), true));
        }
    }

    private static long chunkKey(BlockCoord point) {
        long chunkX = point.x() >> 4;
        long chunkZ = point.z() >> 4;
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    private record Stall(long tickCount, long since, boolean reported) {}
}

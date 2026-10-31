package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ForceloadRange;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.DropProfile;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class ForceloadRescue {
    static final int RETRY_TICKS = 100;
    private static final int VERTICAL_SEARCH = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private ForceloadRescue() {}

    static void tick(ServerLevel level, SpiritEntity spirit, BlockCoord position, LongPredicate forced) {
        if (ForceloadRange.inside(position, forced)) {
            LAST.remove(spirit.getUUID());
            return;
        }
        long now = level.getGameTime();
        Long last = LAST.get(spirit.getUUID());
        if (last != null && now - last < RETRY_TICKS) {
            return;
        }
        LAST.put(spirit.getUUID(), now);
        BlockCoord home = spirit.home().orElse(position);
        Optional<BlockCoord> target = ForceloadRange.returnPoint(
                        position, forced, home, ForceloadRange.DEFAULT_SEARCH_CHUNKS)
                .flatMap(cell -> stand(level, cell));
        if (target.isEmpty()) {
            LOGGER.warn(
                    "[board] дух {} в {} вне forceload: вернуть некуда — в помеченных чанках нет безопасной клетки",
                    spirit.displayName(),
                    position);
            return;
        }
        BlockCoord cell = target.get();
        spirit.getNavigation().stop();
        spirit.teleportTo(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        spirit.countTeleport();
        LOGGER.warn(
                "[board] дух {} возвращён из {} в {}: вышел за forceload и не тикает",
                spirit.displayName(),
                position,
                cell);
    }

    private static Optional<BlockCoord> stand(ServerLevel level, BlockCoord around) {
        LevelSafetyView safety = new LevelSafetyView(level);
        for (int step = 0; step <= VERTICAL_SEARCH; step++) {
            BlockCoord up = new BlockCoord(around.x(), around.y() + step, around.z());
            if (MiningSafety.canStandOnGround(up, safety, DropProfile.MAX_SAFE_DROP)) {
                return Optional.of(up);
            }
            if (step == 0) {
                continue;
            }
            BlockCoord down = new BlockCoord(around.x(), around.y() - step, around.z());
            if (MiningSafety.canStandOnGround(down, safety, DropProfile.MAX_SAFE_DROP)) {
                return Optional.of(down);
            }
        }
        return Optional.empty();
    }
}

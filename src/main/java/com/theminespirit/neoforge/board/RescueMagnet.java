package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.DropProfile;
import com.theminespirit.core.movement.MagnetSearch;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class RescueMagnet {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RADIUS = 8;
    private static final int VERTICAL = 8;

    private RescueMagnet() {}

    static boolean home(ServerLevel level, SpiritEntity spirit, BlockCoord goal) {
        LevelSafetyView safety = new LevelSafetyView(level);
        MagnetSearch.Result found = MagnetSearch.around(
                goal, cell -> MiningSafety.canStandOnGround(cell, safety, DropProfile.MAX_SAFE_DROP), RADIUS, VERTICAL);
        BlockPos before = spirit.blockPosition();
        if (found.target().isEmpty()) {
            LOGGER.warn(
                    "[rescue] дух {}: перенос домой в {} не удался — проверено {} клеток, первые {}",
                    spirit.displayName(),
                    goal,
                    found.checked(),
                    found.first());
            return false;
        }
        BlockCoord target = found.target().orElseThrow();
        spirit.getNavigation().stop();
        spirit.teleportTo(target.x() + 0.5, target.y(), target.z() + 0.5);
        spirit.countTeleport();
        boolean moved = !spirit.blockPosition().equals(before);
        if (!moved) {
            LOGGER.warn(
                    "[rescue] дух {}: магнит в {} не сдвинул духа (проверено {} клеток, первые {})",
                    spirit.displayName(),
                    target,
                    found.checked(),
                    found.first());
            return false;
        }
        LOGGER.warn(
                "[rescue] дух {}: перенос домой {} → {} (проверено {} клеток)",
                spirit.displayName(),
                before,
                target,
                found.checked());
        return true;
    }
}

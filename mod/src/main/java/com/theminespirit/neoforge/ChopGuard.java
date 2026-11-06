package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class ChopGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChopGuard() {}

    static boolean protectedLog(SpiritEntity spirit, Tree tree, List<BlockCoord> logs, boolean orchard) {
        for (BlockCoord log : logs) {
            BlockPos pos = new BlockPos(log.x(), log.y(), log.z());
            if (!orchard && planned(spirit, pos)) {
                LOGGER.info(
                        "[chop] {}: дерево {} не рубится — бревно {} принадлежит плану постройки этапа",
                        spirit.getName().getString(),
                        tree.base(),
                        log);
                return true;
            }
            BlockState state = spirit.level().getBlockState(pos);
            BlockProtection.Decision decision = orchard
                    ? spirit.protectionGate().checkHarvest(pos, state)
                    : spirit.protectionGate().checkBreak(pos, state);
            if (!state.isAir() && decision instanceof BlockProtection.Refused refused) {
                LOGGER.info(
                        "[chop] {}: дерево {} не рубится целиком — бревно {} защищено: {}",
                        spirit.getName().getString(),
                        tree.base(),
                        log,
                        refused.reason());
                return true;
            }
        }
        return false;
    }

    private static boolean planned(SpiritEntity spirit, BlockPos pos) {
        return spirit.level() instanceof ServerLevel server
                && SettlementPlotStore.get(server)
                        .site()
                        .map(site -> site.planCell(pos.getX(), pos.getY(), pos.getZ()))
                        .orElse(false);
    }
}

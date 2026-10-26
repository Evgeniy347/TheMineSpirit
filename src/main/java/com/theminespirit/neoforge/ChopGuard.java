package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class ChopGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChopGuard() {}

    static boolean protectedLog(SpiritEntity spirit, Tree tree, List<BlockCoord> logs) {
        for (BlockCoord log : logs) {
            BlockPos pos = new BlockPos(log.x(), log.y(), log.z());
            BlockState state = spirit.level().getBlockState(pos);
            if (!state.isAir()
                    && spirit.protectionGate().checkBreak(pos, state) instanceof BlockProtection.Refused refused) {
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
}

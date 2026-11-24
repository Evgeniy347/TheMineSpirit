package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.BlockStates;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class ClearableCells {
    private ClearableCells() {}

    static Predicate<BlockCoord> of(ServerLevel level) {
        return at -> {
            String id = BlockStates.idOf(level.getBlockState(new BlockPos(at.x(), at.y(), at.z())));
            return !(DataTables.INSTANCE.protection().current().checkOwn(id, false) instanceof BlockProtection.Refused);
        };
    }
}

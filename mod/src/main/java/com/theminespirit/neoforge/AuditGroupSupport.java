package com.theminespirit.neoforge;

import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Wanted;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;

final class AuditGroupSupport {
    private AuditGroupSupport() {}

    static String wantedText(DiffItem item) {
        Wanted wanted = item.wanted();
        return SchematicBlocks.isFloor(wanted.id()) ? SchematicBlocks.placementBlock(wanted.text()) : wanted.text();
    }

    static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    static BlockCoord spiritCoord(SpiritEntity spirit) {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}

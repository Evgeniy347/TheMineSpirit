package com.theminespirit.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class PlaceRefusal {
    private PlaceRefusal() {}

    static BlockPlacer.Begin checked(SpiritEntity spirit, BlockPos target, String blockId, BlockPlacer.Begin result) {
        if (result instanceof BlockPlacer.Refused refused) {
            SpiritBlocks.refuse((ServerLevel) spirit.level(), spirit, target, "place", blockId, refused.message());
        } else if (result instanceof BlockPlacer.Protected protectedBlock) {
            SpiritBlocks.refuse(
                    (ServerLevel) spirit.level(), spirit, target, "place", blockId, protectedBlock.reason());
        }
        return result;
    }
}

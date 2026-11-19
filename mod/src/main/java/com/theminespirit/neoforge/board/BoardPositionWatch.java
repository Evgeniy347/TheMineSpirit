package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.world.entity.Entity;

final class BoardPositionWatch {
    private BoardPositionWatch() {}

    static void observe(Board board, List<? extends Entity> spirits, long now) {
        for (Entity spirit : spirits) {
            BlockCoord at = new BlockCoord(
                    spirit.blockPosition().getX(),
                    spirit.blockPosition().getY(),
                    spirit.blockPosition().getZ());
            board.stuck().see(spirit.getUUID(), at, now);
        }
    }
}

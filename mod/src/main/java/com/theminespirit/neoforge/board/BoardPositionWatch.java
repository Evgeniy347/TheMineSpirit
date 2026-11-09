package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;

final class BoardPositionWatch {
    private BoardPositionWatch() {}

    static void observe(Board board, List<SpiritEntity> spirits, long now) {
        for (SpiritEntity spirit : spirits) {
            BlockCoord at = new BlockCoord(
                    spirit.blockPosition().getX(),
                    spirit.blockPosition().getY(),
                    spirit.blockPosition().getZ());
            board.stuck().see(spirit.getUUID(), at, now);
        }
    }
}

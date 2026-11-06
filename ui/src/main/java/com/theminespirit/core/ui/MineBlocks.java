package com.theminespirit.core.ui;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

@FunctionalInterface
public interface MineBlocks {
    Observed at(BlockCoord cell);

    record Observed(String blockId, boolean empty) {
        public Observed {
            Objects.requireNonNull(blockId, "Id блока среза шахты не может быть null");
        }
    }
}

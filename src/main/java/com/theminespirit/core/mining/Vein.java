package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;

public record Vein(List<BlockCoord> blocks, boolean truncated) {
    public Vein {
        blocks = List.copyOf(blocks);
    }
}

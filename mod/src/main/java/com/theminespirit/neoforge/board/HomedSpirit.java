package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;

public interface HomedSpirit {
    Optional<BlockCoord> home();

    boolean isRemoved();
}

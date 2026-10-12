package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;

interface LocateSource {
    List<BlockCoord> advance();

    boolean done();

    int examinedPositions();
}

package com.theminespirit.gametest;

import java.util.List;
import java.util.Set;

record ChunkHold(List<Long> held) {
    ChunkHold {
        held = List.copyOf(held);
    }

    List<Long> releasable(Set<Long> neededByLiveTests) {
        return held.stream().filter(chunk -> !neededByLiveTests.contains(chunk)).toList();
    }
}

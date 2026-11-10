package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class HomeCache {
    record Key(BlockCoord center, @Nullable UUID spirit) {}

    private long tick = Long.MIN_VALUE;
    private final Map<Key, Optional<BlockCoord>> homes = new HashMap<>();

    Optional<BlockCoord> resolve(long now, Key key, Supplier<Optional<BlockCoord>> compute) {
        if (now != tick) {
            tick = now;
            homes.clear();
        }
        return homes.computeIfAbsent(key, ignored -> compute.get());
    }
}

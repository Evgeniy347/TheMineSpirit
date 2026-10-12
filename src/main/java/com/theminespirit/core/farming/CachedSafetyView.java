package com.theminespirit.core.farming;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CachedSafetyView implements SafetyView {
    private final SafetyView delegate;
    private final Map<BlockCoord, Cell> cache = new HashMap<>();

    public CachedSafetyView(SafetyView delegate) {
        this.delegate = Objects.requireNonNull(delegate,
                "SafetyView не может быть null: без него кэш не знает, что читать");
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        Objects.requireNonNull(coord, "Координата не может быть null: без неё неизвестно, что читать из мира");
        return cache.computeIfAbsent(coord, delegate::cellAt);
    }
}

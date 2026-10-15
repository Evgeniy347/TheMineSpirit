package com.theminespirit.core.doors;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.UUID;

public record DoorClosing(BlockCoord door, UUID spirit) {
    public DoorClosing {
        Objects.requireNonNull(door, "Назначение без двери бессмысленно: дух не знает, что закрывать");
        Objects.requireNonNull(spirit, "Назначение без духа бессмысленно: дверь не будет закрыта");
    }
}

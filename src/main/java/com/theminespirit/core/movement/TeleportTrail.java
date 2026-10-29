package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class TeleportTrail {
    private @Nullable BlockCoord target;
    private final Set<BlockCoord> used = new HashSet<>();
    private int attempts;
    private @Nullable BlockCoord last;
    private long lastTick;

    public void aimAt(BlockCoord target) {
        Objects.requireNonNull(
                target, "Цель навигации не может быть null: без неё не с чем сравнивать прошлые переносы");
        if (target.equals(this.target)) {
            return;
        }
        this.target = target;
        used.clear();
        attempts = 0;
        last = null;
        lastTick = 0;
    }

    public boolean used(BlockCoord cell) {
        return used.contains(cell);
    }

    public int attempts() {
        return attempts;
    }

    public Optional<BlockCoord> last() {
        return Optional.ofNullable(last);
    }

    public long lastTick() {
        return lastTick;
    }

    public void record(BlockCoord cell, long tick) {
        Objects.requireNonNull(cell, "Клетка переноса не может быть null: иначе её не запомнить");
        used.add(cell);
        attempts++;
        last = cell;
        lastTick = tick;
    }
}

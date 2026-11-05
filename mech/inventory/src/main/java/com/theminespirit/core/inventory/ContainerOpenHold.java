package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ContainerOpenHold {
    private final int holdTicks;
    private final Map<BlockCoord, Tracked> tracked = new HashMap<>();

    public ContainerOpenHold(int holdTicks) {
        if (holdTicks <= 0) {
            throw new IllegalArgumentException(
                    "holdTicks = " + holdTicks + ": ожидание закрытия обязано быть положительным");
        }
        this.holdTicks = holdTicks;
    }

    public boolean touch(BlockCoord pos, UUID spirit, long now) {
        Objects.requireNonNull(pos, "Позиция контейнера не может быть null: без неё нечем держать крышку");
        Objects.requireNonNull(spirit, "Дух не может быть null: иначе не учесть нескольких духов у одного контейнера");
        requireTick(now);
        Tracked state = tracked.get(pos);
        if (state == null) {
            state = new Tracked();
            tracked.put(pos, state);
            state.touch(spirit, now);
            return true;
        }
        state.touch(spirit, now);
        return false;
    }

    public List<BlockCoord> tick(long now) {
        requireTick(now);
        List<BlockCoord> closing = new ArrayList<>();
        Iterator<Map.Entry<BlockCoord, Tracked>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockCoord, Tracked> entry = iterator.next();
            if (now - entry.getValue().lastTouch() < holdTicks) {
                continue;
            }
            closing.add(entry.getKey());
            iterator.remove();
        }
        closing.sort(BlockCoord::compareTo);
        return List.copyOf(closing);
    }

    public int openers(BlockCoord pos) {
        Tracked state = tracked.get(pos);
        return state == null ? 0 : state.touched.size();
    }

    public boolean isEmpty() {
        return tracked.isEmpty();
    }

    private static void requireTick(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("now = " + now + ": время не может быть отрицательным");
        }
    }

    private static final class Tracked {
        private final Map<UUID, Long> touched = new HashMap<>();
        private long lastTouch;

        void touch(UUID spirit, long now) {
            touched.put(spirit, now);
            lastTouch = Math.max(lastTouch, now);
        }

        long lastTouch() {
            return lastTouch;
        }
    }
}

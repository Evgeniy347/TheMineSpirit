package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class FenceBreaks {
    public static final int MAX_BREAKS = 128;

    private final int capacity;
    private final Map<BlockCoord, String> breaks = new LinkedHashMap<>();

    public FenceBreaks(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "Ёмкость памяти забора " + capacity + " меньше 1: без памяти сломанный забор не починить");
        }
        this.capacity = capacity;
    }

    public static String cause(BlockCoord at) {
        Objects.requireNonNull(at, "Клетка сломанного забора не может быть null: по ней строится причина");
        return "fence:" + at.x() + ":" + at.y() + ":" + at.z();
    }

    public void remember(BlockCoord at, String block) {
        Objects.requireNonNull(at, "Клетка сломанного забора не может быть null: без неё нечего чинить");
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("Блок забора пуст: без него ремонт не знает, что ставить");
        }
        breaks.remove(at);
        breaks.put(at, block);
        while (breaks.size() > capacity) {
            BlockCoord oldest = breaks.keySet().iterator().next();
            breaks.remove(oldest);
        }
    }

    public Map<BlockCoord, String> retainLive(Predicate<BlockCoord> stillBroken) {
        Objects.requireNonNull(stillBroken, "Проверка клетки не может быть null: без неё не понять, цел ли забор");
        var iterator = breaks.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!stillBroken.test(iterator.next().getKey())) {
                iterator.remove();
            }
        }
        return current();
    }

    public Map<BlockCoord, String> current() {
        return Map.copyOf(breaks);
    }

    public int size() {
        return breaks.size();
    }
}

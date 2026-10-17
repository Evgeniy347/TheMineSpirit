package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UnprotectAreas {
    public static final int MAX_AREAS = 64;

    private final List<UnprotectArea> areas;

    public UnprotectAreas() {
        this(List.of());
    }

    public UnprotectAreas(List<UnprotectArea> areas) {
        Objects.requireNonNull(areas, "Список разрешённых областей не может быть null: пустой список задают List.of()");
        if (areas.size() > MAX_AREAS) {
            throw new IllegalArgumentException("Разрешённых областей " + areas.size() + ", допустимо не больше " + MAX_AREAS
                    + ": список проверяется на каждую ломку, снимите старые разрешения");
        }
        this.areas = new ArrayList<>(areas);
    }

    public void add(UnprotectArea area, int maxVolume, long nowMillis) {
        Objects.requireNonNull(area, "Разрешённая область не может быть null: добавлять нечего");
        if (maxVolume < 1) {
            throw new IllegalArgumentException("Предел объёма " + maxVolume + " меньше 1: область не может быть пустой");
        }
        if (!area.isActive(nowMillis)) {
            throw new IllegalArgumentException("Срок разрешения '" + area.id() + "' уже истёк: новая область не может быть мёртвой");
        }
        for (UnprotectArea existing : areas) {
            if (existing.id().equals(area.id())) {
                throw new IllegalArgumentException("Разрешение '" + area.id() + "' уже существует: id должен быть уникальным");
            }
        }
        if (areas.size() >= MAX_AREAS) {
            throw new IllegalArgumentException("Разрешённых областей уже " + areas.size() + ", допустимо не больше " + MAX_AREAS
                    + ": снимите старые разрешения");
        }
        long volume = area.volume();
        if (volume > maxVolume) {
            throw new IllegalArgumentException("Объём области " + volume + " больше предела " + maxVolume
                    + " (protection.json → maxUnprotectVolume): уменьшите область или поднимите предел данными");
        }
        areas.add(area);
    }

    public boolean remove(String id) {
        Objects.requireNonNull(id, "Id разрешения не может быть null: снимать нечего");
        return areas.removeIf(area -> area.id().equals(id));
    }

    public boolean covers(String dimension, BlockCoord pos, long nowMillis) {
        Objects.requireNonNull(dimension, "Мир не может быть null: области привязаны к миру");
        Objects.requireNonNull(pos, "Координата не может быть null: без неё неизвестно, что проверять");
        for (UnprotectArea area : areas) {
            if (area.isActive(nowMillis) && area.contains(dimension, pos)) {
                return true;
            }
        }
        return false;
    }

    public List<UnprotectArea> all() {
        return List.copyOf(areas);
    }

    public int size() {
        return areas.size();
    }
}

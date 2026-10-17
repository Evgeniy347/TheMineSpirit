package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Отряд духов, строящих одну схему в одном месте. Каждый вошедший получает место в порядке входа; место
 * определяет его долю клеток в {@link CellPartition}. Уход духа сдвигает доли остальных, поэтому версия
 * растёт на каждом входе и уходе — по ней стройка понимает, что доли нужно пересчитать.
 */
public final class BuildSquad {
    private final List<UUID> members = new ArrayList<>();
    private long version;

    public void join(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: отряд стройки держит дух, а не пустое место");
        if (!members.contains(spirit)) {
            members.add(spirit);
            version++;
        }
    }

    public boolean leave(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кого убрать из отряда");
        if (members.remove(spirit)) {
            version++;
            return true;
        }
        return false;
    }

    public int slotOf(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: место ищется конкретному духу");
        return members.indexOf(spirit);
    }

    public int size() {
        return members.size();
    }

    public long version() {
        return version;
    }

    public List<UUID> members() {
        return List.copyOf(members);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}

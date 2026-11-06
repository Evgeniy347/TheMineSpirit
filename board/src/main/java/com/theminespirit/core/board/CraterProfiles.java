package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class CraterProfiles {
    public static final int MAX_CELLS = 16;

    private final int capacity;
    private final int maxCells;
    private final Map<String, List<BlockCoord>> profiles = new LinkedHashMap<>();

    public CraterProfiles(int capacity) {
        this(capacity, MAX_CELLS);
    }

    public CraterProfiles(int capacity, int maxCells) {
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "Ёмкость кольца воронок " + capacity + " меньше 1: кольцо без памяти не проверяет актуальность");
        }
        if (maxCells < 1) {
            throw new IllegalArgumentException(
                    "Предел клеток профиля " + maxCells + " меньше 1: пустой профиль считался бы засыпанным");
        }
        this.capacity = capacity;
        this.maxCells = maxCells;
    }

    public static String cause(BlockCoord center) {
        Objects.requireNonNull(center, "Центр воронки не может быть null: по нему строится причина");
        return "crater:" + center.x() + ":" + center.y() + ":" + center.z();
    }

    public static Optional<BlockCoord> center(String cause) {
        Objects.requireNonNull(cause, "Причина воронки не может быть null: из неё берётся центр");
        String[] parts = cause.split(":", -1);
        if (parts.length != 4 || !"crater".equals(parts[0])) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    new BlockCoord(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public void remember(String cause, List<BlockCoord> cells) {
        if (cause == null || cause.isBlank()) {
            throw new IllegalArgumentException("Причина воронки пуста: без неё задание не с чем сверить");
        }
        Objects.requireNonNull(cells, "Клетки воронки не могут быть null: пустой профиль задают пустым списком");
        List<BlockCoord> unique = new ArrayList<>(new LinkedHashSet<>(cells));
        unique.sort(Comparator.comparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::z));
        List<BlockCoord> profile = List.copyOf(unique.subList(0, Math.min(maxCells, unique.size())));
        profiles.remove(cause);
        profiles.put(cause, profile);
        while (profiles.size() > capacity) {
            String oldest = profiles.keySet().iterator().next();
            profiles.remove(oldest);
        }
    }

    public Set<String> retainLive(Predicate<BlockCoord> empty) {
        Objects.requireNonNull(empty, "Проверка клетки не может быть null: без неё не понять, засыпана ли воронка");
        Set<String> live = new LinkedHashSet<>();
        var iterator = profiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<BlockCoord>> entry = iterator.next();
            if (entry.getValue().stream().anyMatch(empty)) {
                live.add(entry.getKey());
            } else {
                iterator.remove();
            }
        }
        return Set.copyOf(live);
    }

    public Set<String> causes() {
        return Set.copyOf(profiles.keySet());
    }

    public List<BlockCoord> profile(String cause) {
        List<BlockCoord> cells = profiles.get(cause);
        return cells == null ? List.of() : cells;
    }

    public int size() {
        return profiles.size();
    }
}

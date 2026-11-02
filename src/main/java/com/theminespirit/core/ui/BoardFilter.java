package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BoardFilter(
        @Nullable BoardTileGroup group,
        Spirit spirit,
        @Nullable String spiritName) {
    public static final BoardFilter ALL = new BoardFilter(null, Spirit.ANY, null);

    public enum Spirit {
        ANY,
        NOBODY,
        NAMED
    }

    public BoardFilter {
        Objects.requireNonNull(spirit, "Режим исполнителя не может быть null: «все» задают Spirit.ANY");
        if (spirit == Spirit.NAMED && (spiritName == null || spiritName.isBlank())) {
            throw new IllegalArgumentException("Фильтр по духу без имени: укажите имя духа или режим ANY/NOBODY");
        }
        if (spirit != Spirit.NAMED) {
            spiritName = null;
        }
    }

    public boolean accepts(BoardTile tile) {
        Objects.requireNonNull(tile, "tile не может быть null: фильтруют плитки доски");
        return accepts(tile.group(), tile.spirit());
    }

    public boolean accepts(BoardTileGroup tileGroup, @Nullable String tileSpirit) {
        Objects.requireNonNull(tileGroup, "Группа плитки не может быть null: фильтр по статусу сравнивает её");
        if (group != null && tileGroup != group) {
            return false;
        }
        return switch (spirit) {
            case ANY -> true;
            case NOBODY -> tileSpirit == null;
            case NAMED -> Objects.equals(tileSpirit, spiritName);
        };
    }

    public List<BoardTile> apply(List<BoardTile> tiles) {
        Objects.requireNonNull(tiles, "tiles не может быть null: пустая доска — пустой список");
        List<BoardTile> kept = new ArrayList<>();
        for (BoardTile tile : tiles) {
            if (accepts(tile)) {
                kept.add(tile);
            }
        }
        return List.copyOf(kept);
    }

    public BoardFilter nextGroup() {
        List<BoardTileGroup> groups = List.of(BoardTileGroup.values());
        int index = group == null ? 0 : groups.indexOf(group) + 1;
        BoardTileGroup next = index < groups.size() ? groups.get(index) : null;
        return new BoardFilter(next, spirit, spiritName);
    }

    public BoardFilter nextSpiritNames(List<String> names) {
        Objects.requireNonNull(names, "names не может быть null: цикл фильтра идёт по именам духов доски");
        return nextSpiritFromNames(names);
    }

    private BoardFilter nextSpiritFromNames(List<String> names) {
        int index = switch (spirit) {
            case ANY -> -1;
            case NOBODY -> 0;
            case NAMED -> names.indexOf(spiritName) < 0 ? names.size() : names.indexOf(spiritName) + 1;
        };
        if (index < 0) {
            return new BoardFilter(group, Spirit.NOBODY, null);
        }
        if (index >= names.size()) {
            return new BoardFilter(group, Spirit.ANY, null);
        }
        return new BoardFilter(group, Spirit.NAMED, names.get(index));
    }
}

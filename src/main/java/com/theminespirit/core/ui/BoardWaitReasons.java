package com.theminespirit.core.ui;

import com.theminespirit.core.board.BoardStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class BoardWaitReasons {
    private static final List<String> TOOL_SUFFIXES = List.of("_pickaxe", "_axe", "_shovel", "_hoe", "_sword");
    private static final Set<String> TOOL_ITEMS = Set.of(
            "shears",
            "flint_and_steel",
            "bucket",
            "water_bucket",
            "lava_bucket",
            "milk_bucket",
            "fishing_rod",
            "carrot_on_a_stick",
            "warped_fungus_on_a_stick",
            "shield",
            "bow",
            "crossbow",
            "trident");

    private BoardWaitReasons() {}

    public enum Kind {
        FREE_SPIRIT,
        SPIRIT,
        RESOURCE,
        TOOL,
        DEPENDENCY,
        BLOCKED
    }

    public record Wait(Kind kind, String subject, int count) {
        public Wait {
            Objects.requireNonNull(kind, "Вид причины не может быть null: по нему экран выбирает текст");
            Objects.requireNonNull(subject, "Предмет причины не может быть null: пустой задают пустой строкой");
            if (count < 0) {
                throw new IllegalArgumentException(
                        "Количество " + count + " отрицательно: нехватка не бывает меньше нуля");
            }
        }
    }

    public static List<Wait> of(BoardTile tile, List<BoardTile> all) {
        Objects.requireNonNull(all, "all не может быть null: зависимости ищутся по всему снимку доски");
        Map<String, BoardTile> byId = new LinkedHashMap<>();
        for (BoardTile other : all) {
            Objects.requireNonNull(other, "плитка снимка не может быть null: по id ищутся зависимости");
            byId.put(other.id(), other);
        }
        return of(tile, byId);
    }

    public static List<Wait> of(BoardTile tile, Map<String, BoardTile> byId) {
        Objects.requireNonNull(tile, "tile не может быть null: причину считают по заданию");
        Objects.requireNonNull(byId, "byId не может быть null: без снимка не найти зависимость");
        String status = tile.status();
        if (!BoardStatus.PENDING.name().equals(status)
                && !BoardStatus.WAITING_RESOURCES.name().equals(status)
                && !BoardStatus.BLOCKED.name().equals(status)) {
            return List.of();
        }
        List<Wait> reasons = new ArrayList<>();
        if (BoardStatus.BLOCKED.name().equals(status)) {
            reasons.add(new Wait(Kind.BLOCKED, "", 0));
        }
        for (String dependency : tile.dependsOn()) {
            BoardTile dependencyTile = byId.get(dependency);
            if (dependencyTile == null || !dependencyTile.completed()) {
                reasons.add(new Wait(Kind.DEPENDENCY, dependency, 0));
            }
        }
        for (Map.Entry<String, Integer> entry : new TreeMap<>(tile.missing()).entrySet()) {
            Kind kind = isTool(entry.getKey()) ? Kind.TOOL : Kind.RESOURCE;
            reasons.add(new Wait(kind, entry.getKey(), entry.getValue()));
        }
        if (reasons.isEmpty()) {
            if (tile.spirit() == null) {
                reasons.add(new Wait(Kind.FREE_SPIRIT, "", 0));
            } else {
                reasons.add(new Wait(Kind.SPIRIT, tile.spirit(), 0));
            }
        }
        return List.copyOf(reasons);
    }

    public static boolean isTool(String item) {
        Objects.requireNonNull(item, "Предмет не может быть null: без него не отличить инструмент от ресурса");
        String name = item;
        int namespace = name.indexOf(':');
        if (namespace >= 0) {
            name = name.substring(namespace + 1);
        }
        if (TOOL_ITEMS.contains(name)) {
            return true;
        }
        for (String suffix : TOOL_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}

package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class BoardMaterials {
    private BoardMaterials() {}

    public enum Kind {
        GATHER,
        SUPPLY,
        MISSING
    }

    public record Material(Kind kind, String item, int count) {
        public Material {
            Objects.requireNonNull(kind, "Вид материала не может быть null: по нему экран выбирает подпись");
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException(
                        "Предмет материала пуст: без него не найти иконку и не назвать, что добывать");
            }
            if (count <= 0) {
                throw new IllegalArgumentException(
                        "Количество материала " + count + " неположительно: показывать нечего");
            }
        }
    }

    public static List<Material> of(BoardTile tile) {
        Objects.requireNonNull(tile, "Плитка не может быть null: материалы берут из задания");
        Kind supplyKind = supplyKind(tile.boardCase());
        Map<String, Material> materials = new LinkedHashMap<>();
        if (supplyKind != null && tile.supplyItem() != null && tile.supplyCount() > 0) {
            materials.put(tile.supplyItem(), new Material(supplyKind, tile.supplyItem(), tile.supplyCount()));
        }
        for (Map.Entry<String, Integer> entry : new TreeMap<>(tile.missing()).entrySet()) {
            materials.putIfAbsent(entry.getKey(), new Material(Kind.MISSING, entry.getKey(), entry.getValue()));
        }
        return List.copyOf(new ArrayList<>(materials.values()));
    }

    public static @Nullable Kind supplyKind(String boardCase) {
        Objects.requireNonNull(boardCase, "Кейс не может быть null: по нему решают, что означает запас задания");
        return switch (boardCase) {
            case "GATHER_MATERIALS" -> Kind.GATHER;
            case "SUPPLY_SITE" -> Kind.SUPPLY;
            default -> null;
        };
    }
}

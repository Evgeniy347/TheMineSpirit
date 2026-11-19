package com.theminespirit.core.ui;

import java.util.ArrayList;
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
        NEED,
        MISSING
    }

    public record Material(
            Kind kind, String item, int count, @Nullable Integer have) {
        public Material(Kind kind, String item, int count) {
            this(kind, item, count, null);
        }

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
        List<Material> materials = new ArrayList<>();
        String supplyItem = null;
        if (supplyKind != null && tile.supplyItem() != null && tile.supplyCount() > 0) {
            supplyItem = tile.supplyItem();
            materials.add(new Material(supplyKind, supplyItem, tile.supplyCount()));
        }
        if ("REPAIR_BUILDING".equals(tile.boardCase())) {
            for (Map.Entry<String, Integer> entry : new TreeMap<>(tile.requiredItems()).entrySet()) {
                materials.add(new Material(
                        Kind.NEED,
                        entry.getKey(),
                        entry.getValue(),
                        tile.stock().get(entry.getKey())));
            }
        }
        boolean needRows = "REPAIR_BUILDING".equals(tile.boardCase());
        for (Map.Entry<String, Integer> entry : new TreeMap<>(tile.missing()).entrySet()) {
            if (entry.getKey().equals(supplyItem) || (needRows && tile.stock().containsKey(entry.getKey()))) {
                continue;
            }
            materials.add(new Material(Kind.MISSING, entry.getKey(), entry.getValue()));
        }
        return List.copyOf(materials);
    }

    public static boolean shortage(Material material) {
        Objects.requireNonNull(material, "Материал не может быть null: по нему решают, ставить ли звезду");
        return material.kind() == Kind.NEED && (material.have() == null || material.count() > material.have());
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

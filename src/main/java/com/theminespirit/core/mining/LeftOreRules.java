package com.theminespirit.core.mining;

import java.util.Objects;
import java.util.Optional;

public final class LeftOreRules {
    public static final String LUCK_PICKAXE = "pickaxe_luck";
    public static final String WOOD_TIER = "pickaxe_wood_tier";
    public static final String STONE_TIER = "pickaxe_stone_tier";
    public static final String IRON_TIER = "pickaxe_iron_tier";

    private LeftOreRules() {}

    public static boolean isToolTier(String item) {
        Objects.requireNonNull(item, "Ключ инструмента не может быть null: маркер кирки проверяется по имени");
        return LUCK_PICKAXE.equals(item) || WOOD_TIER.equals(item) || STONE_TIER.equals(item) || IRON_TIER.equals(item);
    }

    public static String requiredTool(String blockId) {
        Objects.requireNonNull(blockId, "Id руды не может быть null: по нему выбирается кирка");
        if (MineOres.kindOf(blockId) == MineOres.Kind.LUCK) {
            return LUCK_PICKAXE;
        }
        Optional<OreKind> kind = OreKind.ofBlock(blockId);
        if (kind.isEmpty()) {
            throw new IllegalArgumentException(
                    "Блок '" + blockId + "' не руда: оставленной в шахте бывает только руда");
        }
        return switch (kind.get()) {
            case COAL, QUARTZ -> WOOD_TIER;
            case COPPER, IRON, LAPIS -> STONE_TIER;
            default -> IRON_TIER;
        };
    }
}

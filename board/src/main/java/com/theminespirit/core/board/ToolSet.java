package com.theminespirit.core.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ToolSet {
    private static final String NAMESPACE = "minecraft:";
    private static final Map<BoardCase, String> ITEMS = items();

    private ToolSet() {}

    public static boolean isTool(@Nullable BoardCase boardCase) {
        return boardCase != null && ITEMS.containsKey(boardCase);
    }

    public static String itemId(BoardCase boardCase) {
        String item = ITEMS.get(boardCase);
        if (item == null) {
            throw new IllegalArgumentException(
                    "Кейс " + boardCase + " не из набора инструментов: " + "у него нет предмета крафта");
        }
        return item;
    }

    public static Optional<BoardCase> caseFor(String item) {
        Objects.requireNonNull(item, "Предмет инструмента не может быть null: по нему ищут кейс крафта");
        String normalized = item.startsWith(NAMESPACE) ? item : NAMESPACE + item;
        return ITEMS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(normalized))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static Map<BoardCase, String> items() {
        Map<BoardCase, String> items = new LinkedHashMap<>();
        items.put(BoardCase.CRAFT_WOODEN_PICKAXE, "minecraft:wooden_pickaxe");
        items.put(BoardCase.CRAFT_WOODEN_AXE, "minecraft:wooden_axe");
        items.put(BoardCase.CRAFT_WOODEN_SHOVEL, "minecraft:wooden_shovel");
        items.put(BoardCase.CRAFT_STONE_PICKAXE, "minecraft:stone_pickaxe");
        items.put(BoardCase.CRAFT_STONE_AXE, "minecraft:stone_axe");
        items.put(BoardCase.CRAFT_STONE_SHOVEL, "minecraft:stone_shovel");
        items.put(BoardCase.CRAFT_STONE_SWORD, "minecraft:stone_sword");
        items.put(BoardCase.CRAFT_IRON_PICKAXE, "minecraft:iron_pickaxe");
        items.put(BoardCase.CRAFT_IRON_AXE, "minecraft:iron_axe");
        items.put(BoardCase.CRAFT_IRON_SHOVEL, "minecraft:iron_shovel");
        items.put(BoardCase.CRAFT_IRON_SWORD, "minecraft:iron_sword");
        items.put(BoardCase.CRAFT_DIAMOND_PICKAXE, "minecraft:diamond_pickaxe");
        items.put(BoardCase.CRAFT_DIAMOND_AXE, "minecraft:diamond_axe");
        items.put(BoardCase.CRAFT_DIAMOND_SHOVEL, "minecraft:diamond_shovel");
        items.put(BoardCase.CRAFT_DIAMOND_SWORD, "minecraft:diamond_sword");
        return Map.copyOf(items);
    }
}

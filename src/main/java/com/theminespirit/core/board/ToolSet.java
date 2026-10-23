package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Набор инструментов первых шагов: деревянные кирка, топор и лопата, затем каменные
 * кирка, топор, лопата и меч. Порядок ступеней — код (по нему открывается следующий
 * кейс), число комплектов на поселение — данные горячей таблицы board ({@code tools}):
 * {@code perSpirit} комплектов на духа, но не меньше {@code minSets}.
 */
public final class ToolSet {
    public static final String PER_SPIRIT_KEY = "perSpirit";
    public static final String MIN_SETS_KEY = "minSets";

    private static final List<BoardCase> WOODEN =
            List.of(BoardCase.CRAFT_WOODEN_PICKAXE, BoardCase.CRAFT_WOODEN_AXE, BoardCase.CRAFT_WOODEN_SHOVEL);
    private static final List<BoardCase> STONE = List.of(
            BoardCase.CRAFT_STONE_PICKAXE, BoardCase.CRAFT_STONE_AXE,
            BoardCase.CRAFT_STONE_SHOVEL, BoardCase.CRAFT_STONE_SWORD);
    private static final List<BoardCase> CHAIN = chainOf(WOODEN, STONE);
    private static final Map<BoardCase, String> ITEMS = items();
    private static final int DEFAULT_PER_SPIRIT = 1;
    private static final int DEFAULT_MIN_SETS = 1;
    private static final String MINECRAFT_PREFIX = "minecraft:";

    private ToolSet() {}

    public static List<BoardCase> chain() {
        return CHAIN;
    }

    public static List<BoardCase> wooden() {
        return WOODEN;
    }

    public static List<BoardCase> stone() {
        return STONE;
    }

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

    public static String stockKey(BoardCase boardCase) {
        String item = itemId(boardCase);
        return item.startsWith(MINECRAFT_PREFIX) ? item.substring(MINECRAFT_PREFIX.length()) : item;
    }

    public static Optional<BoardCase> nextMissing(Set<String> present, List<BoardCase> stage) {
        Objects.requireNonNull(
                present, "present не может быть null: без запасов не понять, " + "какого инструмента не хватает");
        Objects.requireNonNull(stage, "stage не может быть null: ступень набора — wooden(), stone() или chain()");
        for (BoardCase boardCase : stage) {
            if (!present.contains(stockKey(boardCase))) {
                return Optional.of(boardCase);
            }
        }
        return Optional.empty();
    }

    public static int count(Map<String, Integer> tools, int spirits) {
        Objects.requireNonNull(
                tools,
                "tools не может быть null: число комплектов читается из таблицы, "
                        + "пустую таблицу задают пустой картой");
        if (spirits < 0) {
            throw new IllegalArgumentException("Духов " + spirits + ": число не может быть отрицательным");
        }
        int perSpirit = tools.getOrDefault(PER_SPIRIT_KEY, DEFAULT_PER_SPIRIT);
        int minSets = tools.getOrDefault(MIN_SETS_KEY, DEFAULT_MIN_SETS);
        return Math.max(Math.max(minSets, perSpirit * spirits), 1);
    }

    public static int count(int spirits) {
        return count(DataTables.INSTANCE.board().current().tools(), spirits);
    }

    public static String toolKey(String key) {
        if (!PER_SPIRIT_KEY.equals(key) && !MIN_SETS_KEY.equals(key)) {
            throw new IllegalArgumentException("Неизвестный ключ таблицы инструментов '" + key
                    + "': допустимы " + PER_SPIRIT_KEY + " и " + MIN_SETS_KEY
                    + ", сверьтесь с таблицей board.json");
        }
        return key;
    }

    private static List<BoardCase> chainOf(List<BoardCase> wooden, List<BoardCase> stone) {
        List<BoardCase> chain = new ArrayList<>(wooden.size() + stone.size());
        chain.addAll(wooden);
        chain.addAll(stone);
        return List.copyOf(chain);
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
        return Map.copyOf(items);
    }
}

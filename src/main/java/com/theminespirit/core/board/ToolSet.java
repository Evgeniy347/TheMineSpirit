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

public final class ToolSet {
    private static final List<BoardCase> WOODEN =
            List.of(BoardCase.CRAFT_WOODEN_PICKAXE, BoardCase.CRAFT_WOODEN_AXE, BoardCase.CRAFT_WOODEN_SHOVEL);
    private static final List<BoardCase> STONE = List.of(
            BoardCase.CRAFT_STONE_PICKAXE, BoardCase.CRAFT_STONE_AXE,
            BoardCase.CRAFT_STONE_SHOVEL, BoardCase.CRAFT_STONE_SWORD);
    private static final List<BoardCase> CHAIN = chainOf(WOODEN, STONE);
    private static final Map<BoardCase, String> ITEMS = items();
    private static final Map<String, BoardCase> CASES = cases();
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final List<String> RANKED = List.of("wooden", "stone", "iron", "diamond", "netherite");

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

    public static Optional<BoardCase> caseFor(String itemId) {
        Objects.requireNonNull(itemId, "Предмет не может быть null: кейс ищется по имени инструмента");
        return Optional.ofNullable(CASES.get(shortId(itemId)));
    }

    public static String stockKey(BoardCase boardCase) {
        return shortId(itemId(boardCase));
    }

    public static boolean covered(BoardCase boardCase, Set<String> present) {
        Objects.requireNonNull(present, "present не может быть null: пустой запас задают пустым множеством");
        String key = stockKey(boardCase);
        int split = key.indexOf('_');
        String kind = key.substring(split + 1);
        int rank = RANKED.indexOf(key.substring(0, split));
        for (int tier = Math.max(rank, 0); tier < RANKED.size(); tier++) {
            if (present.contains(RANKED.get(tier) + "_" + kind)) {
                return true;
            }
        }
        return present.contains(key);
    }

    public static List<String> missing(Map<String, Integer> own, Set<String> craftable) {
        return missing(DataTables.INSTANCE.board().current().tools(), own, craftable);
    }

    public static List<String> missing(ToolsConfig config, Map<String, Integer> own, Set<String> craftable) {
        Objects.requireNonNull(config, "Набор инструментов не может быть null: без него нет цели духа");
        Objects.requireNonNull(own, "Свои инструменты не могут быть null: пустой набор задают пустой картой");
        Objects.requireNonNull(craftable, "Скрафчиваемые инструменты не могут быть null: пустой — пустым множеством");
        int target = ToolsConfig.rank(config.tier());
        List<String> missing = new ArrayList<>();
        for (String kind : config.set()) {
            int best = bestRank(own, kind);
            if (best >= target) {
                continue;
            }
            Optional<String> candidate = affordable(kind, best, target, craftable);
            if (candidate.isPresent()) {
                missing.add(candidate.orElseThrow());
            } else if (best < 0) {
                lowestCase(kind).ifPresent(missing::add);
            }
        }
        return List.copyOf(missing);
    }

    public static List<String> candidates(ToolsConfig config) {
        Objects.requireNonNull(config, "Набор инструментов не может быть null: без него нет кандидатов крафта");
        int target = ToolsConfig.rank(config.tier());
        List<String> candidates = new ArrayList<>();
        for (String kind : config.set()) {
            for (int rank = 0; rank <= target; rank++) {
                String item = ToolsConfig.itemId(ToolsConfig.TIERS.get(rank), kind);
                if (caseFor(item).isPresent()) {
                    candidates.add(item);
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static Optional<String> affordable(String kind, int best, int target, Set<String> craftable) {
        for (int rank = target; rank > best; rank--) {
            String item = ToolsConfig.itemId(ToolsConfig.TIERS.get(rank), kind);
            if (caseFor(item).isPresent() && craftable.contains(item)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> lowestCase(String kind) {
        for (String tier : ToolsConfig.TIERS) {
            String item = ToolsConfig.itemId(tier, kind);
            if (caseFor(item).isPresent()) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private static int bestRank(Map<String, Integer> own, String kind) {
        int best = -1;
        for (Map.Entry<String, Integer> entry : own.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            Optional<String> ownKind = ToolsConfig.kindOf(entry.getKey());
            if (ownKind.isEmpty() || !kind.equals(ownKind.orElseThrow())) {
                continue;
            }
            Optional<String> ownTier = ToolsConfig.tierOf(entry.getKey());
            if (ownTier.isPresent()) {
                best = Math.max(best, ToolsConfig.rank(ownTier.orElseThrow()));
            }
        }
        return best;
    }

    private static String shortId(String itemId) {
        return itemId.startsWith(MINECRAFT_PREFIX) ? itemId.substring(MINECRAFT_PREFIX.length()) : itemId;
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

    private static Map<String, BoardCase> cases() {
        Map<String, BoardCase> cases = new LinkedHashMap<>();
        for (Map.Entry<BoardCase, String> entry : items().entrySet()) {
            cases.put(shortId(entry.getValue()), entry.getKey());
        }
        return Map.copyOf(cases);
    }
}

package com.theminespirit.core.board.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class GearRules {
    private static final String NAMESPACE = "minecraft:";
    private static final List<String> ARMOR_SLOTS = List.of("HEAD", "CHEST", "LEGS", "FEET");
    private static final Set<String> TOOL_KINDS = Set.of("pickaxe", "axe", "shovel", "sword", "hoe");

    public record Upgrade(String item, GraphFacts.Position chest) {
        public Upgrade {
            Objects.requireNonNull(item, "Предмет не может быть null: надеть нечего");
            Objects.requireNonNull(chest, "Сундук не может быть null: без него вещь не забрать");
        }
    }

    private GearRules() {}

    public static Optional<Upgrade> equipUpgrade(
            GraphFacts.Spirit spirit, List<GraphFacts.Chest> chests, GearInputs.Settings settings) {
        Objects.requireNonNull(spirit, "Дух не может быть null: снаряжение подбирают ему");
        Map<String, Integer> tools = bestTools(spirit);
        Map<String, Integer> armor = armorRanks(spirit, settings);
        Upgrade best = null;
        int bestRank = -1;
        for (GraphFacts.Chest chest : chests) {
            for (String item : chest.contents().keySet()) {
                String name = shortId(item);
                String kind = toolKind(name);
                int rank;
                if (kind != null) {
                    rank = settings.rank(tier(name));
                    if (rank <= tools.getOrDefault(kind, -1)) {
                        continue;
                    }
                } else {
                    String slot = armorSlot(name);
                    if (slot == null) {
                        continue;
                    }
                    rank = settings.armorRank(armorTier(name));
                    if (rank <= armor.getOrDefault(slot, -1)) {
                        continue;
                    }
                }
                if (rank > bestRank) {
                    bestRank = rank;
                    best = new Upgrade(item, chest.position());
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static String craftToolItem(GraphFacts.Spirit spirit, String kind, int stage, GearInputs.Settings settings) {
        Objects.requireNonNull(spirit, "Дух не может быть null: инструмент куют ему");
        String tier =
                settings.requiredAt(stage).getOrDefault(kind, settings.tiers().get(0));
        return tier + "_" + kind;
    }

    public static Optional<String> missingArmor(GraphFacts.Spirit spirit, int stage, GearInputs.Settings settings) {
        Objects.requireNonNull(spirit, "Дух не может быть null: броню куют ему");
        Map<String, Integer> armor = armorRanks(spirit, settings);
        String tier = stage >= settings.armorStage() ? "iron" : "leather";
        for (String slot : ARMOR_SLOTS) {
            if (!armor.containsKey(slot)) {
                return Optional.of(tier + "_" + suffix(slot));
            }
        }
        return Optional.empty();
    }

    public static boolean needsTool(GraphFacts.Spirit spirit, String kind, int stage, GearInputs.Settings settings) {
        Objects.requireNonNull(spirit, "Дух не может быть null: инструмент куют ему");
        String tier =
                settings.requiredAt(stage).getOrDefault(kind, settings.tiers().get(0));
        int rank = settings.rank(tier);
        return rank >= 0 && bestTools(spirit).getOrDefault(kind, -1) < rank;
    }

    private static Map<String, Integer> bestTools(GraphFacts.Spirit spirit) {
        Map<String, Integer> best = new LinkedHashMap<>();
        for (GraphFacts.Tool tool : spirit.tools()) {
            if (tool.kind() != null) {
                best.merge(tool.kind(), tool.tier(), Math::max);
            }
        }
        return best;
    }

    private static Map<String, Integer> armorRanks(GraphFacts.Spirit spirit, GearInputs.Settings settings) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (GraphFacts.Armor armor : spirit.armor()) {
            ranks.merge(armor.slot(), settings.armorRank(armorTier(shortId(armor.item()))), Math::max);
        }
        return ranks;
    }

    private static @Nullable String toolKind(String name) {
        int split = name.lastIndexOf('_');
        if (split <= 0) {
            return null;
        }
        String kind = name.substring(split + 1);
        return TOOL_KINDS.contains(kind) ? kind : null;
    }

    private static String tier(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        int split = normalized.lastIndexOf('_');
        return split <= 0 ? "" : normalized.substring(0, split);
    }

    private static @Nullable String armorSlot(String name) {
        if (name.endsWith("_helmet")) {
            return "HEAD";
        }
        if (name.endsWith("_chestplate")) {
            return "CHEST";
        }
        if (name.endsWith("_leggings")) {
            return "LEGS";
        }
        if (name.endsWith("_boots")) {
            return "FEET";
        }
        return null;
    }

    private static String armorTier(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        int split = normalized.indexOf('_');
        return split <= 0 ? normalized : normalized.substring(0, split);
    }

    private static String shortId(String item) {
        return item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
    }

    private static String suffix(String slot) {
        return switch (slot) {
            case "HEAD" -> "helmet";
            case "CHEST" -> "chestplate";
            case "LEGS" -> "leggings";
            default -> "boots";
        };
    }
}

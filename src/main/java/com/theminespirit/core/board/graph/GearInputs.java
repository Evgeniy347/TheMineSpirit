package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class GearInputs {
    public static final String RESOURCE = "data/theminespirit/board/graph.d/gear.json";
    public static final String TOOL_WORN = "tool_worn";
    public static final String ARMOR_WORN = "armor_worn";
    public static final String EQUIP_BEST = "gear_equip_best";
    public static final String CRAFT_ARMOR = "gear_craft_armor";
    public static final String CRAFT_TOOL = "gear_craft_";
    public static final String KIT = "gear_kit_";
    public static final String EQUIP_TASK = "equip_best";
    public static final String ARMOR_TASK = "craft_armor";
    public static final String TOOL_TASK = "craft_tool:";
    private static final String NAMESPACE = "minecraft:";
    private static final List<String> ARMOR_SLOTS = List.of("HEAD", "CHEST", "LEGS", "FEET");
    private static final Map<String, String> ARMOR_SUFFIXES = Map.of(
            "helmet", "HEAD",
            "chestplate", "CHEST",
            "leggings", "LEGS",
            "boots", "FEET");

    public record Kit(String activity, List<String> tools) {
        public Kit {
            Objects.requireNonNull(activity, "Деятельность набора не может быть null: по ней выбирается роль");
            tools = List.copyOf(
                    Objects.requireNonNull(tools, "Инструменты набора не могут быть null: пустой — список"));
        }
    }

    public record Settings(
            int wornPercent,
            int armorStage,
            List<String> tiers,
            List<String> armorTiers,
            Map<Integer, Map<String, String>> required,
            Map<String, Kit> kits) {
        public Settings {
            if (wornPercent < 0 || wornPercent > 100) {
                throw new IllegalArgumentException(
                        "Порог износа " + wornPercent + "% вне [0, 100]: прочность не бывает отрицательной");
            }
            if (armorStage < 1) {
                throw new IllegalArgumentException(
                        "Этап брони " + armorStage + " меньше 1: поселение начинается с этапа 1");
            }
            tiers = List.copyOf(Objects.requireNonNull(tiers, "Уровни инструментов не могут быть null"));
            armorTiers = List.copyOf(Objects.requireNonNull(armorTiers, "Уровни брони не могут быть null"));
            required = Map.copyOf(Objects.requireNonNull(required, "Требования снаряжения не могут быть null"));
            kits = Map.copyOf(Objects.requireNonNull(kits, "Наборы по ролям не могут быть null"));
        }

        public Map<String, String> requiredAt(int stage) {
            Map<String, String> result = new LinkedHashMap<>();
            for (int step = 1; step <= stage; step++) {
                result.putAll(required.getOrDefault(step, Map.of()));
            }
            return result;
        }

        public int rank(String tier) {
            return tiers.indexOf(tier);
        }

        public int armorRank(String tier) {
            return armorTiers.indexOf(tier);
        }
    }

    private static volatile @Nullable Settings cached;

    private GearInputs() {}

    public static Settings settings() {
        Settings known = cached;
        if (known == null) {
            synchronized (GearInputs.class) {
                known = cached;
                if (known == null) {
                    known = parse(JsonResource.parse(JsonResource.readClasspath(RESOURCE), RESOURCE), RESOURCE);
                    cached = known;
                }
            }
        }
        return known;
    }

    static Settings parse(JsonNode root, String origin) {
        List<String> tiers = new ArrayList<>();
        root.path("gearTiers").forEach(node -> tiers.add(node.asText()));
        List<String> armorTiers = new ArrayList<>();
        root.path("gearArmorTiers").forEach(node -> armorTiers.add(node.asText()));
        Map<Integer, Map<String, String>> required = new LinkedHashMap<>();
        root.path("gearRequired").properties().forEach(entry -> {
            Map<String, String> stage = new LinkedHashMap<>();
            entry.getValue()
                    .properties()
                    .forEach(node -> stage.put(node.getKey(), node.getValue().asText()));
            try {
                required.put(Integer.parseInt(entry.getKey()), stage);
            } catch (NumberFormatException error) {
                throw new IllegalStateException(
                        "Файл '" + origin + "': этап снаряжения '" + entry.getKey()
                                + "' — не число: этап задают номером",
                        error);
            }
        });
        Map<String, Kit> kits = new LinkedHashMap<>();
        root.path("gearKits").properties().forEach(entry -> {
            List<String> tools = new ArrayList<>();
            entry.getValue().path("tools").forEach(node -> tools.add(node.asText()));
            kits.put(entry.getKey(), new Kit(entry.getValue().path("activity").asText(entry.getKey()), tools));
        });
        return new Settings(
                root.path("gearWorn").path("percent").asInt(20),
                root.path("gearWorn").path("armorStage").asInt(2),
                tiers,
                armorTiers,
                required,
                kits);
    }

    public static boolean handlesTask(String taskId) {
        return EQUIP_TASK.equals(taskId) || ARMOR_TASK.equals(taskId) || taskId.startsWith(TOOL_TASK);
    }

    public static Set<String> events(GraphFacts facts, int stage) {
        Settings settings = settings();
        Map<String, String> required = settings.requiredAt(stage);
        Set<String> events = new LinkedHashSet<>();
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            if (spirit.tools().stream().anyMatch(tool -> worn(tool.durability(), tool.maxDurability(), settings))) {
                events.add(TOOL_WORN);
            }
            if (spirit.armor().stream().anyMatch(armor -> worn(armor.durability(), armor.maxDurability(), settings))) {
                events.add(ARMOR_WORN);
            }
            if (betterInChests(spirit, facts.chests(), settings)) {
                events.add(EQUIP_BEST);
            }
            Map<String, Integer> best = bestTools(spirit);
            required.forEach((kind, tier) -> {
                int rank = settings.rank(tier);
                if (rank >= 0 && best.getOrDefault(kind, -1) < rank) {
                    events.add(CRAFT_TOOL + kind);
                }
            });
            if (stage >= settings.armorStage() && armorSlots(spirit) < ARMOR_SLOTS.size()) {
                events.add(CRAFT_ARMOR);
            }
        }
        for (Map.Entry<String, Kit> kit : settings.kits().entrySet()) {
            boolean active = facts.spirits().values().stream()
                    .anyMatch(spirit -> kit.getValue().activity().equals(spirit.activity()));
            if (!active) {
                continue;
            }
            boolean lacks = kit.getValue().tools().isEmpty()
                    || facts.spirits().values().stream()
                            .filter(spirit -> kit.getValue().activity().equals(spirit.activity()))
                            .anyMatch(spirit -> kit.getValue().tools().stream().anyMatch(kind -> {
                                String tier = required.get(kind);
                                return tier != null && bestTools(spirit).getOrDefault(kind, -1) < settings.rank(tier);
                            }));
            if (lacks) {
                events.add(KIT + kit.getKey());
            }
        }
        return Set.copyOf(events);
    }

    private static boolean worn(int durability, int maxDurability, Settings settings) {
        return maxDurability > 0 && durability * 100 / maxDurability <= settings.wornPercent();
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

    private static int armorSlots(GraphFacts.Spirit spirit) {
        Set<String> slots = new LinkedHashSet<>();
        spirit.armor().forEach(armor -> slots.add(armor.slot()));
        return slots.size();
    }

    private static boolean betterInChests(GraphFacts.Spirit spirit, List<GraphFacts.Chest> chests, Settings settings) {
        Map<String, Integer> tools = bestTools(spirit);
        Map<String, Integer> armor = armorRanks(spirit, settings);
        for (GraphFacts.Chest chest : chests) {
            for (String item : chest.contents().keySet()) {
                String name = shortId(item);
                String kind = toolKind(name);
                if (kind != null) {
                    int tier = settings.rank(tier(name));
                    if (tier > tools.getOrDefault(kind, -1)) {
                        return true;
                    }
                }
                String slot = armorSlot(name);
                if (slot != null) {
                    int rank = settings.armorRank(armorTier(name));
                    if (rank > armor.getOrDefault(slot, -1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Map<String, Integer> armorRanks(GraphFacts.Spirit spirit, Settings settings) {
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
        return Set.of("pickaxe", "axe", "shovel", "sword", "hoe").contains(kind) ? kind : null;
    }

    private static String tier(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        int split = normalized.lastIndexOf('_');
        return split <= 0 ? "" : normalized.substring(0, split);
    }

    private static @Nullable String armorSlot(String name) {
        for (Map.Entry<String, String> suffix : ARMOR_SUFFIXES.entrySet()) {
            if (name.endsWith("_" + suffix.getKey())) {
                return suffix.getValue();
            }
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
}

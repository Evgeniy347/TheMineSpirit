package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GraphStockNote {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphStockNote.class);
    private static final String AXE = "_axe";
    private static final String MINECRAFT = "minecraft:";
    private static final List<String> TIER_ORDER = List.of("wooden", "stone", "iron", "diamond", "netherite", "golden");
    private static final Map<String, String> TIER_NAMES = Map.of(
            "wooden", "деревянных",
            "stone", "каменных",
            "iron", "железных",
            "diamond", "алмазных",
            "netherite", "незеритовых",
            "golden", "золотых");
    private static String last = "";

    private GraphStockNote() {}

    static void log(GraphFacts facts, List<SpiritEntity> spirits) {
        String line = line(facts, spirits);
        if (!line.equals(last)) {
            last = line;
            LOGGER.info(line);
        }
    }

    static String line(GraphFacts facts, List<SpiritEntity> spirits) {
        Map<String, Integer> chestAxes = new LinkedHashMap<>();
        for (GraphFacts.Chest chest : facts.chests()) {
            axes(chest.contents(), chestAxes);
        }
        Map<String, Integer> inventoryAxes = new LinkedHashMap<>();
        Map<String, Integer> handAxes = new LinkedHashMap<>();
        for (SpiritEntity spirit : spirits) {
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                ItemStack stack = spirit.getInventory().getItem(slot);
                if (isAxe(stack)) {
                    add(
                            inventoryAxes,
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            stack.getCount());
                }
            }
            if (spirit.mainHandOwned() && isAxe(spirit.getMainHandItem())) {
                ItemStack stack = spirit.getMainHandItem();
                add(handAxes, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
            }
        }
        return line(facts.chests().size(), chestAxes, inventoryAxes, handAxes);
    }

    static String line(
            int chests,
            Map<String, Integer> chestAxes,
            Map<String, Integer> inventoryAxes,
            Map<String, Integer> handAxes) {
        int chestCount = sum(chestAxes);
        int inventoryCount = sum(inventoryAxes);
        int handCount = sum(handAxes);
        int axes = chestCount + inventoryCount + handCount;
        StringBuilder line = new StringBuilder("[graph] запасы: сундуков ")
                .append(chests)
                .append(", топоров ")
                .append(axes)
                .append(" (сундуки ")
                .append(chestCount)
                .append(", духи ")
                .append(inventoryCount)
                .append(", в руках ")
                .append(handCount);
        Map<String, Integer> tiers = new LinkedHashMap<>(chestAxes);
        inventoryAxes.forEach((tier, count) -> tiers.merge(tier, count, Integer::sum));
        handAxes.forEach((tier, count) -> tiers.merge(tier, count, Integer::sum));
        appendTiers(line, tiers);
        return line.append(")").toString();
    }

    private static void appendTiers(StringBuilder line, Map<String, Integer> tiers) {
        List<String> shown = TIER_ORDER.stream()
                .filter(tier -> tiers.getOrDefault(tier, 0) > 0)
                .toList();
        if (shown.isEmpty()) {
            return;
        }
        line.append("; ");
        for (int index = 0; index < shown.size(); index++) {
            if (index > 0) {
                line.append(", ");
            }
            String tier = shown.get(index);
            line.append(TIER_NAMES.getOrDefault(tier, tier)).append(' ').append(tiers.get(tier));
        }
    }

    private static void axes(Map<String, Integer> contents, Map<String, Integer> tiers) {
        contents.forEach((item, count) -> {
            if (count > 0 && isAxe(item)) {
                add(tiers, tier(item), count);
            }
        });
    }

    private static void add(Map<String, Integer> tiers, String item, int count) {
        if (count > 0) {
            tiers.merge(tier(item), count, Integer::sum);
        }
    }

    private static int sum(Map<String, Integer> tiers) {
        return tiers.values().stream().mapToInt(Integer::intValue).sum();
    }

    static String tier(String item) {
        String id = item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
        int split = id.lastIndexOf('_');
        return split <= 0 ? id : id.substring(0, split);
    }

    private static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty()
                && isAxe(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static boolean isAxe(String item) {
        String id = item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
        return id.endsWith(AXE);
    }
}

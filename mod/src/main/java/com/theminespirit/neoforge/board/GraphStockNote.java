package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

final class GraphStockNote {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String AXE = "_axe";
    private static final String MINECRAFT = "minecraft:";
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
        int chestAxes = 0;
        for (GraphFacts.Chest chest : facts.chests()) {
            chestAxes += axes(chest.contents());
        }
        int inventoryAxes = 0;
        int handAxes = 0;
        for (SpiritEntity spirit : spirits) {
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                if (isAxe(spirit.getInventory().getItem(slot))) {
                    inventoryAxes += spirit.getInventory().getItem(slot).getCount();
                }
            }
            if (spirit.mainHandOwned() && isAxe(spirit.getMainHandItem())) {
                handAxes += spirit.getMainHandItem().getCount();
            }
        }
        int axes = chestAxes + inventoryAxes + handAxes;
        return "[graph] запасы: сундуков " + facts.chests().size() + ", топоров " + axes + " (сундуки " + chestAxes
                + ", духи " + inventoryAxes + ", в руках " + handAxes + ")";
    }

    private static int axes(Map<String, Integer> contents) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            if (entry.getValue() > 0 && isAxe(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
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

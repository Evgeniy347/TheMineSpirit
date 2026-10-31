package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.ToolBreakCost;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CheapBlocks {
    private CheapBlocks() {}

    public static Map<String, Integer> counts(SpiritEntity spirit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : ToolBreakCost.CHEAP_ORDER) {
            counts.put(id, BlockItems.countIn(spirit.getInventory(), id));
        }
        return counts;
    }

    static int count(SpiritEntity spirit, String blockId) {
        return BlockItems.countIn(spirit.getInventory(), blockId);
    }
}

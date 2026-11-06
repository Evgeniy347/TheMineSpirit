package com.theminespirit.neoforge;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class OwnWood {
    private static final int PLANKS_PER_LOG = 4;
    private static final String PLANKS = "_planks";

    private OwnWood() {}

    public static String species(String item) {
        String path = item.substring(item.indexOf(':') + 1);
        for (String suffix : new String[] {"_fence_gate", "_fence", PLANKS, "_stairs", "_door", "_slab"}) {
            if (path.endsWith(suffix)) {
                return path.substring(0, path.length() - suffix.length());
            }
        }
        return "";
    }

    public static boolean stick(String item) {
        return "stick".equals(item.substring(item.indexOf(':') + 1));
    }

    public static int plankCost(String item, int count) {
        String path = item.substring(item.indexOf(':') + 1);
        if (stick(item)) {
            return (count + 1) / 2;
        }
        if (path.endsWith("_fence_gate")) {
            return count * 4;
        }
        if (path.endsWith("_fence")) {
            return (count + 2) / 3 * 5;
        }
        if (path.endsWith("_stairs")) {
            return (count + 3) / 4 * 6;
        }
        if (path.endsWith("_door")) {
            return (count + 2) / 3 * 6;
        }
        if (path.endsWith("_slab")) {
            return (count + 5) / 6 * 3;
        }
        return count;
    }

    public static int planksHeld(Container inventory, String species) {
        int have = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (id.equals(species + PLANKS)) {
                have += stack.getCount();
            } else if (id.equals(species + "_log")) {
                have += stack.getCount() * PLANKS_PER_LOG;
            }
        }
        return have;
    }

    public static int planksIn(Map<String, Integer> stock, String species) {
        int planks = 0;
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            String path = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
            if (path.equals(species + PLANKS)) {
                planks += entry.getValue();
            } else if (path.equals(species + "_log")) {
                planks += entry.getValue() * PLANKS_PER_LOG;
            }
        }
        return planks;
    }
}

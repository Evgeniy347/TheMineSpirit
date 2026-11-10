package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphKits;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class More4Scripts {
    static final String MINECRAFT = "minecraft:";
    static final String NEAREST = "nearest";
    static final int CONTAINER_RADIUS = 8;
    static final int MAX_COUNT = 64;

    private More4Scripts() {}

    static Map<String, Object> patrolParams(List<BlockCoord> stops) {
        List<String> points = new ArrayList<>(stops.size());
        for (BlockCoord stop : stops) {
            points.add(point(stop));
        }
        return Map.of("points", List.copyOf(points), "mode", "loop", "loops", 1);
    }

    static String toolBlock(String kind) {
        return switch (kind) {
            case "axe" -> "oak_log";
            case "hoe" -> "grass_block";
            default -> "stone";
        };
    }

    static Optional<String> rareItem(Map<String, Integer> items, Set<String> rares) {
        Set<String> known = new LinkedHashSet<>();
        for (String rare : rares) {
            known.add(rare.startsWith(MINECRAFT) ? rare.substring(MINECRAFT.length()) : rare);
        }
        for (String item : items.keySet().stream().sorted().toList()) {
            String name = item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
            if (known.contains(name) && items.getOrDefault(item, 0) > 0) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    static Map<String, Object> withdraw(String item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", NEAREST);
        params.put("item", item);
        params.put("count", 1);
        params.put("radius", CONTAINER_RADIUS);
        return Map.copyOf(params);
    }

    static Map<String, Object> drop(String item, ServerPlayer player) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("item", item);
        params.put("count", 1);
        params.put("to", "player:" + player.getGameProfile().getName());
        return Map.copyOf(params);
    }

    static String heldKind(SpiritEntity spirit, SupplyGraph graph) {
        List<String> hand = new ArrayList<>(1);
        ItemStack main = spirit.getMainHandItem();
        if (!main.isEmpty()) {
            hand.add(BuiltInRegistries.ITEM.getKey(main.getItem()).toString());
        }
        return GraphKits.of(hand, graph).keySet().stream().findFirst().orElse(null);
    }

    static List<String> itemIds(SpiritEntity spirit) {
        List<String> items = new ArrayList<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        ItemStack main = spirit.getMainHandItem();
        if (!main.isEmpty()) {
            items.add(BuiltInRegistries.ITEM.getKey(main.getItem()).toString());
        }
        return items;
    }

    static Map<String, Integer> inventory(SpiritEntity spirit) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        return Map.copyOf(items);
    }

    static double distance(BlockCoord one, BlockCoord two) {
        double dx = one.x() - two.x();
        double dy = one.y() - two.y();
        double dz = one.z() - two.z();
        return dx * dx + dy * dy + dz * dz;
    }

    static String point(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }
}

package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildClutter;
import com.theminespirit.core.building.BuildPhase;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SettlementChests;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.DepositSelection;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.Slice;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BuildSupplyTrip {
    static final int MAX_TRIPS = 32;

    private static final Logger LOGGER = LogUtils.getLogger();

    private BuildSupplyTrip() {}

    static List<ScriptCall> plan(SpiritEntity spirit, SchematicSite site, Predicate<Schematic.PlacedEntry> phase) {
        return fetch(spirit, site, need(spirit, site, phase));
    }

    static List<ScriptCall> plan(SpiritEntity spirit, SchematicSite site, int band, int bottomY) {
        Predicate<Schematic.PlacedEntry> phase = entry -> band < 0
                || (BuildPhase.rough(entry.block())
                        && BuildPhase.band(entry.pos().y() - bottomY) >= band);
        return plan(spirit, site, phase);
    }

    static List<ScriptCall> exact(SpiritEntity spirit, SchematicSite site, Map<Item, Integer> wanted) {
        Map<Item, Integer> missing = need(spirit, site, entry -> true);
        Map<Item, Integer> need = new LinkedHashMap<>();
        wanted.forEach((item, count) -> {
            int left = Math.min(count, missing.getOrDefault(item, 0));
            if (left > 0) {
                need.merge(item, left, Integer::sum);
            }
        });
        return fetch(spirit, site, need);
    }

    static int junkSlots(SpiritEntity spirit, SchematicSite site) {
        Container inventory = spirit.getInventory();
        List<@Nullable String> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            slots.add(
                    stack.isEmpty()
                            ? null
                            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        Set<Integer> keep = spirit.isDemo() ? Set.of() : kitSlots(spirit);
        return BuildClutter.junk(slots, neededItems(site), keep).size();
    }

    private static List<ScriptCall> fetch(SpiritEntity spirit, SchematicSite site, Map<Item, Integer> need) {
        if (need.isEmpty()) {
            return List.of();
        }
        List<BlockPos> chests = containers(spirit);
        List<ScriptCall> release = new ArrayList<>();
        int freeSlots = emptySlots(spirit) + releaseClutter(spirit, site, need, chests, release);
        if (freeSlots == 0) {
            return List.of();
        }
        List<ScriptCall> steps = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : chests) {
            if (freeSlots <= 0) {
                break;
            }
            BlockPos other = otherHalf(spirit, pos);
            if (!seen.add(pos) || (!other.equals(pos) && seen.contains(other))) {
                continue;
            }
            if (!spirit.level().isLoaded(pos)) {
                continue;
            }
            if (!(ContainerAccess.open(spirit.level(), pos) instanceof ContainerAccess.Opened opened)) {
                continue;
            }
            freeSlots = take(opened.container(), pos, need, freeSlots, steps);
        }
        if (steps.isEmpty()) {
            return List.of();
        }
        release.addAll(steps);
        return List.copyOf(release);
    }

    static int emptySlots(SpiritEntity spirit) {
        int free = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private static int releaseClutter(
            SpiritEntity spirit,
            SchematicSite site,
            Map<Item, Integer> need,
            List<BlockPos> chests,
            List<ScriptCall> steps) {
        Container inventory = spirit.getInventory();
        List<@Nullable String> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            slots.add(
                    stack.isEmpty()
                            ? null
                            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        Set<Integer> keep = spirit.isDemo() ? Set.of() : kitSlots(spirit);
        List<Integer> junk = BuildClutter.junk(slots, neededItems(site), keep);
        int wanted = 0;
        for (Map.Entry<Item, Integer> item : need.entrySet()) {
            int max = item.getKey().getDefaultMaxStackSize();
            wanted += (item.getValue() + max - 1) / max;
        }
        List<Integer> release = BuildClutter.toRelease(junk, emptySlots(spirit), wanted);
        if (release.isEmpty() || (!spirit.isDemo() && chests.isEmpty())) {
            return 0;
        }
        for (int slot : release) {
            ItemStack stack = inventory.getItem(slot);
            if (spirit.isDemo()) {
                LOGGER.info(
                        "Стройка: демо — лишнее {} ×{} выброшено, слот {} под порцию",
                        slots.get(slot),
                        stack.getCount(),
                        slot);
                inventory.setItem(slot, ItemStack.EMPTY);
            } else {
                BlockPos chest = chests.get(0);
                steps.add(new ScriptCall(
                        "deposit",
                        Map.of(
                                "container",
                                chest.getX() + "," + chest.getY() + "," + chest.getZ(),
                                "item",
                                String.valueOf(slots.get(slot)),
                                "count",
                                Math.min(stack.getCount(), CatalogLimits.COUNT_MAX),
                                "radius",
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
            }
        }
        return release.size();
    }

    private static Set<String> neededItems(SchematicSite site) {
        Set<String> needed = new HashSet<>();
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (BlockItems.hasBlockItem(entry.block())
                    && !SchematicActions.isActionPlaced(entry.block())
                    && !site.matches(entry)) {
                needed.add(BuiltInRegistries.ITEM
                        .getKey(BlockItems.itemFor(entry.block()))
                        .toString());
            }
        }
        return needed;
    }

    private static Set<Integer> kitSlots(SpiritEntity spirit) {
        List<Stack> stacks = new StackMapper().map(spirit.getInventory());
        BaseKit kit = KitFor.kitFor(spirit.currentActivity().orElse(null), spirit.isChild());
        Set<Integer> giveAway = new HashSet<>();
        for (Slice slice : DepositSelection.surplus(stacks, kit, InventoryTables.values(), -1)) {
            giveAway.add(slice.slot());
        }
        Set<Integer> keep = new HashSet<>();
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!giveAway.contains(slot)) {
                keep.add(slot);
            }
        }
        return keep;
    }

    private static Map<Item, Integer> need(
            SpiritEntity spirit, SchematicSite site, Predicate<Schematic.PlacedEntry> phase) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (BlockItems.hasBlockItem(entry.block())
                    && !SchematicActions.isActionPlaced(entry.block())
                    && !site.matches(entry)
                    && phase.test(entry)) {
                need.merge(BlockItems.itemFor(entry.block()), 1, Integer::sum);
            }
        }
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                need.computeIfPresent(
                        stack.getItem(), (item, count) -> count > stack.getCount() ? count - stack.getCount() : null);
            }
        }
        return need;
    }

    private static int take(
            Container container, BlockPos pos, Map<Item, Integer> need, int freeSlots, List<ScriptCall> steps) {
        Map<Item, Integer> taken = new LinkedHashMap<>();
        int free = freeSlots;
        for (Map.Entry<Item, Integer> wanted : need.entrySet()) {
            int have = container.countItem(wanted.getKey());
            int max = wanted.getKey().getDefaultMaxStackSize();
            int fits = free * max;
            int count = Math.min(Math.min(wanted.getValue(), have), fits);
            if (count <= 0) {
                continue;
            }
            taken.put(wanted.getKey(), count);
            free -= (count + max - 1) / max;
            if (free <= 0) {
                break;
            }
        }
        for (Map.Entry<Item, Integer> item : taken.entrySet()) {
            need.computeIfPresent(
                    item.getKey(), (key, count) -> count > item.getValue() ? count - item.getValue() : null);
            String id = BuiltInRegistries.ITEM.getKey(item.getKey()).toString();
            for (int left = item.getValue(); left > 0; left -= CatalogLimits.COUNT_MAX) {
                steps.add(new ScriptCall(
                        "withdraw",
                        Map.of(
                                "container",
                                pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                                "item",
                                id,
                                "count",
                                Math.min(left, CatalogLimits.COUNT_MAX),
                                "radius",
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
            }
        }
        return free;
    }

    static List<BlockPos> containers(SpiritEntity spirit) {
        SettlementChests registry = registry(spirit);
        BlockCoord feet = coord(spirit.blockPosition());
        List<BlockPos> found = new ArrayList<>();
        for (BlockCoord pos : registry.nearest(feet, registry.size())) {
            found.add(new BlockPos(pos.x(), pos.y(), pos.z()));
        }
        return found;
    }

    static SettlementChests registry(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Реестр сундуков живёт на серверном уровне: дух вне сервера");
        }
        return SettlementChestStore.get(level).chests();
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    static BlockPos otherHalf(SpiritEntity spirit, BlockPos pos) {
        BlockState state = spirit.level().getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return pos;
    }
}

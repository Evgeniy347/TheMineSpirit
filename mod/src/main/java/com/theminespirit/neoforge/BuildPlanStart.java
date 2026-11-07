package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildPlanner;
import com.theminespirit.core.building.BuildSupplyPick;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class BuildPlanStart {
    record Prepared(BlockCoord supply, BuildPlanner.Start start, Map<String, Item> items) {}

    private BuildPlanStart() {}

    static Prepared of(SpiritEntity spirit, SchematicSite site) {
        BlockCoord origin = site.origin();
        Map<String, Item> items = new LinkedHashMap<>();
        Map<String, String> blocks = new LinkedHashMap<>();
        Map<String, Integer> required = new TreeMap<>();
        Set<BlockCoord> done = new HashSet<>();
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (SchematicActions.isActionPlaced(entry.block()) || SchematicActions.isFluidPlaced(entry.block())) {
                continue;
            }
            if (optionalPresent(spirit, entry)) {
                done.add(relative(entry.pos(), origin));
                continue;
            }
            String key = BuildPlanner.item(entry.block());
            items.putIfAbsent(key, BlockItems.itemFor(entry.block()));
            blocks.putIfAbsent(key, entry.block());
            if (site.matches(entry) && site.secondPartMatches(entry)) {
                done.add(relative(entry.pos(), origin));
            } else if (BlockItems.hasBlockItem(entry.block())) {
                required.merge(key, 1, Integer::sum);
            }
        }
        Set<BlockCoord> skip = new HashSet<>();
        for (Schematic.PlacedEntry entry : site.schematic().entries(origin, site.rotation())) {
            if (!BlockItems.hasBlockItem(entry.block())
                    || SchematicActions.isActionPlaced(entry.block())
                    || SchematicActions.isFluidPlaced(entry.block())) {
                boolean present = spirit.level()
                        .getBlockState(SchematicSite.toPos(entry.pos()))
                        .is(BlockStates.blockFor(entry.block()));
                (present ? done : skip).add(relative(entry.pos(), origin));
            }
        }
        Map<String, Integer> inventory = new TreeMap<>();
        Set<Item> materials = new HashSet<>(items.values());
        blocks.forEach((key, block) -> {
            int count = BlockItems.countIn(spirit.getInventory(), block);
            if (count > 0) {
                inventory.put(key, count);
            }
        });
        int materialSlots = materialSlots(spirit, materials);
        BlockPos spiritPos = spirit.blockPosition();
        BlockCoord feet = new BlockCoord(spiritPos.getX(), spiritPos.getY(), spiritPos.getZ());
        Set<String> needed = needed(inventory, required);
        Set<BlockPos> seen = new HashSet<>();
        List<BuildSupplyPick.Candidate> candidates = new ArrayList<>();
        Map<BlockCoord, Map<String, Integer>> found = new LinkedHashMap<>();
        for (BlockPos pos : BuildSupplyTrip.containers(spirit)) {
            BlockPos other = BuildSupplyTrip.otherHalf(spirit, pos);
            if (!seen.add(pos) || (!other.equals(pos) && seen.contains(other))) {
                continue;
            }
            if (!spirit.level().isLoaded(pos)) {
                continue;
            }
            if (ContainerAccess.open(spirit.level(), pos) instanceof ContainerAccess.Opened opened) {
                Container container = opened.container();
                Map<String, Integer> counts = new TreeMap<>();
                boolean hasNeeded = false;
                for (Map.Entry<String, Item> item : items.entrySet()) {
                    int count = container.countItem(item.getValue());
                    if (count > 0) {
                        counts.put(item.getKey(), count);
                        hasNeeded |= needed.contains(item.getKey());
                    }
                }
                Optional<BlockCoord> stand = BuildSupplyReach.feet(site, pos);
                if (stand.isPresent()) {
                    BlockCoord chest = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
                    candidates.add(new BuildSupplyPick.Candidate(chest, stand.get(), hasNeeded));
                    found.put(chest, counts);
                }
            }
        }
        Optional<Set<BlockCoord>> walkable = site.walkable(feet, BuildSchematicScript.WALK_MARGIN);
        List<BuildSupplyPick.Candidate> reachable = candidates.stream()
                .filter(candidate -> BuildSupplyReach.usable(site, feet, walkable, candidate.stand()))
                .toList();
        Map<String, Integer> chest = new TreeMap<>();
        for (BuildSupplyPick.Candidate candidate : reachable) {
            found.getOrDefault(candidate.chest(), Map.of())
                    .forEach((key, count) -> chest.merge(key, count, Integer::sum));
        }
        boolean fetchNeeded = required.entrySet().stream()
                .anyMatch(need -> inventory.getOrDefault(need.getKey(), 0) < need.getValue());
        BlockCoord supply =
                fetchNeeded ? relative(BuildSupplyPick.choose(feet, reachable), origin) : relative(feet, origin);
        BuildPlanner.Start start = new BuildPlanner.Start(
                done,
                skip,
                inventory,
                chest,
                BuildSupplyTrip.emptySlots(spirit) + materialSlots,
                BuildSupplyTrip.junkSlots(spirit, site),
                stacks(items));
        return new Prepared(supply, start, Map.copyOf(items));
    }

    private static int materialSlots(SpiritEntity spirit, Set<Item> materials) {
        int slots = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty() && materials.contains(stack.getItem())) {
                slots++;
            }
        }
        return slots;
    }

    private static boolean optionalPresent(SpiritEntity spirit, Schematic.PlacedEntry entry) {
        if (!entry.optional()) {
            return false;
        }
        BlockState present = spirit.level().getBlockState(SchematicSite.toPos(entry.pos()));
        return !present.isAir() && !present.canBeReplaced();
    }

    private static Set<String> needed(Map<String, Integer> inventory, Map<String, Integer> required) {
        Set<String> needed = new HashSet<>();
        required.forEach((key, count) -> {
            if (inventory.getOrDefault(key, 0) < count) {
                needed.add(key);
            }
        });
        return needed;
    }

    private static Map<String, Integer> stacks(Map<String, Item> items) {
        Map<String, Integer> stacks = new TreeMap<>();
        items.forEach((key, item) -> {
            int size = item.getDefaultMaxStackSize();
            if (size != BuildPlanner.STACK) {
                stacks.put(key, size);
            }
        });
        return stacks;
    }

    private static BlockCoord relative(BlockCoord cell, BlockCoord origin) {
        return new BlockCoord(cell.x() - origin.x(), cell.y() - origin.y(), cell.z() - origin.z());
    }
}

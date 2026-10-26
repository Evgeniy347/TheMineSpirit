package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

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
            if (!BlockItems.hasBlockItem(entry.block())) {
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
        int materialSlots = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty() && materials.contains(stack.getItem())) {
                materialSlots++;
            }
        }
        List<BlockPos> chests = BuildSupplyTrip.containers(spirit);
        Map<String, Integer> chest = new TreeMap<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos supplyChest = null;
        for (BlockPos pos : chests) {
            BlockPos other = BuildSupplyTrip.otherHalf(spirit, pos);
            if (!seen.add(pos) || (!other.equals(pos) && seen.contains(other))) {
                continue;
            }
            if (!spirit.level().isLoaded(pos)) {
                continue;
            }
            if (ContainerAccess.open(spirit.level(), pos) instanceof ContainerAccess.Opened opened) {
                Container container = opened.container();
                for (Map.Entry<String, Item> item : items.entrySet()) {
                    int count = container.countItem(item.getValue());
                    if (count > 0) {
                        chest.merge(item.getKey(), count, Integer::sum);
                        if (supplyChest == null) {
                            supplyChest = pos;
                        }
                    }
                }
            }
        }
        BlockCoord supply = supplyPoint(spirit, site, origin, chests, supplyChest, inventory, required);
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

    private static boolean optionalPresent(SpiritEntity spirit, Schematic.PlacedEntry entry) {
        if (!entry.optional()) {
            return false;
        }
        BlockState present = spirit.level().getBlockState(SchematicSite.toPos(entry.pos()));
        return !present.isAir() && !present.canBeReplaced();
    }

    private static BlockCoord supplyPoint(
            SpiritEntity spirit,
            SchematicSite site,
            BlockCoord origin,
            List<BlockPos> chests,
            @Nullable BlockPos supplyChest,
            Map<String, Integer> inventory,
            Map<String, Integer> required) {
        BlockCoord feet = relative(
                new BlockCoord(
                        spirit.blockPosition().getX(),
                        spirit.blockPosition().getY(),
                        spirit.blockPosition().getZ()),
                origin);
        boolean fetchNeeded = required.entrySet().stream()
                .anyMatch(need -> inventory.getOrDefault(need.getKey(), 0) < need.getValue());
        BlockPos supplyPos =
                !fetchNeeded ? null : (supplyChest != null ? supplyChest : (chests.isEmpty() ? null : chests.get(0)));
        return supplyPos == null ? feet : supplyFeet(site, supplyPos).orElse(feet);
    }

    private static Optional<BlockCoord> supplyFeet(SchematicSite site, BlockPos chest) {
        BlockCoord origin = site.origin();
        @Nullable BlockCoord best = null;
        double bestDistance = Double.MAX_VALUE;
        BuildBox box = site.networkBox(0);
        BlockPos centre = new BlockPos((box.minX() + box.maxX()) / 2, origin.y(), (box.minZ() + box.maxZ()) / 2);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos cell = chest.relative(side);
            BlockCoord coord = new BlockCoord(cell.getX(), cell.getY(), cell.getZ());
            if (cell.getY() != origin.y()
                    || !site.standable(coord)
                    || site.schemeCells().contains(coord)
                    || !site.outsideFootprint(coord)) {
                continue;
            }
            double distance = cell.distSqr(centre);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = relative(coord, origin);
            }
        }
        return Optional.ofNullable(best);
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

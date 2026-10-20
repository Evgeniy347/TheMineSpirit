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
import org.jspecify.annotations.Nullable;

/**
 * Начало плана стройки из мира: что из схемы уже стоит, сколько материала при духе и в сундуках у площадки, сколько
 * слотов под материал и лишнего, где дух стоит после захода. Ключи материала — {@link BuildPlanner#item}; обратно в
 * предмет — {@link Prepared#items()}.
 */
final class BuildPlanStart {
    record Prepared(BlockCoord supply, BuildPlanner.Start start, Map<String, Item> items) {}

    private BuildPlanStart() {}

    static Prepared of(SpiritEntity spirit, SchematicSite site) {
        BlockCoord origin = site.origin();
        Map<String, Item> items = new LinkedHashMap<>();
        Map<String, String> blocks = new LinkedHashMap<>();
        Set<BlockCoord> done = new HashSet<>();
        for (Schematic.PlacedEntry entry : site.primaries()) {
            String key = BuildPlanner.item(entry.block());
            items.putIfAbsent(key, BlockItems.itemFor(entry.block()));
            blocks.putIfAbsent(key, entry.block());
            if (site.matches(entry) && site.secondPartMatches(entry)) {
                done.add(relative(entry.pos(), origin));
            }
        }
        // Блоки без предмета (вода колодца) дух не ставит: стоят в мире — для плана они готовы, нет — их нет совсем
        // (ставятся после стройки; без этого план вставал на воду, которой ещё нет).
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
        List<BlockPos> chests = BuildSupplyTrip.containers(spirit, site);
        Map<String, Integer> chest = new TreeMap<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : chests) {
            BlockPos other = BuildSupplyTrip.otherHalf(spirit, pos);
            if (!seen.add(pos) || (!other.equals(pos) && seen.contains(other))) {
                continue;
            }
            if (ContainerAccess.open(spirit.level(), pos) instanceof ContainerAccess.Opened opened) {
                Container container = opened.container();
                items.forEach((key, item) -> {
                    int count = container.countItem(item);
                    if (count > 0) {
                        chest.merge(key, count, Integer::sum);
                    }
                });
            }
        }
        BlockCoord feet = relative(
                new BlockCoord(
                        spirit.blockPosition().getX(),
                        spirit.blockPosition().getY(),
                        spirit.blockPosition().getZ()),
                origin);
        BlockCoord supply =
                chests.isEmpty() ? feet : supplyFeet(site, chests.get(0)).orElse(feet);
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

    // Клетка у ближнего сундука вне габарита стройки, ближайшая к середине площадки, на уровне земли: там дух после
    // захода.
    private static Optional<BlockCoord> supplyFeet(SchematicSite site, BlockPos chest) {
        BlockCoord origin = site.origin();
        @Nullable BlockCoord best = null;
        double bestDistance = Double.MAX_VALUE;
        BuildBox box = site.networkBox(0);
        BlockPos centre = new BlockPos((box.minX() + box.maxX()) / 2, origin.y(), (box.minZ() + box.maxZ()) / 2);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos cell = chest.relative(side);
            BlockCoord coord = new BlockCoord(cell.getX(), cell.getY(), cell.getZ());
            // Клетка схемы (крыльцо, ступень) потом застроится — после захода дух стоял бы в стене (дом игрока 3 cw90).
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

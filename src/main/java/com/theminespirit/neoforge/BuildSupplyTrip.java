package com.theminespirit.neoforge;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Заход за материалами посреди стройки по схеме (решение владельца 20.10): дух берёт из сундуков у площадки
 * столько недостающего, сколько влезает в инвентарь, — в порядке схемы (снизу вверх), — ставит всё, что можно
 * взятым, и идёт за следующей порцией. Одинаково в демо, в аудите и в обычной стройке.
 */
final class BuildSupplyTrip {
    // Заходов на одну стройку: дом уровня 4 — ~100 стопок на 36 слотов; больше — стройка не продвигается.
    static final int MAX_TRIPS = 32;
    private static final int SEARCH_SLICES = 8;
    // Площадка демо шире постройки на 5 (сундук стоит на ней); дальше — чужие сундуки соседних строек.
    private static final int SEARCH_MARGIN = 6;

    private BuildSupplyTrip() {}

    // Шаги withdraw следующей порции; пусто — брать нечего (сундуков нет, в них нет нужного или инвентарь полон).
    static List<ScriptCall> plan(SpiritEntity spirit, SchematicSite site) {
        Map<Item, Integer> need = need(spirit, site);
        int freeSlots = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).isEmpty()) {
                freeSlots++;
            }
        }
        if (need.isEmpty() || freeSlots == 0) {
            return List.of();
        }
        List<ScriptCall> steps = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : containers(spirit, site)) {
            if (freeSlots <= 0) {
                break;
            }
            BlockPos other = otherHalf(spirit, pos);
            // Двойной сундук открывается общим содержимым с любой половины: вторую половину не считать дважды.
            if (!seen.add(pos) || (!other.equals(pos) && seen.contains(other))) {
                continue;
            }
            if (!(ContainerAccess.open(spirit.level(), pos) instanceof ContainerAccess.Opened opened)) {
                continue;
            }
            freeSlots = take(opened.container(), pos, need, freeSlots, steps);
        }
        return List.copyOf(steps);
    }

    // Недостающее в мире минус своё, в порядке клеток схемы: сначала то, что ставится раньше.
    private static Map<Item, Integer> need(SpiritEntity spirit, SchematicSite site) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (BlockItems.hasBlockItem(entry.block()) && !site.matches(entry)) {
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

    // Сундуки у площадки: от середины постройки на полразмера и ещё SEARCH_MARGIN.
    private static List<BlockPos> containers(SpiritEntity spirit, SchematicSite site) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int y = Integer.MAX_VALUE;
        for (Schematic.PlacedEntry entry : site.primaries()) {
            minX = Math.min(minX, entry.pos().x());
            maxX = Math.max(maxX, entry.pos().x());
            minZ = Math.min(minZ, entry.pos().z());
            maxZ = Math.max(maxZ, entry.pos().z());
            y = Math.min(y, entry.pos().y());
        }
        if (y == Integer.MAX_VALUE) {
            return List.of();
        }
        BlockPos centre = new BlockPos((minX + maxX) / 2, y, (minZ + maxZ) / 2);
        int radius = (Math.max(maxX - minX, maxZ - minZ) + 1) / 2 + SEARCH_MARGIN;
        BlockLocator.Search search =
                BlockLocator.search(spirit.level(), centre, radius, ContainerAccess.allowedBlocks());
        List<BlockPos> found = new ArrayList<>();
        while (!search.done()) {
            found.addAll(search.step(SEARCH_SLICES));
        }
        BlockPos feet = spirit.blockPosition();
        found.sort(Comparator.comparingDouble(feet::distSqr).thenComparing(Comparator.naturalOrder()));
        return found;
    }

    private static BlockPos otherHalf(SpiritEntity spirit, BlockPos pos) {
        BlockState state = spirit.level().getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return pos;
    }
}

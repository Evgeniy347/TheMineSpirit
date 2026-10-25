package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.planner.AcquireSupplies;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.crafting.StationType;
import com.theminespirit.neoforge.policy.PolicyConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class SpiritSupplies implements AcquireSupplies {
    private static final int SLICES_PER_TICK = 4;
    private static final Set<Block> STATION_BLOCKS =
            Stream.of(StationType.values()).map(StationType::block).collect(Collectors.toUnmodifiableSet());

    private final SpiritEntity spirit;
    private final int radius;

    public SpiritSupplies(SpiritEntity spirit, int radius) {
        this.spirit = Objects.requireNonNull(spirit, "spirit не может быть null: снабжение читает инвентарь духа");
        if (radius < CatalogLimits.CONTAINER_RADIUS_MIN || radius > CatalogLimits.CONTAINER_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска сундуков " + radius + " вне диапазона ["
                    + CatalogLimits.CONTAINER_RADIUS_MIN + ", " + CatalogLimits.CONTAINER_RADIUS_MAX
                    + "]: дальше дух уходит из базовой зоны");
        }
        this.radius = radius;
    }

    @Override
    public int inInventory(String item) {
        Objects.requireNonNull(item, "Предмет не может быть null: по нему считается инвентарь духа");
        IdFilter filter = IdFilter.of(List.of(item));
        return count(spirit.getInventory(), filter) + mainHandCount(filter);
    }

    @Override
    public Map<String, Integer> inventory() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                totals.merge(ItemIds.of(stack.getItem()), stack.getCount(), Integer::sum);
            }
        }
        ItemStack hand = spirit.getMainHandItem();
        if (!hand.isEmpty()) {
            totals.merge(ItemIds.of(hand.getItem()), hand.getCount(), Integer::sum);
        }
        return totals;
    }

    private int mainHandCount(IdFilter filter) {
        ItemStack hand = spirit.getMainHandItem();
        return !hand.isEmpty() && filter.matches(ItemIds.of(hand.getItem())) ? hand.getCount() : 0;
    }

    @Override
    public List<ScriptCall> withdraw(String item, int maxCount) {
        Objects.requireNonNull(item, "Предмет не может быть null: по нему ищутся сундуки");
        if (maxCount <= 0) {
            return List.of();
        }
        IdFilter filter = IdFilter.of(List.of(item));
        BlockPos center = spirit.blockPosition();
        BlockLocator.Search search =
                BlockLocator.search(spirit.level(), center, radius, ContainerAccess.allowedBlocks());
        List<BlockPos> found = new ArrayList<>();
        while (!search.done()) {
            found.addAll(search.step(SLICES_PER_TICK));
        }
        found.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center))
                .thenComparing(Comparator.naturalOrder()));
        List<ScriptCall> steps = new ArrayList<>();
        Set<Container> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        String dimension = spirit.level().dimension().location().toString();
        int left = maxCount;
        for (BlockPos pos : found) {
            if (left <= 0) {
                break;
            }
            BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            if (spirit.protectionGate().checkTravel(coord) instanceof BlockProtection.Refused) {
                continue;
            }
            if (PolicyConfig.zones().contains(dimension, coord)) {
                continue;
            }
            ContainerAccess.Open open = ContainerAccess.open(spirit.level(), pos);
            if (!(open instanceof ContainerAccess.Opened opened)) {
                continue;
            }
            if (!seen.add(opened.container())) {
                continue;
            }
            int available = count(opened.container(), filter);
            if (available <= 0) {
                continue;
            }
            int take = Math.min(left, available);
            steps.add(new ScriptCall(
                    "withdraw",
                    Map.of(
                            "container", pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                            "item", item,
                            "count", take,
                            "radius", radius)));
            left -= take;
        }
        return steps;
    }

    @Override
    public Set<String> nearbyStations() {
        BlockLocator.Search search = BlockLocator.search(
                spirit.level(), spirit.blockPosition(), CatalogLimits.STATION_RADIUS_DEFAULT, STATION_BLOCKS);
        Set<String> found = new LinkedHashSet<>();
        while (!search.done()) {
            for (BlockPos pos : search.step(SLICES_PER_TICK)) {
                StationType.byBlock(spirit.level().getBlockState(pos).getBlock())
                        .map(StationType::id)
                        .ifPresent(found::add);
            }
        }
        return found;
    }

    private static int count(Container container, IdFilter filter) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && filter.matches(ItemIds.of(stack.getItem()))) {
                total += stack.getCount();
            }
        }
        return total;
    }
}

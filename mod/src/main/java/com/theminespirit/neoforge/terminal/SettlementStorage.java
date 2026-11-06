package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.board.BoardReserve;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

public final class SettlementStorage {
    private final List<Container> containers;
    private final Map<String, Integer> reserved;

    public record Kind(ItemStack sample, int count, int free) {
        public Kind {
            sample = sample.copyWithCount(1);
        }
    }

    private SettlementStorage(List<Container> containers, Map<String, Integer> reserved) {
        this.containers = containers;
        this.reserved = reserved;
    }

    public static SettlementStorage open(ServerLevel level, SettlementReach reach) {
        Objects.requireNonNull(level, "Уровень не может быть null: сундуки поселения стоят в мире");
        Objects.requireNonNull(reach, "Граница не может быть null: по ней отбираются сундуки поселения");
        List<BlockCoord> chests =
                new ArrayList<>(SettlementChestStore.get(level).chests().all());
        chests.sort(Comparator.comparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z)
                .thenComparingInt(BlockCoord::y));
        Set<Container> found = new LinkedHashSet<>();
        for (BlockCoord chest : chests) {
            BlockPos pos = new BlockPos(chest.x(), chest.y(), chest.z());
            if (reach.covers(chest.x(), chest.z()) && level.isLoaded(pos)) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (usable(level, pos, entity)) {
                    found.add((Container) entity);
                }
            }
        }
        return new SettlementStorage(
                List.copyOf(found), BoardReserve.items(BoardRegistry.get(level).board()));
    }

    private static boolean usable(ServerLevel level, BlockPos pos, BlockEntity entity) {
        if (!(entity instanceof BaseContainerBlockEntity) || !ContainerAccess.allowed(level.getBlockState(pos))) {
            return false;
        }
        return !(entity instanceof RandomizableContainerBlockEntity random) || random.getLootTable() == null;
    }

    public List<Kind> kinds() {
        List<ItemStack> samples = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                int index = indexOf(samples, stack);
                if (index < 0) {
                    samples.add(stack.copyWithCount(1));
                    counts.add(stack.getCount());
                } else {
                    counts.set(index, counts.get(index) + stack.getCount());
                }
            }
        }
        Map<String, Integer> free = freePlain();
        List<Kind> kinds = new ArrayList<>(samples.size());
        for (int index = 0; index < samples.size(); index++) {
            ItemStack sample = samples.get(index);
            int count = counts.get(index);
            int available = sample.isComponentsPatchEmpty()
                    ? Math.min(count, free.getOrDefault(ItemIds.of(sample.getItem()), 0))
                    : count;
            kinds.add(new Kind(sample, count, available));
        }
        return kinds;
    }

    private static int indexOf(List<ItemStack> samples, ItemStack stack) {
        for (int index = 0; index < samples.size(); index++) {
            if (ItemStack.isSameItemSameComponents(samples.get(index), stack)) {
                return index;
            }
        }
        return -1;
    }

    public Map<String, Integer> freePlain() {
        Map<String, Integer> plain = new HashMap<>();
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && stack.isComponentsPatchEmpty()) {
                    plain.merge(ItemIds.of(stack.getItem()), stack.getCount(), Integer::sum);
                }
            }
        }
        return BoardReserve.free(plain, reserved);
    }

    public int available(ItemStack kind) {
        for (Kind entry : kinds()) {
            if (ItemStack.isSameItemSameComponents(entry.sample(), kind)) {
                return entry.free();
            }
        }
        return 0;
    }

    public ItemStack extract(ItemStack kind, int amount) {
        int wanted = Math.min(amount, Math.min(kind.getMaxStackSize(), available(kind)));
        ItemStack taken = kind.copyWithCount(0);
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && taken.getCount() < wanted; slot++) {
                ItemStack stack = container.getItem(slot);
                if (ItemStack.isSameItemSameComponents(stack, kind)) {
                    int part = Math.min(stack.getCount(), wanted - taken.getCount());
                    container.removeItem(slot, part);
                    taken.grow(part);
                    container.setChanged();
                }
            }
        }
        return taken.isEmpty() ? ItemStack.EMPTY : taken;
    }

    public record Stored(ItemStack rest, Optional<BlockPos> at) {}

    public ItemStack insert(ItemStack incoming) {
        return store(incoming).rest();
    }

    public Stored store(ItemStack incoming) {
        ItemStack rest = incoming.copy();
        Optional<BlockPos> at = Optional.empty();
        for (boolean sameOnly : new boolean[] {true, false}) {
            for (Container container : containers) {
                int before = rest.getCount();
                mergeInto(container, rest, sameOnly);
                if (at.isEmpty() && rest.getCount() < before && container instanceof BlockEntity entity) {
                    at = Optional.of(entity.getBlockPos());
                }
            }
        }
        return new Stored(rest, at);
    }

    private static void mergeInto(Container container, ItemStack rest, boolean sameOnly) {
        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!container.canPlaceItem(slot, rest)) {
                continue;
            }
            int limit = Math.min(container.getMaxStackSize(rest), rest.getMaxStackSize());
            if (sameOnly && ItemStack.isSameItemSameComponents(stack, rest) && stack.getCount() < limit) {
                int part = Math.min(limit - stack.getCount(), rest.getCount());
                stack.grow(part);
                rest.shrink(part);
                container.setChanged();
            } else if (!sameOnly && stack.isEmpty()) {
                container.setItem(slot, rest.split(Math.min(limit, rest.getCount())));
                container.setChanged();
            }
        }
    }
}

package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.WarehouseCategories;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.BlockLocator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

final class AutoContainerScan {
    private static final int SLICES_PER_TICK = 4;

    private final Level level;
    private final BlockLocator.Search search;
    private final StackMapper mapper = new StackMapper();
    private final List<BlockPos> candidates = new ArrayList<>();
    private final List<Container> containers = new ArrayList<>();
    private final List<ContainerSnapshot> snapshots = new ArrayList<>();
    private boolean collected;

    AutoContainerScan(Level level, BlockPos center, int radius) {
        this.level = level;
        this.search = BlockLocator.search(level, center, radius, ContainerAccess.allowedBlocks());
    }

    AutoContainerScan(Level level, List<BlockCoord> known) {
        this.level = level;
        this.search = null;
        for (BlockCoord pos : known) {
            candidates.add(new BlockPos(pos.x(), pos.y(), pos.z()));
        }
    }

    static AutoContainerScan warehouse(Level level, BlockPos origin, boolean yardOnly) {
        if (level instanceof ServerLevel server) {
            BlockCoord at = new BlockCoord(origin.getX(), origin.getY(), origin.getZ());
            return new AutoContainerScan(level, StorageFinder.positions(server, at, yardOnly));
        }
        return new AutoContainerScan(level, List.of());
    }

    boolean tick() {
        if (collected) {
            return true;
        }
        if (search != null) {
            candidates.addAll(search.step(SLICES_PER_TICK));
            if (!search.done()) {
                return false;
            }
        }
        collect();
        collected = true;
        return true;
    }

    private void collect() {
        Set<Container> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos pos : candidates) {
            if (!(ContainerAccess.open(level, pos) instanceof ContainerAccess.Opened opened)) {
                continue;
            }
            if (!seen.add(opened.container())) {
                continue;
            }
            containers.add(opened.container());
            BlockCoord coord = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            List<Stack> slots = mapper.map(opened.container());
            String category = ContainerCategories.of(level, pos, slots, categories());
            snapshots.add(new ContainerSnapshot(coord, slots, category));
        }
    }

    void updateSnapshot(int index) {
        Container container = containers.get(index);
        ContainerSnapshot previous = snapshots.get(index);
        List<Stack> slots = mapper.map(container);
        String category = ContainerCategories.of(
                level,
                BlockPos.containing(
                        previous.pos().x(), previous.pos().y(), previous.pos().z()),
                slots,
                categories());
        snapshots.set(index, new ContainerSnapshot(previous.pos(), slots, category));
    }

    private static WarehouseCategories categories() {
        return DataTables.INSTANCE.warehouseCategories().current();
    }

    StackMapper mapper() {
        return mapper;
    }

    List<Container> containers() {
        return containers;
    }

    List<ContainerSnapshot> snapshots() {
        return snapshots;
    }

    int maxSlicesPerTick() {
        return search == null ? 0 : search.maxSlicesPerTick();
    }

    Level level() {
        return level;
    }
}

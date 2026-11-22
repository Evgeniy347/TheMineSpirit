package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class SettlementChestSync {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SettlementChestSync() {}

    public static void reconcile(ServerLevel level) {
        Optional<ColumnMask> found = ChestSnapshots.area(level);
        if (found.isEmpty()) {
            return;
        }
        ColumnMask area = found.orElseThrow();
        Set<BlockCoord> present = new LinkedHashSet<>(ChestSnapshots.inArea(level, area));
        SettlementChestStore store = SettlementChestStore.get(level);
        int added = track(store, present);
        int removed = forgetGone(level, area, present, store);
        if (added > 0 || removed > 0) {
            LOGGER.info(
                    "[chests] реестр сверен с участком: добавлено {}, снято {}, всего {}",
                    added,
                    removed,
                    store.chests().size());
        }
    }

    private static int track(SettlementChestStore store, Set<BlockCoord> present) {
        int added = 0;
        for (BlockCoord pos : present) {
            if (store.track(new BlockPos(pos.x(), pos.y(), pos.z()))) {
                added++;
            }
        }
        return added;
    }

    private static int forgetGone(
            ServerLevel level, ColumnMask area, Set<BlockCoord> present, SettlementChestStore store) {
        int removed = 0;
        List<BlockCoord> known = store.chests().all();
        for (BlockCoord pos : known) {
            if (!area.contains(pos.x(), pos.z()) || present.contains(pos)) {
                continue;
            }
            BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
            if (level.isLoaded(at) && !ContainerAccess.allowed(level.getBlockState(at)) && store.forget(at)) {
                removed++;
            }
        }
        return removed;
    }
}

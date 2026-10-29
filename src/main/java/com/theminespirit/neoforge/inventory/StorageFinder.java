package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.StoragePoints;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SettlementChestStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class StorageFinder {
    private static final StateChangeLog CHANGES = new StateChangeLog();
    private static final Logger LOGGER = LogUtils.getLogger();

    private StorageFinder() {}

    public static Optional<BlockCoord> depositPoint(ServerLevel level, BlockCoord origin, String item, int count) {
        StoragePoints.Choice choice =
                StoragePoints.deposit(spots(level, origin), origin, new Stack(item, 0, Math.min(count, 64), 64));
        report("deposit:" + item, choice, level);
        return Optional.ofNullable(choice.point());
    }

    public static Optional<BlockCoord> foodPoint(ServerLevel level, BlockCoord origin, String item, int count) {
        StoragePoints.Choice choice = StoragePoints.withdraw(spots(level, origin), origin, item, Math.max(1, count));
        report("food:" + item, choice, level);
        return Optional.ofNullable(choice.point());
    }

    private static void report(String channel, StoragePoints.Choice choice, ServerLevel level) {
        if (!CHANGES.changed(channel, choice.state())) {
            return;
        }
        if (choice.found()) {
            LOGGER.info("[storage] {}", choice.log());
            return;
        }
        LOGGER.info(
                "[storage] {} — следующая попытка на тике {}",
                choice.log(),
                level.getGameTime() + BoardTuning.boardIntervalTicks());
    }

    private static List<StoragePoints.Spot> spots(ServerLevel level, BlockCoord origin) {
        List<BlockCoord> known = SettlementChestStore.get(level).chests().warehouse(origin);
        List<StoragePoints.Spot> spots = new ArrayList<>();
        for (BlockCoord pos : known) {
            BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
            String channel = "container:" + coord(pos);
            if (!level.isLoaded(at)) {
                if (CHANGES.changed(channel, "unloaded")) {
                    LOGGER.info("[storage] контейнер {} не в загруженном чанке — осмотр пропущен", coord(pos));
                }
                continue;
            }
            String type = BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(at).getBlock())
                    .toString();
            ContainerAccess.Open open = ContainerAccess.open(level, at);
            if (open instanceof ContainerAccess.Opened opened) {
                CHANGES.forget(channel);
                spots.add(new StoragePoints.Spot(
                        pos, type, new ContainerSnapshot(pos, new StackMapper().map(opened.container()))));
            } else if (open instanceof ContainerAccess.Refused refused) {
                if (CHANGES.changed(channel, "refused:" + refused.reason())) {
                    LOGGER.info("[storage] контейнер {} ({}) отклонён: {}", coord(pos), type, refused.reason());
                }
            }
        }
        return spots;
    }

    private static String coord(BlockCoord pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }
}

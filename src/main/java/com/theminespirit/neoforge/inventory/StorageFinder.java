package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.building.SettlementChests;
import com.theminespirit.core.inventory.ContainerCell;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.StoragePoints;
import com.theminespirit.core.inventory.UnloadedSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.terminal.CraftTerminalService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class StorageFinder {
    private static final StateChangeLog CHANGES = new StateChangeLog();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int YARD_SLACK = 3;

    private StorageFinder() {}

    public static Optional<BlockCoord> depositPoint(ServerLevel level, BlockCoord origin, String item, int count) {
        return depositPoint(level, origin, item, count, Set.of());
    }

    public static Optional<BlockCoord> depositPoint(
            ServerLevel level, BlockCoord origin, String item, int count, Set<BlockCoord> excluded) {
        StoragePoints.Choice choice = StoragePoints.deposit(
                spots(level, origin), origin, new Stack(item, 0, Math.min(count, 64), 64), excluded);
        report("deposit:" + item, choice, level);
        return Optional.ofNullable(choice.point());
    }

    public static List<BlockCoord> positions(ServerLevel level, BlockCoord origin, boolean yardOnly) {
        List<BlockCoord> known = warehouse(level, origin);
        if (!yardOnly) {
            return known;
        }
        Optional<StageSite> site = SettlementPlotStore.get(level).site();
        if (site.isEmpty()) {
            return List.of();
        }
        StageSite stage = site.get();
        List<BlockCoord> yard = new ArrayList<>();
        for (BlockCoord pos : known) {
            if (stage.yardColumn(pos.x(), pos.z())
                    && Math.abs(pos.y() - stage.anchor().y()) <= YARD_SLACK) {
                yard.add(pos);
            }
        }
        return List.copyOf(yard);
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

    private static List<BlockCoord> warehouse(ServerLevel level, BlockCoord origin) {
        SettlementChests chests = SettlementChestStore.get(level).chests();
        Optional<SettlementReach> reach =
                Level.OVERWORLD.equals(level.dimension()) ? CraftTerminalService.reach(level) : Optional.empty();
        return reach.map(found -> chests.warehouse(origin, found)).orElseGet(() -> chests.warehouse(origin));
    }

    private static List<StoragePoints.Spot> spots(ServerLevel level, BlockCoord origin) {
        List<BlockCoord> known = warehouse(level, origin);
        List<StoragePoints.Spot> spots = new ArrayList<>();
        List<BlockCoord> unloaded = new ArrayList<>();
        for (BlockCoord pos : known) {
            BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
            String channel = "container:" + coord(pos);
            if (!level.isLoaded(at)) {
                unloaded.add(pos);
                continue;
            }
            String type = BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(at).getBlock())
                    .toString();
            ContainerCell cell = ContainerCells.at(level, at);
            ContainerCell.Problem problem = cell.problem();
            if (problem != ContainerCell.Problem.NONE) {
                if (CHANGES.changed(channel, "broken:" + problem + ":" + cell.describe())) {
                    LOGGER.warn(
                            "[storage] расхождение блока и BlockEntity {}: {} — {}",
                            coord(pos),
                            problem.text(),
                            cell.describe());
                }
                continue;
            }
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
        if (!unloaded.isEmpty()) {
            String line = UnloadedSpots.line(unloaded);
            String scope = "unloaded:" + level.dimension().location();
            if (CHANGES.changed(scope, line)) {
                LOGGER.info("[storage] {}", line);
            }
        }
        return spots;
    }

    private static String coord(BlockCoord pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }
}

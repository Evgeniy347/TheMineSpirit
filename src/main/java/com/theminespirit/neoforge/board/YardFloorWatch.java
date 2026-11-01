package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.core.town.YardFloor;
import com.theminespirit.core.town.YardScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class YardFloorWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String YARD = "yard_1";
    private static final int MAX_DEPTH = 12;
    private static final int EVERY = 600;
    private static final String REPAIR = RepairPlan.PREFIX + "stock_chests";

    private YardFloorWatch() {}

    static void tick(ServerLevel level, BoardService service, StageSite site, long now) {
        if (now % EVERY != 0) {
            return;
        }
        if (active(service) || audit(level, site).holes().isEmpty()) {
            return;
        }
        YardFloor.Audit found = audit(level, site);
        List<BlockCoord> holes = found.holes();
        List<PlannedBlock> fill = holes.stream()
                .map(cell -> new PlannedBlock(cell, StageBuilds.SUPPORT))
                .toList();
        TicketTarget target = TicketTarget.at(site.workYard())
                .withWork(new StageWork(site, fill, List.of(), BoardCase.STOCK_CHESTS.name()));
        TaskNode plan = TicketPlans.plan(BoardCase.REPAIR_BUILDING, 1, target);
        BuildGap.Gap gap = BuildGap.trace(plan, "oak", FirstStepsRunner.probe(level), FirstStepsRunner.SCHEMES);
        Map<String, Integer> need =
                StageAudit.shortage(BuildGap.need(plan, gap, FirstStepsRunner.materialSource(level)));
        StageRepair.submit(
                service,
                BoardCase.STOCK_CHESTS,
                target,
                new StageRepair.Gap(
                        found.stations().size() - found.supported(),
                        found.stations().size(),
                        need),
                now);
        LOGGER.info(
                "[layout] двор: под станками {} дыр ({} из {} стоят на опоре), заделка {}",
                holes.size(),
                found.supported(),
                found.stations().size(),
                holes.stream()
                        .limit(5)
                        .map(cell -> cell + " ожидался пол, стоит " + block(level, cell))
                        .toList());
    }

    static TownSnapshot.Progress progress(ServerLevel level, StageSite site) {
        YardFloor.Audit found = audit(level, site);
        return new TownSnapshot.Progress(found.supported(), found.stations().size(), found.damaged());
    }

    private static boolean active(BoardService service) {
        return service.board()
                .statusOf(REPAIR)
                .filter(status -> status == BoardStatus.PENDING
                        || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .isPresent();
    }

    private static YardFloor.Audit audit(ServerLevel level, StageSite site) {
        List<BlockCoord> stations = new ArrayList<>();
        for (Schematic.PlacedEntry entry : entries(site)) {
            String block = entry.block();
            if ((block.endsWith("chest") || block.endsWith("crafting_table") || block.endsWith("furnace"))
                    && block(level, entry.pos()).endsWith(block)) {
                stations.add(entry.pos());
            }
        }
        return YardFloor.of(stations, MAX_DEPTH, cell -> {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        });
    }

    private static List<Schematic.PlacedEntry> entries(StageSite site) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(YARD)
                .map(preset -> preset.schematic().entries(YardScheme.origin(site)))
                .orElse(List.of());
    }

    private static String block(ServerLevel level, BlockCoord cell) {
        return BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                        .getBlock())
                .toString();
    }
}

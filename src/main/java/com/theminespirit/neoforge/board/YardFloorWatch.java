package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.core.town.YardFloor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class YardFloorWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_DEPTH = 12;
    private static final String REPAIR = RepairPlan.PREFIX + "stock_chests";

    private YardFloorWatch() {}

    static boolean handles(String node) {
        return (RepairPlan.PREFIX + BoardCase.STOCK_CHESTS.name().toLowerCase(Locale.ROOT)).equals(node);
    }

    static boolean post(GraphPost post) {
        ServerLevel level = post.level;
        BoardService service = post.service;
        StageSite site = post.scan.stage();
        if (active(service)) {
            return true;
        }
        YardFloor.Audit found = audit(level, site);
        List<BlockCoord> holes = found.holes();
        if (holes.isEmpty()) {
            return true;
        }
        List<PlannedBlock> fill = holes.stream()
                .map(cell -> new PlannedBlock(cell, StageBuilds.SUPPORT))
                .toList();
        TicketTarget target = TicketTarget.at(site.workYard())
                .withWork(new StageWork(site, fill, List.of(), BoardCase.STOCK_CHESTS.name()));
        TaskNode plan = TicketPlans.plan(BoardCase.REPAIR_BUILDING, 1, target);
        BuildGap.Gap gap = BuildGap.trace(plan, "oak", FirstStepsRunner.probe(level), FirstStepsRunner.SCHEMES);
        Map<String, Integer> need =
                StageAudit.shortage(BuildGap.need(plan, gap, FirstStepsRunner.materialSource(level)));
        StageRepair.post(
                post,
                BoardCase.STOCK_CHESTS,
                target,
                new StageRepair.Gap(
                        found.stations().size() - found.supported(),
                        found.stations().size(),
                        need));
        LOGGER.info(
                "[layout] РґРІРѕСЂ: РїРѕРґ СЃС‚Р°РЅРєР°РјРё {} РґС‹СЂ ({} РёР· {} СЃС‚РѕСЏС‚ РЅР° РѕРїРѕСЂРµ), Р·Р°РґРµР»РєР° {} вЂ” СѓР·РµР» repair:stock_chests",
                holes.size(),
                found.supported(),
                found.stations().size(),
                holes.stream()
                        .limit(5)
                        .map(cell -> cell + " РѕР¶РёРґР°Р»СЃСЏ РїРѕР», СЃС‚РѕРёС‚ " + block(level, cell))
                        .toList());
        return true;
    }

    static TownSnapshot.Progress progress(ServerLevel level, StageSite site) {
        if (!loaded(level, site)) {
            return TownSnapshot.Progress.NONE;
        }
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

    static boolean loaded(ServerLevel level, StageSite site) {
        var yard = site.yard();
        int y = site.anchor().y();
        for (int x = yard.x(); x < yard.x() + yard.width(); x++) {
            for (int z = yard.z(); z < yard.z() + yard.depth(); z++) {
                for (int dy = -3; dy <= 1; dy++) {
                    if (!level.isLoaded(new BlockPos(x, y + dy, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static YardFloor.Audit audit(ServerLevel level, StageSite site) {
        List<BlockCoord> stations = new ArrayList<>();
        var yard = site.yard();
        int y = site.anchor().y();
        for (int x = yard.x(); x < yard.x() + yard.width(); x++) {
            for (int z = yard.z(); z < yard.z() + yard.depth(); z++) {
                for (int dy = -3; dy <= 1; dy++) {
                    BlockCoord cell = new BlockCoord(x, y + dy, z);
                    if (station(block(level, cell))) {
                        stations.add(cell);
                    }
                }
            }
        }
        return YardFloor.of(stations, MAX_DEPTH, cell -> {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        });
    }

    private static boolean station(String block) {
        return block.endsWith("chest") || block.endsWith("crafting_table") || block.endsWith("furnace");
    }

    private static String block(ServerLevel level, BlockCoord cell) {
        return BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                        .getBlock())
                .toString();
    }
}

package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicWood;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class BuildingAudit {
    private static final long CACHE_TICKS = 600;
    private static final Map<String, RepairAudit.Report> SCHEMES = new HashMap<>();
    private static final Map<String, BuildingAuditInputs.Audit> GAPS = new HashMap<>();
    private static long cacheBucket = Long.MIN_VALUE;

    private BuildingAudit() {}

    static BuildingAuditInputs.Audit first(ServerLevel level, FirstStepsRunner.StageScan scan, BoardCase step) {
        BuildGap.Gap gap = FirstStepsRunner.gap(level, step, scan.stage(), scan.wood());
        return new BuildingAuditInputs.Audit(gap.total(), gap.done(), blocks(gap), Map.of(), 0, gap.unknown());
    }

    static BuildingAuditInputs.Audit floor(ServerLevel level, FirstStepsRunner.StageScan scan) {
        if (!YardFloorWatch.loaded(level, scan.stage())) {
            return new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1);
        }
        TownSnapshot.Progress progress = YardFloorWatch.progress(level, scan.stage());
        int holes = progress.total() - progress.done();
        return new BuildingAuditInputs.Audit(
                progress.total(),
                progress.done(),
                holes == 0 ? Map.of() : Map.of("minecraft:cobblestone", holes),
                Map.of(),
                0,
                0);
    }

    static BuildingAuditInputs.Audit streets(ServerLevel level, FirstStepsRunner.StageScan scan) {
        bucket(level);
        String key = level.dimension().location() + "|streets|" + scan.stage().anchor() + "|" + scan.wood();
        BuildingAuditInputs.Audit cached = GAPS.get(key);
        if (cached != null) {
            return cached;
        }
        String wood = BuildingWoodChoice.stored(level, BoardCase.BUILD_STREETS, scan.stage(), scan.wood());
        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_STREETS,
                1,
                FirstStepsRunner.targetFor(level, BoardCase.BUILD_STREETS, scan.stage(), wood));
        BuildGap.Gap gap = BuildGap.trace(plan, wood, streetProbe(level), FirstStepsRunner.SCHEMES);
        BuildingAuditInputs.Audit audit =
                new BuildingAuditInputs.Audit(gap.total(), gap.done(), blocks(gap), Map.of(), 0, gap.unknown());
        GAPS.put(key, audit);
        return audit;
    }

    static BuildingAuditInputs.Audit clearing(ServerLevel level, FirstStepsRunner.StageScan scan, long now) {
        StageSite site = scan.stage();
        for (ColumnMask.Cell column : site.level().cells()) {
            if (!level.isLoaded(new BlockPos(column.x(), site.anchor().y(), column.z()))) {
                return new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 1);
            }
        }
        PlotAudit.Audit found = PlotAudit.audit(level, site, now);
        Map<String, Integer> extra = new LinkedHashMap<>();
        for (BlockCoord at : found.extras()) {
            BlockState state = level.getBlockState(new BlockPos(at.x(), at.y(), at.z()));
            if (!vegetation(state)) {
                extra.merge(id(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()), 1, Integer::sum);
            }
        }
        int total = site.level().count();
        Map<String, Integer> missing = found.holes().isEmpty()
                ? Map.of()
                : Map.of("minecraft:dirt", found.holes().size());
        return new BuildingAuditInputs.Audit(
                total, Math.max(0, total - found.holes().size()), missing, extra, 0, 0);
    }

    static BuildingAuditInputs.Audit stageOrchard(ServerLevel level, GraphFacts.Position at, String wood, long now) {
        Optional<String> scheme = DataTables.INSTANCE.stages().current().scheme(BoardCase.BUILD_ORCHARD, 1);
        if (scheme.isEmpty()) {
            return new BuildingAuditInputs.Audit(0, 0, Map.of(), Map.of(), 0, 0);
        }
        return scheme(level, scheme.orElseThrow(), at, wood, now);
    }

    static BuildingAuditInputs.Audit scheme(
            ServerLevel level, String scheme, GraphFacts.Position at, String wood, long now) {
        bucket(level);
        String key = level.dimension().location() + "|" + scheme + "|" + at + "|" + wood;
        RepairAudit.Report report = SCHEMES.get(key);
        if (report == null) {
            Schematic wanted = SchematicWood.swap(SchematicResources.load(scheme), wood);
            report = RepairAudit.audit(
                    wanted,
                    new BlockCoord(at.x(), at.y(), at.z()),
                    BuildRotation.NONE,
                    new AuditWorld(level),
                    RepairData.loadDefault(),
                    new HashMap<>(),
                    now);
            SCHEMES.put(key, report);
        }
        return report(report);
    }

    static BuildingAuditInputs.Audit report(RepairAudit.Report report) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        Map<String, Integer> extra = new LinkedHashMap<>();
        int wrong = 0;
        if (!report.rebuild()) {
            for (RepairAudit.Fix fix : report.fixes()) {
                if (fix.action() == RepairAudit.Action.PLACE) {
                    missing.merge(id(fix.wanted()), 1, Integer::sum);
                    if (!air(fix.found())) {
                        wrong++;
                    }
                } else if (fix.action() != RepairAudit.Action.WARN) {
                    extra.merge(id(fix.found()), 1, Integer::sum);
                }
            }
        }
        return new BuildingAuditInputs.Audit(
                report.total(), report.total() - report.missing(), missing, extra, wrong, report.unknown());
    }

    private static Map<String, Integer> blocks(BuildGap.Gap gap) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (String block : gap.missingCells().values()) {
            if (!air(block)) {
                missing.merge(id(block), 1, Integer::sum);
            }
        }
        return missing;
    }

    private static boolean air(String block) {
        return "air".equals(SchematicBlocks.path(block));
    }

    private static String id(String block) {
        return block.contains(":") ? block : "minecraft:" + block;
    }

    private static void bucket(ServerLevel level) {
        long bucket = level.getGameTime() / CACHE_TICKS;
        if (bucket != cacheBucket) {
            cacheBucket = bucket;
            SCHEMES.clear();
            GAPS.clear();
        }
    }

    private static boolean vegetation(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }

    private static BuildGap.Probe streetProbe(ServerLevel level) {
        BuildGap.Probe base = WorldProbe.of(level);
        return new BuildGap.Probe() {
            @Override
            public String block(BlockCoord at) {
                String block = base.block(at);
                String path = SchematicBlocks.path(block);
                return switch (path) {
                    case "dirt", "grass_block", "dirt_path", "path", "coarse_dirt" -> "minecraft:dirt";
                    default -> block;
                };
            }

            @Override
            public boolean passable(BlockCoord at) {
                return base.passable(at);
            }

            @Override
            public String rawBlock(BlockCoord at) {
                return base.block(at);
            }

            @Override
            public boolean loaded(BlockCoord at) {
                return base.loaded(at);
            }
        };
    }

    private record AuditWorld(ServerLevel level) implements RepairAudit.WorldView {
        @Override
        public String blockAt(BlockCoord cell) {
            return BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                            .getBlock())
                    .toString();
        }

        @Override
        public boolean playerPlaced(BlockCoord cell) {
            return PlacedBlocksData.get(level).isMarked(new BlockPos(cell.x(), cell.y(), cell.z()));
        }

        @Override
        public boolean loaded(BlockCoord cell) {
            return level.isLoaded(new BlockPos(cell.x(), cell.y(), cell.z()));
        }
    }
}

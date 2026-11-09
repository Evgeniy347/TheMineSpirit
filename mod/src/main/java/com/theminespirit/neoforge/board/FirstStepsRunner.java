package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

public final class FirstStepsRunner {
    static final BuildGap.Schemes SCHEMES = new BuildGap.Schemes() {
        @Override
        public Optional<MineLayout> mine(ScriptCall call) {
            return SchemeAudit.mine(call);
        }

        @Override
        public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
            return SchemeAudit.entries(call);
        }
    };
    static final Set<BoardCase> WOODEN_BUILDINGS =
            EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.BUILD_ORCHARD);
    private static final Set<BoardCase> BEFORE_YARD = EnumSet.of(
            BoardCase.CRAFTING_STATION,
            BoardCase.CRAFT_WOODEN_PICKAXE,
            BoardCase.CRAFT_WOODEN_AXE,
            BoardCase.CRAFT_WOODEN_SHOVEL);

    private FirstStepsRunner() {}

    public enum PlanWorld {
        AUDIT,
        JOURNAL
    }

    public record StageScan(
            StageSite stage, String wood, BlockCoord home, SettlementFacts facts, boolean furnaceBlock) {}

    public record StepOrder(BoardCase caseValue, int priority, String cause) {}

    public static Optional<StageScan> tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        return tick(level, service, spirits, stock, now, PlanWorld.AUDIT);
    }

    public static Optional<StageScan> tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            long now,
            PlanWorld world) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return Optional.empty();
        }
        if (spirits.stream().noneMatch(spirit -> spirit.home().isPresent())) {
            return Optional.empty();
        }
        if (plotOf(level, service, spirits, now).isEmpty()) {
            return Optional.empty();
        }
        StageSite stage = SettlementPlotStore.get(level)
                .site(SettlementPlotStore.currentStage(service))
                .orElseThrow(() -> new IllegalStateException(
                        "Участок обмерен, а места этапа 1 нет: план поселения не сохранился в мире"));
        BlockCoord home = HomeStand.of(level, stage, spirits);
        String wood = woodOf(level, home, stock);
        StageWorldFacts.StoneSite site = new StageWorldFacts.StoneSite(home, wood, stage);
        StageWorldFacts.Scan scan = StageWorldFacts.buildFacts(level, site, stock, service, world);
        StageStepOpener.dropOtherWood(level, service, BuildingWoodChoice.allowed(level, wood));
        return Optional.of(new StageScan(stage, wood, home, scan.facts(), scan.furnaceBlock()));
    }

    public static Map<String, Integer> materialsOf(
            ServerLevel level, StockView stock, StageScan scan, BoardCase caseValue) {
        return StageMaterialDesk.materialsOf(level, stock, scan, caseValue);
    }

    public static Map<String, Integer> open(GraphPost post, StepOrder order) {
        return StageStepOpener.open(post, order);
    }

    public static List<String> ticketIds(BoardCase caseValue) {
        return StageReopen.ids(caseValue);
    }

    static Map<String, Integer> need(ServerLevel level, StockView stock, OpenStep step, boolean blocked) {
        return StageMaterialDesk.need(level, stock, step, blocked);
    }

    record OpenStep(BoardCase caseValue, TicketTarget target, Map<String, Integer> need, String wood) {}

    static boolean reopenDone(ServerLevel level, BoardService service, OpenStep step, int parts) {
        return StageStepOpener.reopenDone(level, service, step, parts);
    }

    static int partsOf(BoardCase caseValue) {
        return StageReopen.ids(caseValue).size();
    }

    static String partId(BoardCase caseValue, int part) {
        return StageReopen.partId(caseValue, part);
    }

    static void expand(MaterialSource source, String item, int count, Map<String, Integer> raw, int depth) {
        StageMaterialDesk.expand(source, item, count, raw, depth);
    }

    private static String woodOf(ServerLevel level, BlockCoord home, StockView stock) {
        return SettlementWood.of(level, home, stock.available());
    }

    public static TicketTarget targetFor(ServerLevel level, BoardCase caseValue, StageSite stage, String wood) {
        BlockCoord point = BEFORE_YARD.contains(caseValue) ? Stations.at(level, caseValue, stage) : stage.workYard();
        TicketTarget target = TicketTarget.at(point).withWork(StageBuilds.of(level, caseValue, stage, 0));
        if (WOODEN_BUILDINGS.contains(caseValue) && !WoodSpecies.OAK.equals(wood)) {
            target = target.withWood(wood);
        }
        return ToolSet.isTool(caseValue) ? target.withToolCount(1) : target;
    }

    static Map<String, Integer> materials(MaterialSource source, BoardCase caseValue, TicketTarget target) {
        return StageMaterialDesk.materials(source, caseValue, target);
    }

    static MaterialSource materialSource(ServerLevel level) {
        return StageMaterialDesk.materialSource(level);
    }

    public static void minusPlaced(ServerLevel level, TaskNode plan, String wood, Map<String, Integer> need) {
        StageMaterialDesk.minusPlaced(level, plan, wood, need);
    }

    public static TownSnapshot.Progress progress(
            ServerLevel level, BoardService service, BoardCase step, StageSite stage, String wood) {
        Placed placed = audit(level, step, stage, wood);
        boolean done =
                StageWorldFacts.journalSteps(service, false, level, stage).contains(step);
        return new TownSnapshot.Progress(
                placed.done(), placed.total(), placed.confident() && done && placed.done() < placed.total());
    }

    static Placed audit(ServerLevel level, BoardCase step, StageSite stage, String fallback) {
        return audit(level, step, 1, stage, fallback);
    }

    static Placed audit(ServerLevel level, BoardCase step, int buildingLevel, StageSite stage, String fallback) {
        return BuiltAuditCache.of(level, step, buildingLevel, stage, fallback);
    }

    static BuildGap.Gap gap(ServerLevel level, BoardCase step, StageSite stage, String fallback) {
        return gap(level, step, 1, stage, fallback);
    }

    static BuildGap.Gap gap(ServerLevel level, BoardCase step, int buildingLevel, StageSite stage, String fallback) {
        String wood = BuildingWoodChoice.stored(level, step, stage, fallback);
        return BuildGap.trace(
                TicketPlans.plan(step, buildingLevel, targetFor(level, step, buildingLevel, stage, wood)),
                wood,
                probe(level),
                SCHEMES);
    }

    private static TicketTarget targetFor(
            ServerLevel level, BoardCase step, int buildingLevel, StageSite stage, String wood) {
        if (buildingLevel > 1 && step == BoardCase.BUILD_MINE) {
            Optional<BlockCoord> bottom = MineDescentBottom.standOf(stage.mineEntrance());
            if (bottom.isEmpty()) {
                throw new IllegalStateException(
                        "Низ спуска шахты не считается: этап уровня " + buildingLevel + " не сверить с миром");
            }
            return TicketTarget.at(bottom.orElseThrow());
        }
        return targetFor(level, step, stage, wood);
    }

    record Placed(int done, int total, int unknown) {
        boolean confident() {
            return unknown == 0;
        }
    }

    static Placed placedBlocks(ServerLevel level, TaskNode plan, String wood) {
        BuildGap.Gap gap = BuildGap.trace(plan, wood, probe(level), SCHEMES);
        return new Placed(gap.done(), gap.total(), gap.unknown());
    }

    static BuildGap.Probe probe(ServerLevel level) {
        return WorldProbe.of(level);
    }

    public static Optional<SettlementPlot> plotOf(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        return StagePlotSurvey.plotOf(level, service, spirits, now);
    }
}

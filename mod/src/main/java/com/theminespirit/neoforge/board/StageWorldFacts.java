package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.GroundsPlan;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.StageSite;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;

final class StageWorldFacts {
    private static final int SCAN_RADIUS = 16;
    private static final int LAYOUT_SLACK_DIVISOR = 50;
    private static final String FIRST_PREFIX = "first:";
    private static final Set<BoardCase> EXACT_STEPS =
            EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.LIGHTING, BoardCase.STOCK_CHESTS);

    private StageWorldFacts() {}

    record Scan(SettlementFacts facts, boolean furnaceBlock) {}

    record StoneSite(BlockCoord home, String wood, StageSite stage) {}

    private static List<BlockPos> factOrigins(BlockCoord home, StageSite stage) {
        return Stream.of(home, stage.anchor(), stage.workYard())
                .map(point -> new BlockPos(point.x(), point.y(), point.z()))
                .toList();
    }

    static Scan buildFacts(
            ServerLevel level,
            StoneSite site,
            StockView stock,
            BoardService service,
            FirstStepsRunner.PlanWorld world) {
        StageSite stage = site.stage();
        BlockCoord home = site.home();
        boolean hasHome = true;
        boolean groundsCleared = true;
        int chests = 0;
        boolean craftingStation = false;
        boolean smithingTable = false;
        boolean anvil = false;
        boolean furnace = false;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : factOrigins(home, stage)) {
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                        continue;
                    }
                    BlockPos pos = start.offset(dx, 0, dz);
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos check = pos.offset(0, dy, 0);
                        if (!level.isLoaded(check) || !seen.add(check)) {
                            continue;
                        }
                        var state = level.getBlockState(check);
                        boolean onPad = stage.zone().contains(check.getX(), check.getZ());
                        if (onPad && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
                            groundsCleared = false;
                        }
                        if (state.is(Blocks.CHEST)) {
                            chests++;
                        }
                        if (state.is(Blocks.CRAFTING_TABLE)) {
                            craftingStation = true;
                        }
                        if (state.is(Blocks.FURNACE)) {
                            furnace = true;
                        }
                        if (state.is(Blocks.SMITHING_TABLE)) {
                            smithingTable = true;
                        }
                        if (state.getBlock() instanceof AnvilBlock) {
                            anvil = true;
                        }
                    }
                }
            }
        }
        Set<String> tools = toolStock(stock);
        Set<BoardCase> done = journalSteps(service, false, level, stage);
        Set<BoardCase> blocked = journalSteps(service, true, level, stage);
        boolean journal = world == FirstStepsRunner.PlanWorld.JOURNAL;
        boolean plotLevel =
                journal || PlotLeveling.isLevel(stage.anchor(), stage.zone(), SettlementGround.plotFeet(level, stage));
        boolean mineEntrance = built(level, done, BoardCase.BUILD_MINE, site, journal);
        boolean lighting = built(level, done, BoardCase.LIGHTING, site, journal);
        boolean fence = built(level, done, BoardCase.BUILD_FENCE, site, journal);
        SettlementFacts facts = new SettlementFacts(
                hasHome,
                groundsCleared,
                plotLevel,
                chests,
                built(level, done, BoardCase.STOCK_CHESTS, site, journal),
                craftingStation,
                tools,
                mineEntrance,
                built(level, done, BoardCase.CRAFT_FURNACE, site, journal),
                fence,
                lighting,
                built(level, done, BoardCase.BUILD_HOUSE, site, journal),
                built(level, done, BoardCase.BUILD_ORCHARD, site, journal),
                built(level, done, BoardCase.BUILD_STREETS, site, journal),
                smithingTable,
                anvil,
                done,
                blocked);
        return new Scan(facts, furnace);
    }

    private static boolean built(
            ServerLevel level, Set<BoardCase> done, BoardCase step, StoneSite site, boolean journal) {
        if (journal) {
            return done.contains(step);
        }
        FirstStepsRunner.Placed placed = FirstStepsRunner.audit(level, step, site.stage(), site.wood());
        if (!placed.confident() || placed.total() == 0) {
            return done.contains(step);
        }
        if (EXACT_STEPS.contains(step)) {
            return placed.done() == placed.total();
        }
        return (placed.total() - placed.done()) * LAYOUT_SLACK_DIVISOR <= placed.total();
    }

    static Set<BoardCase> journalSteps(BoardService service, boolean blocked, ServerLevel level, StageSite stage) {
        Set<BoardCase> steps = EnumSet.noneOf(BoardCase.class);
        for (BoardCase step : StageReopen.STEPS) {
            String id = FIRST_PREFIX + step.name().toLowerCase(Locale.ROOT);
            boolean match = blocked
                    ? service.board().statusOf(id).orElse(null) == BoardStatus.BLOCKED
                    : service.board().ledger().has(step, 1) && partsDone(service, step, level, stage);
            if (match) {
                steps.add(step);
            }
        }
        return steps;
    }

    static boolean partsDone(BoardService service, BoardCase step, ServerLevel level, StageSite stage) {
        if (step == BoardCase.CLEAR_GROUNDS) {
            for (int part = 2; part <= GroundsPlan.CLEAR_PARTS; part++) {
                if (active(service, FirstStepsRunner.partId(step, part))) {
                    return false;
                }
            }
            return true;
        }
        if (step != BoardCase.BUILD_FENCE) {
            return true;
        }
        return FenceParts.done(service, FenceParts.of(level, stage, FirstStepsRunner.partsOf(step)));
    }

    static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static Set<String> toolStock(StockView stock) {
        Set<String> tools = new HashSet<>();
        for (Map.Entry<String, Integer> entry : stock.available().entrySet()) {
            if (entry.getValue() > 0) {
                tools.add(entry.getKey());
            }
        }
        return tools;
    }
}

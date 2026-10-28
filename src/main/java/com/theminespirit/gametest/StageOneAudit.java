package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import com.theminespirit.neoforge.board.GroundProbe;
import com.theminespirit.neoforge.board.SchemeAudit;
import com.theminespirit.neoforge.board.StrayBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

final class StageOneAudit {
    private static final int SHOWN = 6;
    private static final List<BoardCase> STEPS =
            List.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE, BoardCase.LIGHTING, BoardCase.STOCK_CHESTS);

    private StageOneAudit() {}

    static List<String> problems(ServerLevel level, StageSite site, ColumnMask region, String wood) {
        List<String> problems = new ArrayList<>();
        level(level, site, problems);
        for (BoardCase step : STEPS) {
            building(level, step, site, wood, problems);
        }
        ring(level, site, problems);
        mine(level, site, problems);
        List<String> stray = StrayBlocks.find(level, site, region).stream()
                .map(cell -> cell + " "
                        + BuiltInRegistries.BLOCK
                                .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                                        .getBlock())
                                .getPath())
                .toList();
        report("постройки духов вне плана — их снос не сделан", stray, problems);
        return problems;
    }

    private static void level(ServerLevel level, StageSite site, List<String> problems) {
        List<String> extra = new ArrayList<>();
        int y = site.anchor().y();
        IntBinaryOperator tops = (x, z) -> GroundProbe.top(level, x, z, y + 1);
        for (PlotLeveling.Box box : SiteLevels.extra(site, tops, cell -> site.ringPost(cell.x(), cell.z()))) {
            extra.add(box + " лишнее выше y=" + y);
        }
        report("площадки этапа не расчищены выше y=" + y, extra, problems);
    }

    private static void ring(ServerLevel level, StageSite site, List<String> problems) {
        int fenceY = SiteLevels.fenceY(site);
        List<String> steps = new ArrayList<>();
        for (ColumnMask.Cell post : site.ring().posts()) {
            BlockState state = level.getBlockState(new BlockPos(post.x(), fenceY, post.z()));
            if (!state.is(BlockTags.FENCES) && !state.is(BlockTags.FENCE_GATES)) {
                steps.add("(" + post.x() + ", " + post.z() + ") на y=" + fenceY + " нет столба: "
                        + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            }
        }
        report("ограда не на одном уровне y=" + fenceY, steps, problems);
    }

    private static void building(
            ServerLevel level, BoardCase step, StageSite site, String wood, List<String> problems) {
        TicketTarget target = FirstStepsRunner.targetFor(level, step, site, wood);
        List<String> missing = new ArrayList<>();
        int[] total = {0};
        collect(level, TicketPlans.plan(step, 1, target), wood, missing, total);
        if (total[0] == 0) {
            problems.add(step + ": в плане нет ни одного блока — сверять нечего");
        }
        report(step + " не по плану: нет " + missing.size() + " из " + total[0], missing, problems);
    }

    private static void collect(ServerLevel level, TaskNode plan, String wood, List<String> missing, int[] total) {
        if (plan instanceof FallbackNode fallback) {
            collect(level, fallback.steps().get(0), wood, missing, total);
            return;
        }
        if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(node -> collect(level, node, wood, missing, total));
            return;
        }
        if (!(plan instanceof ScriptCall call)
                || !(call.params().get("x") instanceof Integer x)
                || !(call.params().get("y") instanceof Integer y)
                || !(call.params().get("z") instanceof Integer z)) {
            return;
        }
        if ("place_block".equals(call.script()) && call.params().get("block") instanceof String block) {
            expect(level, new BlockPos(x, y, z), WoodSpecies.swap(block, wood), missing, total);
        }
        SchemeAudit.entries(call)
                .ifPresent(entries -> entries.forEach(entry -> expect(
                        level,
                        new BlockPos(
                                entry.pos().x(), entry.pos().y(), entry.pos().z()),
                        WoodSpecies.swap(entry.block(), wood),
                        missing,
                        total)));
    }

    private static void expect(ServerLevel level, BlockPos pos, String block, List<String> missing, int[] total) {
        total[0]++;
        String want = bare(block);
        String actual = bare(BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock())
                .toString());
        boolean torch = "torch".equals(want) && "wall_torch".equals(actual);
        boolean grown = want.endsWith("_sapling") && actual.equals(want.replace("_sapling", "_log"));
        if (!actual.equals(want) && !torch && !grown && !WoodSpecies.sameRole(actual, want)) {
            missing.add(pos.toShortString() + " " + want + "≠" + actual);
        }
    }

    private static void mine(ServerLevel level, StageSite site, List<String> problems) {
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(TicketPlans.MINE_DESCENT_SCHEME);
        if (preset.isEmpty()) {
            problems.add("схема спуска " + TicketPlans.MINE_DESCENT_SCHEME + " не загружена");
            return;
        }
        MineLayout layout = MineLayout.place(
                preset.get().schematic(),
                preset.get().entry(),
                site.mineEntrance(),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE);
        List<String> wrong = new ArrayList<>();
        for (BlockCoord cell : layout.air()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                wrong.add(pos.toShortString() + " проход завален");
            }
        }
        for (BlockCoord cell : layout.floors()) {
            BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (!SchematicBlocks.isFloorClass(id)) {
                wrong.add(cell + " пол/стенка " + id);
            }
        }
        report("спуск в шахту не по схеме", wrong, problems);
    }

    private static void report(String what, List<String> cells, List<String> problems) {
        if (!cells.isEmpty()) {
            problems.add(what + " (" + cells.size() + "): " + cells.subList(0, Math.min(SHOWN, cells.size())));
        }
    }

    private static String bare(String id) {
        int state = id.indexOf('[');
        String block = state < 0 ? id : id.substring(0, state);
        return block.startsWith("minecraft:") ? block.substring("minecraft:".length()) : block;
    }
}

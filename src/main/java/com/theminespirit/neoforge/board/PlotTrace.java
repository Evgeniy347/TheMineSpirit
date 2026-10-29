package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.GapLog;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class PlotTrace {
    private static final Logger LOGGER = LogUtils.getLogger();

    record Trace(Map<String, Integer> required, Map<String, Integer> placed, List<String> missing) {}

    private PlotTrace() {}

    static Trace trace(ServerLevel level, TaskNode plan, String wood) {
        Map<String, Integer> required = new TreeMap<>();
        Map<String, Integer> placed = new TreeMap<>();
        List<String> missing = new ArrayList<>();
        walk(level, plan, wood, required, placed, missing);
        return new Trace(Map.copyOf(required), Map.copyOf(placed), List.copyOf(missing));
    }

    static void reportGap(
            ServerLevel level,
            BoardCase boardCase,
            TicketTarget target,
            String wood,
            int missing,
            int total,
            boolean rebuild) {
        Trace trace = trace(level, TicketPlans.plan(boardCase, 1, target), wood);
        if (!GapLog.changed(boardCase, trace.missing())) {
            return;
        }
        LOGGER.info(
                "[layout] {} сделан по журналу, но {} {} из {} блоков: {}; недостающие {}",
                boardCase,
                rebuild ? "нет" : "в мире нет",
                missing,
                total,
                rebuild ? "стройка заново" : "восстановление",
                trace.missing());
    }

    private static void walk(
            ServerLevel level,
            TaskNode node,
            String wood,
            Map<String, Integer> required,
            Map<String, Integer> placed,
            List<String> missing) {
        switch (node) {
            case SequenceNode sequence ->
                sequence.steps().forEach(step -> walk(level, step, wood, required, placed, missing));
            case FallbackNode fallback -> walk(level, fallback.steps().get(0), wood, required, placed, missing);
            case RepeatNode repeat -> walk(level, repeat.step(), wood, required, placed, missing);
            case ParallelNode parallel ->
                parallel.steps().forEach(step -> walk(level, step, wood, required, placed, missing));
            case ConditionNode condition -> {
                walk(level, condition.thenStep(), wood, required, placed, missing);
                if (condition.elseStep() != null) {
                    walk(level, condition.elseStep(), wood, required, placed, missing);
                }
            }
            case ScriptCall call
            when SchemeAudit.entries(call).isPresent() -> {
                for (Schematic.PlacedEntry entry : SchemeAudit.entries(call).orElseThrow()) {
                    item(level, entry.pos(), entry.block(), wood, required, placed, missing);
                }
            }
            case ScriptCall call
            when "place_block".equals(call.script())
                    && call.params().get("block") instanceof String block
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z ->
                item(level, new BlockCoord(x, y, z), block, wood, required, placed, missing);
            default -> {}
        }
    }

    private static void item(
            ServerLevel level,
            BlockCoord at,
            String block,
            String wood,
            Map<String, Integer> required,
            Map<String, Integer> placed,
            List<String> missing) {
        String item = FirstStepsRunner.bare(WoodSpecies.swap(SchematicBlocks.itemOf(block), wood));
        required.merge(item, 1, Integer::sum);
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        if (FirstStepsRunner.placed(level.getBlockState(pos), item)) {
            placed.merge(item, 1, Integer::sum);
        } else {
            missing.add(at + " " + item);
        }
    }
}

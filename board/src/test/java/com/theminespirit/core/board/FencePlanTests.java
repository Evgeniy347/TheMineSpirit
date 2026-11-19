package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FencePlanTests {
    @Test
    void repairFence_CellBatch_FillsEachCellOwnBlock() {
        BlockCoord first = new BlockCoord(-2, 71, 4);
        BlockCoord second = new BlockCoord(5, 70, -3);
        TicketTarget target = TicketTarget.at(first)
                .withFenceCells(List.of(first, second), List.of("minecraft:cobblestone_wall", "minecraft:oak_fence"));

        TaskNode plan = TicketPlans.plan(BoardCase.REPAIR_FENCE, target);

        SequenceNode sequence = (SequenceNode) plan;
        assertEquals(
                3,
                sequence.steps().size(),
                "План ограды — шаг к первой клетке и по заливке на каждую клетку: одной областью проломы "
                        + "разной формы не закрыть");
        assertEquals(
                new ScriptCall("goto", Map.of("x", -2, "y", 71, "z", 4)),
                sequence.steps().get(0),
                "Дух стартует у первой клетки: порядок клеток задаёт реестр проломов");
        assertEquals(
                fillCell(first, "minecraft:cobblestone_wall"),
                fillOf(sequence.steps().get(1)),
                "Первая клетка чинится своим блоком: чужая заливка оставила бы пролом");
        assertEquals(
                fillCell(second, "minecraft:oak_fence"),
                fillOf(sequence.steps().get(2)),
                "Каждая следующая клетка чинится своим блоком: наборы блоков у проломов разные");
    }

    private static ScriptCall fillOf(TaskNode step) {
        assertEquals(
                FallbackNode.class,
                step.getClass(),
                "Клетка чинится с откатом: один недостижимый блок не обрывает починку остальных");
        return (ScriptCall) ((FallbackNode) step).steps().get(0);
    }

    private static ScriptCall fillCell(BlockCoord cell, String block) {
        return new ScriptCall(
                "fill",
                Map.of(
                        "block", block, "x1", cell.x(), "y1", cell.y(), "z1", cell.z(), "x2", cell.x(), "y2", cell.y(),
                        "z2", cell.z()));
    }
}

package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphNeedsTests {
    private static final BlockCoord SITE = new BlockCoord(10, 64, 20);
    private static final String HOLDER = "00000000-0000-0000-0000-000000000001";

    @Test
    void supplyGather_OwnItemOnly_NeverBlocksOnItself() {
        BoardTicket ticket = ticket("supply_gather:jungle_log", BoardCase.GATHER_MATERIALS, List.of());

        GraphBlock.Need need = GraphNeeds.of(
                new Board(() -> 0L),
                ticket,
                TicketTarget.at(SITE).withSupply("jungle_log", 4),
                HOLDER,
                List.of(),
                new StockView(Map.of()),
                0);

        assertNull(
                need,
                "Добыча jungle_log не может ждать сама себя: Item(jungle_log) запирал ключ навсегда "
                        + "(живой тупик 10.11: WAITING ждут jungle_log, а supply_gather:jungle_log BLOCKED)");
    }

    @Test
    void supplyGather_WithAxe_NeedsToolNotDepositPoint() {
        BoardTicket ticket = ticket("supply_gather:jungle_log", BoardCase.GATHER_MATERIALS, List.of("tool=axe"));

        GraphBlock.Need need = GraphNeeds.of(
                new Board(() -> 0L),
                ticket,
                TicketTarget.at(SITE).withSupply("jungle_log", 4),
                HOLDER,
                List.of(),
                new StockView(Map.of()),
                0);

        GraphBlock.Tool tool = assertInstanceOf(GraphBlock.Tool.class, need, "Нет топора — нужда в топоре");
        assertEquals("axe", tool.kind(), "Нужда называет инструмент");
        assertEquals(HOLDER, tool.spirit(), "Нужда адресована держателю задания");
    }

    @Test
    void graphGather_Failure_NeedsPathAtWorkSite() {
        BoardTicket ticket = ticket("graph:chop_jungle_log", BoardCase.GATHER_MATERIALS, List.of());

        GraphBlock.Need need = GraphNeeds.of(
                new Board(() -> 0L), ticket, TicketTarget.at(SITE), HOLDER, List.of(), new StockView(Map.of()), 0);

        assertEquals(
                new GraphBlock.Path("10,64,20"),
                need,
                "У графовой добычи цель — место работы: провал помечает именно его недостижимым");
    }

    @Test
    void graphMake_EmptyStock_NeedsProducedItem() {
        BoardTicket ticket = ticket("graph:make:stick", BoardCase.GATHER_MATERIALS, List.of());

        GraphBlock.Need need = GraphNeeds.of(
                new Board(() -> 0L),
                ticket,
                TicketTarget.at(SITE).withSupply("stick", 4),
                HOLDER,
                List.of(),
                new StockView(Map.of()),
                0);

        assertEquals(
                new GraphBlock.Item("stick", 4), need, "Неудачный крафт ждёт свой предмет: граф поднимет его цепочку");
    }

    @Test
    void materialNeed_NestedSchematic_NeedsMissingBlocks() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("goto", Map.of("x", 10, "y", 64, "z", 20)),
                new ScriptCall("build_schematic", Map.of("file", "fence", "x", 10, "y", 64, "z", 20))));

        assertEquals(
                new GraphBlock.Item("oak_fence", 32),
                GraphNeeds.materialNeed(BoardCase.REPAIR_BUILDING, plan, SOURCE, new StockView(Map.of())),
                "Схема внутри SequenceNode обязана дать нужду в своих блоках: плоский place_block видел только "
                        + "готовую клетку и отправлял граф к «стоянке за преградой»");
    }

    @Test
    void materialNeed_PlannedCellsAlreadyFilled_NoNeed() {
        TaskNode plan = new SequenceNode(List.of(
                new ScriptCall("goto", Map.of("x", 10, "y", 64, "z", 20)),
                new ScriptCall("build_schematic", Map.of("file", "fence", "x", 10, "y", 64, "z", 20))));

        assertNull(
                GraphNeeds.materialNeed(
                        BoardCase.REPAIR_BUILDING, plan, SOURCE, new StockView(Map.of("oak_fence", 32))),
                "Полный запас закрывает вложенный план: лишняя нужда заперла бы задание навсегда");
    }

    private static final MaterialSource SOURCE = new MaterialSource() {
        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public Map<String, Integer> schematicBlocks(String file) {
            return "fence".equals(file) ? Map.of("minecraft:oak_fence", 32) : Map.of();
        }
    };

    private static BoardTicket ticket(String id, BoardCase boardCase, List<String> modifiers) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), 50, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of(), modifiers);
    }
}

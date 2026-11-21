package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FenceRepairsTests {
    private static final BlockCoord A = new BlockCoord(5, 70, -3);
    private static final BlockCoord B = new BlockCoord(5, 70, -1);
    private static final BlockCoord C = new BlockCoord(-2, 71, 4);

    @Test
    void of_UnsortedBreaks_OrdersCellsAndBlocksTogether() {
        Map<BlockCoord, String> breaks = new LinkedHashMap<>();
        breaks.put(C, "minecraft:cobblestone_wall");
        breaks.put(A, "minecraft:oak_fence");
        breaks.put(B, "minecraft:oak_fence");

        FenceRepairs.Batch batch = FenceRepairs.of(breaks);

        assertEquals(
                List.of(C, A, B),
                batch.cells(),
                "Клетки ограды идут по x, y, z: порядок общий для всех духов"
                        + " Иначе тест of_UnsortedBreaks_OrdersCellsAndBlocksTogether перестанет ловить эту поломку. При смене of_UnsortedBreaks_OrdersCellsAndBlocksTogether проверь это утверждение.");
        assertEquals(
                List.of("minecraft:cobblestone_wall", "minecraft:oak_fence", "minecraft:oak_fence"),
                batch.blocks(),
                "Блок клетки обязан переезжать вместе с ней: иначе дух починит пролом чужим блоком"
                        + " При смене of_UnsortedBreaks_OrdersCellsAndBlocksTogether проверь это утверждение.");
        assertEquals(
                C,
                batch.first(),
                "Дух идёт к первой клетке: без неё план стартует в пустоту"
                        + " Иначе тест of_UnsortedBreaks_OrdersCellsAndBlocksTogether перестанет ловить эту поломку. При смене of_UnsortedBreaks_OrdersCellsAndBlocksTogether проверь это утверждение.");
    }

    @Test
    void of_EmptyBreaks_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FenceRepairs.of(Map.of()),
                "Пустой реестр проломов не ставит задание: доска и так не должна получать пустую работу"
                        + " Иначе тест of_EmptyBreaks_Rejected перестанет ловить эту поломку. При смене of_EmptyBreaks_Rejected проверь это утверждение.");
    }

    @Test
    void scale_PerCellNeed_MultipliesByCells() {
        assertEquals(
                Map.of("minecraft:oak_log", 12),
                FenceRepairs.scale(Map.of("minecraft:oak_log", 4), 3),
                "Расход на клетку масштабируется числом клеток: иначе гейт ресурсов пропустит недобор"
                        + " При смене scale_PerCellNeed_MultipliesByCells проверь это утверждение.");
    }

    @Test
    void coveredBy_SameCells_True() {
        TicketTarget target =
                TicketTarget.at(A).withFenceCells(List.of(A, B), List.of("minecraft:oak_fence", "minecraft:oak_fence"));

        assertTrue(
                FenceRepairs.coveredBy(target, Map.of(A, "minecraft:oak_fence", B, "minecraft:oak_fence")),
                "Клетки из цели задания уже учтены: новый тикет на них ставить незачем"
                        + " Иначе тест coveredBy_SameCells_True перестанет ловить эту поломку. При смене coveredBy_SameCells_True проверь это утверждение.");
    }

    @Test
    void coveredBy_NewCell_False() {
        TicketTarget target = TicketTarget.at(A).withFenceCells(List.of(A), List.of("minecraft:oak_fence"));

        assertFalse(
                FenceRepairs.coveredBy(target, Map.of(A, "minecraft:oak_fence", C, "minecraft:oak_fence")),
                "Новый пролом вне цели обязан переоткрывать задание: иначе он останется незамеченным"
                        + " При смене coveredBy_NewCell_False проверь это утверждение.");
    }

    @Test
    void coveredBy_OtherBlock_False() {
        TicketTarget target = TicketTarget.at(A).withFenceCells(List.of(A), List.of("minecraft:oak_fence"));

        assertFalse(
                FenceRepairs.coveredBy(target, Map.of(A, "minecraft:cobblestone_wall")),
                "Смена блока клетки — новая работа: цель с другим блоком не покрывает пролом"
                        + " Иначе тест coveredBy_OtherBlock_False перестанет ловить эту поломку. При смене coveredBy_OtherBlock_False проверь это утверждение.");
    }

    @Test
    void coveredBy_LegacyTarget_False() {
        assertFalse(
                FenceRepairs.coveredBy(TicketTarget.at(A), Map.of(A, "minecraft:oak_fence")),
                "Цель без клеток не покрывает проломы: ограду ставят только через агрегат"
                        + " Иначе тест coveredBy_LegacyTarget_False перестанет ловить эту поломку. При смене coveredBy_LegacyTarget_False проверь это утверждение.");
    }
}

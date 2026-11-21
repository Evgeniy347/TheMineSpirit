package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WarehouseApprovalTests {
    @Test
    void apply_NoOrderNoBuilding_LeavesWarehouseAbsent() {
        Map<String, GraphState.Built> buildings = new LinkedHashMap<>();

        WarehouseApproval.apply(buildings, 0, null);

        assertEquals(
                GraphState.Built.ABSENT,
                buildings.get("warehouse"),
                "Без решения стратега склада нет"
                        + " Иначе тест apply_NoOrderNoBuilding_LeavesWarehouseAbsent перестанет ловить эту поломку. При смене apply_NoOrderNoBuilding_LeavesWarehouseAbsent проверь это утверждение.");
        assertEquals(
                GraphState.Built.ABSENT,
                buildings.get("warehouse_2"),
                "Второй уровень без решения не планируется"
                        + " Иначе тест apply_NoOrderNoBuilding_LeavesWarehouseAbsent перестанет ловить эту поломку. При смене apply_NoOrderNoBuilding_LeavesWarehouseAbsent проверь это утверждение.");
    }

    @Test
    void apply_OrderTwoWithoutBuild_PlansFirstLevelOnly() {
        Map<String, GraphState.Built> buildings = new LinkedHashMap<>();

        WarehouseApproval.apply(buildings, 0, order(2));

        assertEquals(
                GraphState.Built.PLANNED,
                buildings.get("warehouse"),
                "Стратег решил строить склад: граф получает задание на первый уровень"
                        + " Иначе тест apply_OrderTwoWithoutBuild_PlansFirstLevelOnly перестанет ловить эту поломку. При смене apply_OrderTwoWithoutBuild_PlansFirstLevelOnly проверь это утверждение.");
        assertEquals(
                GraphState.Built.ABSENT,
                buildings.get("warehouse_2"),
                "Второй уровень не ставится одновременно с первым: сначала стоит первый"
                        + " Иначе тест apply_OrderTwoWithoutBuild_PlansFirstLevelOnly перестанет ловить эту поломку. При смене apply_OrderTwoWithoutBuild_PlansFirstLevelOnly проверь это утверждение.");
    }

    @Test
    void apply_OrderTwoAfterFirst_PlansUpgrade() {
        Map<String, GraphState.Built> buildings = new LinkedHashMap<>();

        WarehouseApproval.apply(buildings, 1, order(2));

        assertEquals(
                GraphState.Built.BUILT,
                buildings.get("warehouse"),
                "Первый уровень уже стоит"
                        + " Иначе тест apply_OrderTwoAfterFirst_PlansUpgrade перестанет ловить эту поломку. При смене apply_OrderTwoAfterFirst_PlansUpgrade проверь это утверждение.");
        assertEquals(
                GraphState.Built.PLANNED,
                buildings.get("warehouse_2"),
                "Стратег решил второй уровень — граф планирует"
                        + " Иначе тест apply_OrderTwoAfterFirst_PlansUpgrade перестанет ловить эту поломку. При смене apply_OrderTwoAfterFirst_PlansUpgrade проверь это утверждение.");
    }

    @Test
    void apply_OrderMet_NoTask() {
        Map<String, GraphState.Built> buildings = new LinkedHashMap<>();

        WarehouseApproval.apply(buildings, 2, order(2));

        assertEquals(
                GraphState.Built.BUILT,
                buildings.get("warehouse"),
                "Первый уровень стоит по журналу"
                        + " Иначе тест apply_OrderMet_NoTask перестанет ловить эту поломку. При смене apply_OrderMet_NoTask проверь это утверждение.");
        assertEquals(
                GraphState.Built.BUILT,
                buildings.get("warehouse_2"),
                "Второй уровень стоит по журналу"
                        + " Иначе тест apply_OrderMet_NoTask перестанет ловить эту поломку. При смене apply_OrderMet_NoTask проверь это утверждение.");
        assertEquals(
                GraphState.Built.ABSENT,
                buildings.get("warehouse_3"),
                "Третьего уровня стратег не просил"
                        + " Иначе тест apply_OrderMet_NoTask перестанет ловить эту поломку. При смене apply_OrderMet_NoTask проверь это утверждение.");
    }

    @Test
    void apply_NoOrderButBuilt_KeepsBuiltAndPlansNothing() {
        Map<String, GraphState.Built> buildings = new LinkedHashMap<>();

        WarehouseApproval.apply(buildings, 2, null);

        assertEquals(
                GraphState.Built.BUILT,
                buildings.get("warehouse"),
                "Построенный склад остаётся построенным"
                        + " Иначе тест apply_NoOrderButBuilt_KeepsBuiltAndPlansNothing перестанет ловить эту поломку. При смене apply_NoOrderButBuilt_KeepsBuiltAndPlansNothing проверь это утверждение.");
        assertEquals(
                GraphState.Built.BUILT,
                buildings.get("warehouse_2"),
                "Построенный второй уровень остаётся"
                        + " Иначе тест apply_NoOrderButBuilt_KeepsBuiltAndPlansNothing перестанет ловить эту поломку. При смене apply_NoOrderButBuilt_KeepsBuiltAndPlansNothing проверь это утверждение.");
        assertEquals(
                GraphState.Built.ABSENT,
                buildings.get("warehouse_3"),
                "Без нового решения апгрейда нет"
                        + " Иначе тест apply_NoOrderButBuilt_KeepsBuiltAndPlansNothing перестанет ловить эту поломку. При смене apply_NoOrderButBuilt_KeepsBuiltAndPlansNothing проверь это утверждение.");
    }

    @Test
    void apply_OrderFourAfterTwo_PlansThirdOnly() {
        Map<String, GraphState.Built> buildings = new LinkedHashMap<>();

        WarehouseApproval.apply(buildings, 2, order(4));

        assertEquals(
                GraphState.Built.BUILT,
                buildings.get("warehouse_2"),
                "Второй уровень уже стоит"
                        + " Иначе тест apply_OrderFourAfterTwo_PlansThirdOnly перестанет ловить эту поломку. При смене apply_OrderFourAfterTwo_PlansThirdOnly проверь это утверждение.");
        assertEquals(
                GraphState.Built.PLANNED,
                buildings.get("warehouse_3"),
                "Следующий уровень — третий, а не четвёртый"
                        + " Иначе тест apply_OrderFourAfterTwo_PlansThirdOnly перестанет ловить эту поломку. При смене apply_OrderFourAfterTwo_PlansThirdOnly проверь это утверждение.");
        assertEquals(
                GraphState.Built.ABSENT,
                buildings.get("warehouse_4"),
                "Четвёртый уровень ждёт третьего"
                        + " Иначе тест apply_OrderFourAfterTwo_PlansThirdOnly перестанет ловить эту поломку. При смене apply_OrderFourAfterTwo_PlansThirdOnly проверь это утверждение.");
    }

    @Test
    void id_Levels_MatchGraphNames() {
        assertEquals(
                "warehouse",
                WarehouseApproval.id(1),
                "Первый уровень — узел warehouse"
                        + " Иначе тест id_Levels_MatchGraphNames перестанет ловить эту поломку. При смене id_Levels_MatchGraphNames проверь это утверждение.");
        assertEquals(
                "warehouse_3",
                WarehouseApproval.id(3),
                "Третий уровень — узел warehouse_3"
                        + " Иначе тест id_Levels_MatchGraphNames перестанет ловить эту поломку. При смене id_Levels_MatchGraphNames проверь это утверждение.");
        assertEquals(
                0,
                WarehouseRules.builtLevel(new BoardLedger()),
                "Пустой журнал — склад не построен"
                        + " Иначе тест id_Levels_MatchGraphNames перестанет ловить эту поломку. При смене id_Levels_MatchGraphNames проверь это утверждение.");
    }

    private static WarehouseOrder order(int level) {
        return new WarehouseOrder(level, new BlockCoord(10, 64, 10), "ai", "нужен склад", 1);
    }
}

package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanStatesInputsTests {
    @Test
    void putGroups_PartlyBuiltWarehouses_GraphStaysPlanned() {
        Map<String, Built> buildings = new HashMap<>();
        PlanStatesInputs.putGroups(
                Map.of("warehouse", List.of("storage", "storage_2", "storage_3")),
                Map.of("storage", true, "storage_2", true, "storage_3", false),
                buildings);

        assertEquals(
                Built.PLANNED,
                buildings.get("warehouse"),
                "Два склада из трёх не закрывают узел warehouse: граф видит недоделанный склад и продолжает "
                        + "планировать, иначе третий участок теряется из виду. "
                        + "Иначе тест putGroups_PartlyBuiltWarehouses_GraphStaysPlanned перестанет ловить эту поломку. При смене putGroups_PartlyBuiltWarehouses_GraphStaysPlanned проверь это утверждение.");
    }

    @Test
    void putGroups_AllWarehousesBuilt_GraphIsBuilt() {
        Map<String, Built> buildings = new HashMap<>();
        PlanStatesInputs.putGroups(
                Map.of("warehouse", List.of("storage", "storage_2", "storage_3")),
                Map.of("storage", true, "storage_2", true, "storage_3", true),
                buildings);

        assertEquals(
                Built.BUILT,
                buildings.get("warehouse"),
                "Три склада из трёх закрывают узел warehouse: только тогда граф считает склад построенным "
                        + "и перестаёт выдавать стройку. "
                        + "Иначе тест putGroups_AllWarehousesBuilt_GraphIsBuilt перестанет ловить эту поломку. При смене putGroups_AllWarehousesBuilt_GraphIsBuilt проверь это утверждение.");
    }
}

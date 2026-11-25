package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanStatesInputsTests {
    @Test
    void auditedState_JournalPlanWithoutWorldProof_StaysPlanned() {
        assertEquals(
                Built.PLANNED,
                GraphBuildInputs.auditedState(Optional.empty()),
                "Запись плана в журнале не доказывает постройку: без аудита мира узел остаётся"
                        + " запланированным, иначе упавшее поле исчезает с доски навсегда Иначе тест"
                        + " auditedState_JournalPlanWithoutWorldProof_StaysPlanned перестанет ловить"
                        + " живой случай wheat_field от 25.11. При смене"
                        + " auditedState_JournalPlanWithoutWorldProof_StaysPlanned проверь GraphBuildInputs.planBuilt.");
        assertEquals(
                Built.PLANNED,
                GraphBuildInputs.auditedState(Optional.of(false)),
                "Явный отрицательный аудит тоже оставляет постройку незавершённой: граф обязан"
                        + " продолжить её выдавать, а не считать готовой по журналу Иначе тест"
                        + " auditedState_JournalPlanWithoutWorldProof_StaysPlanned перестанет ловить"
                        + " эту поломку. При смене auditedState_JournalPlanWithoutWorldProof_StaysPlanned"
                        + " проверь PlanStatesInputs.worldBuilt.");
        assertEquals(
                Built.BUILT,
                GraphBuildInputs.auditedState(Optional.of(true)),
                "Только положительный аудит мира закрывает постройку: иначе граф перестанет"
                        + " выдавать работу до фактического завершения схемы Иначе тест"
                        + " auditedState_JournalPlanWithoutWorldProof_StaysPlanned перестанет ловить"
                        + " эту поломку. При смене auditedState_JournalPlanWithoutWorldProof_StaysPlanned"
                        + " проверь BuildingAudit и GraphBuildInputs.");
    }

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

package com.theminespirit.core.planner;

import static com.theminespirit.core.planner.AcquireScriptTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.ConditionCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AcquireScriptPart4Tests {
    @Test
    void firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten() {
        AtomicInteger inventory = new AtomicInteger(1);
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryLive = inventory;
        AtomicInteger buildCount = new AtomicInteger();
        AcquireScript script = new AcquireScript(
                ITEM,
                3,
                alwaysGatherable(),
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> {
                            buildCount.incrementAndGet();
                            return ScriptRegistry.of(Map.of(
                                    "gather",
                                    params -> buildCount.get() == 1
                                            ? new GatherThenFailInstance(inventory)
                                            : new CountingInstance(2, inventory)));
                        },
                        NO_CONDITIONS));
        assertEquals(
                ScriptStatus.SUCCEEDED,
                run(script, 40),
                "Первая попытка добывает и падает, вторая успешна — acquire обязан дойти до конца Иначе тест firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten перестанет ловить эту поломку. При смене firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten проверь это утверждение.");
        Map<String, Object> fields = ((ScriptResult.Data) script.result().orElseThrow()).fields();
        assertEquals(
                1,
                fields.get("from_inventory"),
                "startInventory зафиксирован на первой попытке: пересборка не считает частичную добычу "
                        + "стартовым инвентарём Иначе тест firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten перестанет ловить эту поломку. При смене firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten проверь это утверждение.");
        assertEquals(
                3,
                fields.get("gathered"),
                "gathered считает обе попытки: 1 предмет до пересборки и 2 после, стартовые числа не съедены Иначе тест firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten перестанет ловить эту поломку. При смене firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten проверь это утверждение.");
        assertEquals(
                2,
                fields.get("attempts"),
                "Обе попытки учтены: первая упала, вторая успешна Иначе тест firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten перестанет ловить эту поломку. При смене firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten проверь это утверждение.");
    }

    @Test
    void savedRunnerState_AfterFirstTick_SnapshotDecodesBack() {
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(
                                Map.of("gather", params -> new CountingInstance(5, new AtomicInteger()))),
                        NO_CONDITIONS));
        script.tick();
        String snapshot = (String) script.saveState().get("runner");
        assertTrue(
                snapshot != null && TaskSnapshotCodec.decode(snapshot).plan() instanceof SequenceNode,
                "Снимок вложенной задачи обязан сохраняться строкой и разбираться обратно в то же дерево шагов Иначе тест savedRunnerState_AfterFirstTick_SnapshotDecodesBack перестанет ловить эту поломку. При смене savedRunnerState_AfterFirstTick_SnapshotDecodesBack проверь это утверждение.");
    }

    @Test
    void restore_WithoutRunnerSnapshot_SubplanRebuildsOnTick() {
        AcquireScript restored = script(1, new StubSupplies(0, Map.of()), alwaysGatherable(), new AtomicInteger());
        restored.restoreState(Map.of("attempts", 1));
        assertEquals(
                1,
                restored.attempts(),
                "Попытка читается из снимка, даже если снимка вложенной задачи нет Иначе тест restore_WithoutRunnerSnapshot_SubplanRebuildsOnTick перестанет ловить эту поломку. При смене restore_WithoutRunnerSnapshot_SubplanRebuildsOnTick проверь это утверждение.");
        assertEquals(
                ScriptStatus.RUNNING,
                run(restored, 5),
                "Без снимка подплан строится заново на первом тике: восстановление не ломает скрипт При смене restore_WithoutRunnerSnapshot_SubplanRebuildsOnTick проверь это утверждение.");
    }

    @Test
    void inventoryWithFuel_ResolverSeesFuel_NoGatherForFuel() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryOverride = Map.of("coal", 1);
        AcquireScript script = new AcquireScript(
                "iron_ingot",
                1,
                knowledge,
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));
        Optional<TaskNode> plan = script.buildSubplan();
        assertTrue(
                plan.isPresent(),
                "План обязан быть построен Иначе тест inventoryWithFuel_ResolverSeesFuel_NoGatherForFuel перестанет ловить эту поломку. При смене inventoryWithFuel_ResolverSeesFuel_NoGatherForFuel проверь это утверждение.");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(
                !scripts.contains("gather:coal"),
                "Топливо в инвентаре — резолвер не должен планировать его добычу:  Иначе тест inventoryWithFuel_ResolverSeesFuel_NoGatherForFuel перестанет ловить эту поломку. При смене inventoryWithFuel_ResolverSeesFuel_NoGatherForFuel проверь это утверждение."
                        + scripts);
    }

    @Test
    void emptyInventory_ResolverPlans_GatherForFuel() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        AcquireScript script = new AcquireScript(
                "iron_ingot",
                1,
                knowledge,
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));
        Optional<TaskNode> plan = script.buildSubplan();
        assertTrue(
                plan.isPresent(),
                "План обязан быть построен Иначе тест emptyInventory_ResolverPlans_GatherForFuel перестанет ловить эту поломку. При смене emptyInventory_ResolverPlans_GatherForFuel проверь это утверждение.");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(
                scripts.contains("gather:coal"),
                "Топлива нет в инвентаре — резолвер обязан спланировать его добычу:  Иначе тест emptyInventory_ResolverPlans_GatherForFuel перестанет ловить эту поломку. При смене emptyInventory_ResolverPlans_GatherForFuel проверь это утверждение."
                        + scripts);
    }

    @Test
    void nearbyFurnace_Present_PlanSkipsConstruction() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryOverride = Map.of("coal", 1);
        supplies.stations = Set.of("furnace");
        AcquireScript script = new AcquireScript(
                "iron_ingot",
                1,
                knowledge,
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));
        Optional<TaskNode> plan = script.buildSubplan();
        assertTrue(
                plan.isPresent(),
                "План обязан быть построен Иначе тест nearbyFurnace_Present_PlanSkipsConstruction перестанет ловить эту поломку. При смене nearbyFurnace_Present_PlanSkipsConstruction проверь это утверждение.");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(
                !scripts.contains("craft:furnace"),
                "Печь уже стоит в мире — подплан не вправе строить вторую:  Иначе тест nearbyFurnace_Present_PlanSkipsConstruction перестанет ловить эту поломку. При смене nearbyFurnace_Present_PlanSkipsConstruction проверь это утверждение."
                        + scripts);
        assertTrue(
                !scripts.contains("gather:cobblestone"),
                "Без постройки печи план не должен добывать булыжник под неё:  Иначе тест nearbyFurnace_Present_PlanSkipsConstruction перестанет ловить эту поломку. При смене nearbyFurnace_Present_PlanSkipsConstruction проверь это утверждение."
                        + scripts);
    }

    @Test
    void nearbyFurnace_Absent_PlanBuildsFurnace() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryOverride = Map.of("coal", 1);
        AcquireScript script = new AcquireScript(
                "iron_ingot",
                1,
                knowledge,
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));
        Optional<TaskNode> plan = script.buildSubplan();
        assertTrue(
                plan.isPresent(),
                "План обязан быть построен Иначе тест nearbyFurnace_Absent_PlanBuildsFurnace перестанет ловить эту поломку. При смене nearbyFurnace_Absent_PlanBuildsFurnace проверь это утверждение.");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(
                scripts.contains("craft:furnace"),
                "Рядом нет печи — резолвер обязан запланировать её постройку:  Иначе тест nearbyFurnace_Absent_PlanBuildsFurnace перестанет ловить эту поломку. При смене nearbyFurnace_Absent_PlanBuildsFurnace проверь это утверждение."
                        + scripts);
        assertTrue(
                scripts.contains("gather:cobblestone"),
                "Под постройку печи обязан быть запланирован булыжник:  Иначе тест nearbyFurnace_Absent_PlanBuildsFurnace перестанет ловить эту поломку. При смене nearbyFurnace_Absent_PlanBuildsFurnace проверь это утверждение."
                        + scripts);
    }

    @Test
    void succeededAcquire_Runner_CarriesDataResultWithNumbers() {
        AtomicInteger gatheredFromWorld = new AtomicInteger();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryLive = gatheredFromWorld;
        AcquireScript acquire = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(1, gatheredFromWorld))),
                        NO_CONDITIONS));
        TaskRunner runner = TaskRunner.start(
                new ScriptCall("acquire", Map.of("item", ITEM, "count", 1)),
                ScriptRegistry.of(Map.of("acquire", params -> acquire)),
                NO_CONDITIONS);
        assertEquals(
                RunStatus.SUCCEEDED,
                runToCompletion(runner),
                "Сбора одного предмета обязана хватить для успеха задачи Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение.");
        assertTrue(
                runner.successResult().orElse(null) instanceof ScriptResult.Data,
                "успешный acquire обязан класть итог в задачу, фактически  Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение."
                        + runner.successResult());
        Map<String, Object> fields = ((ScriptResult.Data) runner.successResult().orElseThrow()).fields();
        assertEquals(
                ITEM,
                fields.get("item"),
                "итог называет предмет: по нему стратег узнаёт, что добыто Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение.");
        assertEquals(
                1,
                fields.get("count"),
                "итог называет запрошенную цель Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение.");
        assertEquals(
                0,
                fields.get("from_inventory"),
                "из инвентаря ничего не взято Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение.");
        assertEquals(
                0,
                fields.get("from_chests"),
                "из сундуков ничего не взято Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение.");
        assertEquals(
                1,
                fields.get("gathered"),
                "один предмет добыт из мира Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение.");
        assertEquals(
                1,
                fields.get("attempts"),
                "первая попытка успешна — пересборок не было Иначе тест succeededAcquire_Runner_CarriesDataResultWithNumbers перестанет ловить эту поломку. При смене succeededAcquire_Runner_CarriesDataResultWithNumbers проверь это утверждение.");
    }

    @Test
    void failedAcquire_Result_IsEmpty() {
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new AlwaysFailingInstance())),
                        NO_CONDITIONS));
        assertEquals(
                ScriptStatus.FAILED,
                run(script, 40),
                "Шаг падает на каждой пересборке — acquire обязан честно завершиться FAILED Иначе тест failedAcquire_Result_IsEmpty перестанет ловить эту поломку. При смене failedAcquire_Result_IsEmpty проверь это утверждение.");
        assertTrue(
                script.result().isEmpty(),
                "провал обязан отдавать пустой итог: цифры успеха врут, когда цель не достигнута Иначе тест failedAcquire_Result_IsEmpty перестанет ловить эту поломку. При смене failedAcquire_Result_IsEmpty проверь это утверждение.");
    }

    @Test
    void constructor_BlankItem_IsRejectedWithReason() {
        String reason = assertThrows(
                        IllegalArgumentException.class,
                        () -> new AcquireScript(
                                "  ",
                                1,
                                alwaysGatherable(),
                                new StubSupplies(0, Map.of()),
                                CATALOG,
                                ConditionCatalog.empty(),
                                new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS)),
                        "Пустой item обязан отвергаться: без имени предмета скрипт не знает, что добыть Иначе тест constructor_BlankItem_IsRejectedWithReason перестанет ловить эту поломку. При смене constructor_BlankItem_IsRejectedWithReason проверь это утверждение.")
                .getMessage();
        assertTrue(
                reason.contains("Предмет"),
                "Ошибка называет предмет: по ней видно, чего не хватило Иначе тест constructor_BlankItem_IsRejectedWithReason перестанет ловить эту поломку. При смене constructor_BlankItem_IsRejectedWithReason проверь это утверждение.");
    }
}

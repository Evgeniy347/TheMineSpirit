package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AcquireScriptTests {
    private static final String ITEM = "stick";
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();
    private static final ConditionRegistry NO_CONDITIONS = ConditionRegistry.of(Map.of());

    @Test
    void inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan() {
        AtomicInteger registryCalls = new AtomicInteger();
        AcquireScript script = script(2, new StubSupplies(5, Map.of()), alwaysGatherable(), registryCalls);

        ScriptStatus status = script.tick();

        assertEquals(ScriptStatus.SUCCEEDED, status,
                "Цель уже в инвентаре — acquire обязан завершиться сразу, мир не трогаем");
        assertEquals(0, registryCalls.get(),
                "Реестр скриптов не должен запрашиваться: подплан не строится, когда хватает инвентаря");
        assertNull(script.failureReason(), "Успешный acquire не несёт причины провала");
    }

    @Test
    void shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw() {
        AcquireScript script = script(3, new StubSupplies(1, Map.of("chestA", 10)), alwaysGatherable(),
                new AtomicInteger());

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(plan.isPresent(), "Не хватает 2 из 3 — подплан обязан быть построен");
        List<TaskNode> steps = ((SequenceNode) plan.get()).steps();
        assertEquals("withdraw", ((ScriptCall) steps.get(0)).script(),
                "Снабжение идёт первым: сначала сундуки, потом мир");
        assertEquals(2, ((ScriptCall) steps.get(0)).params().get("count"),
                "withdraw берёт ровно остаток после инвентаря: 3 − 1 инвентарь");
        assertEquals("chestA", ((ScriptCall) steps.get(0)).params().get("container"),
                "Шаг обязан называть ближний сундук");
    }

    @Test
    void withdrawCoversRemainder_RemainderCovered_ResolverNotCalled() {
        StubKnowledge knowledge = alwaysGatherable();
        AcquireScript script = script(3, new StubSupplies(1, Map.of("chestA", 10)), knowledge,
                new AtomicInteger());

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(plan.isPresent(), "Остаток покрыт сундуком — подплан обязан быть");
        List<TaskNode> steps = ((SequenceNode) plan.get()).steps();
        assertEquals(1, steps.size(),
                "Инвентарь + сундук покрывают цель — gather/craft/smelt не планируются");
        assertTrue(knowledge.gatherQueries.isEmpty(),
                "Резолвер не вызывается, когда withdraw покрывает остаток: мир не трогаем без нужды");
    }

    @Test
    void chestsEmpty_RemainderUncovered_ResolverPlansGather() {
        StubKnowledge knowledge = alwaysGatherable();
        AcquireScript script = script(3, new StubSupplies(1, Map.of()), knowledge, new AtomicInteger());

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(plan.isPresent(), "Остаток 2 — подплан обязан быть");
        List<TaskNode> steps = ((SequenceNode) plan.get()).steps();
        assertEquals(List.of(ITEM), knowledge.gatherQueries,
                "Резолвер вызывается на остаток 3 − 1 = 2, когда сундуков нет");
        assertEquals("gather", ((ScriptCall) steps.get(steps.size() - 1)).script(),
                "Шаг добычи идёт последним, после снабжения");
    }

    @Test
    void stepKeepsFailing_AttemptsReachLimit_RebuildStops() {
        AtomicInteger builds = new AtomicInteger();
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> {
                    builds.incrementAndGet();
                    return ScriptRegistry.of(Map.of("gather", params -> new AlwaysFailingInstance()));
                }, NO_CONDITIONS));

        ScriptStatus status = run(script, 40);

        assertEquals(ScriptStatus.FAILED, status,
                "Шаг падает на каждой пересборке — acquire обязан честно завершиться FAILED");
        assertEquals(CatalogLimits.ACQUIRE_MAX_ATTEMPTS, script.attempts(),
                "Пересборок не больше ACQUIRE_MAX_ATTEMPTS: мир перечитывается ограниченное число раз");
        assertEquals(CatalogLimits.ACQUIRE_MAX_ATTEMPTS, builds.get(),
                "Каждая попытка строит новый подплан и берёт свежий реестр — но не больше предела");
        String reason = script.failureReason();
        assertTrue(reason != null && reason.contains(ITEM),
                "FAILED обязан нести причину с числами: по ней видно, что именно не добыли");
        assertTrue(reason.contains("×1"), "Причина называет цель: сколько просили");
        assertTrue(reason.contains("попыток " + CatalogLimits.ACQUIRE_MAX_ATTEMPTS),
                "Причина называет число попыток: «похоже, работает» не отдаём");
    }

    @Test
    void subplanExceedsBudget_NodesOverBudget_BuildFailsWithNumbers() {
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.chestSteps = withdrawSteps(CatalogLimits.COUNT_MAX + 1);
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> {
                    throw new AssertionError("Реестр не должен запрашиваться: подплан отклоняется по бюджету");
                }, NO_CONDITIONS));

        Optional<TaskNode> plan;
        String reason = null;
        try {
            plan = script.buildSubplan();
        } catch (RuntimeException error) {
            plan = Optional.empty();
            reason = error.getMessage();
        }

        assertTrue(plan.isEmpty(), "Подплан сверх 256 узлов обязан отклониться, а не запускаться");
        assertTrue(reason != null && reason.contains("256"),
                "Отклонение по бюджету обязано нести текст с пределом узлов: по нему видно, что упёрлись в бюджет");
        assertEquals(ScriptStatus.FAILED, script.tick(),
                "Провал сборки по бюджету — acquire завершается FAILED, а не крутится безлимитно");
        assertTrue(script.failureReason() != null && script.failureReason().contains("попыток"),
                "FAILED по бюджету обязан нести причину с числом попыток");
    }

    @Test
    void nestedRunnerRunning_WhileWorking_ProgressAndExemptionsDelegate() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(3, ticks))), NO_CONDITIONS));

        assertEquals(ScriptStatus.RUNNING, script.tick(), "Первый тик строит подплан и двигает первый шаг");
        assertEquals(ScriptStatus.RUNNING, script.tick(), "Счётный скрипт ещё работает");
        assertTrue(script.progress().isPresent(), "Вложенный шаг счётный — прогресс обязан дойти до acquire");
        assertEquals(2, script.progress().get().done(), "Прогресс делегируется вложенному раннеру");
        assertEquals(3, script.progress().get().target(), "Цель прогресса — из вложенного скрипта");
        assertEquals(ScriptStatus.SUCCEEDED, script.tick(), "Третий тик доводит счётный скрипт до конца");
    }

    @Test
    void nestedStarted_ControlSignals_ReachLiveStep() {
        List<String> calls = new ArrayList<>();
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new RecordingInstance(calls))), NO_CONDITIONS));

        script.tick();
        script.suspend();
        script.resume();
        script.cancel();

        assertEquals(List.of("suspend", "resume", "cancel"), calls,
                "Управление вложенным раннером доходит до живого скрипта шага: иначе отмена оставит духа в мире");
    }

    @Test
    void control_BeforeFirstTick_ScriptStillRunsAfter() {
        AcquireScript script = script(1, new StubSupplies(0, Map.of()), alwaysGatherable(),
                new AtomicInteger());

        script.suspend();
        script.resume();

        assertEquals(ScriptStatus.RUNNING, run(script, 5),
                "Управление до первого тика безопасно: подплан строится после, скрипт не падает");
    }

    @Test
    void cancel_BeforeFirstTick_ImmediatelyCancelled() {
        AcquireScript script = script(1, new StubSupplies(0, Map.of()), alwaysGatherable(),
                new AtomicInteger());

        script.cancel();

        assertEquals(ScriptStatus.CANCELLED, run(script, 5),
                "Отмена до первого тика сразу завершает скрипт: финальный статус CANCELLED, не FAILED");
    }

    @Test
    void suspend_BeforeFirstTick_NewRunnerIsSuspended() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))), NO_CONDITIONS));

        script.suspend();
        assertEquals(ScriptStatus.RUNNING, script.tick(),
                "suspend до первого тика не ломает сборку подплана");
        assertEquals(ScriptStatus.RUNNING, script.tick(),
                "Повторный тик при suspend не двигает вложенный шаг: раннер заморожен");
        assertEquals(0, ticks.get(),
                "suspend до первого тика обязан дойти до нового раннера: шаг не тикает");

        script.resume();
        assertEquals(ScriptStatus.RUNNING, script.tick(),
                "После resume вложенный шаг снова работает");
        assertEquals(1, ticks.get(),
                "resume размораживает раннер, созданный после suspend");
    }

    @Test
    void cancel_AfterNestedStarted_InsteadOfFailed() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))), NO_CONDITIONS));
        assertEquals(ScriptStatus.RUNNING, script.tick(), "Первый тик строит подплан");
        assertEquals(ScriptStatus.RUNNING, script.tick(), "Вложенный скрипт работает");

        script.cancel();

        assertEquals(ScriptStatus.CANCELLED, script.tick(),
                "Отмена вложенного раннера обязана давать CANCELLED, а не FAILED: отмена — не провал");
        assertNull(script.failureReason(), "CANCELLED не несёт причины провала: отмена — не ошибка");
    }

    @Test
    void saveRestore_BetweenTicks_AttemptAndSnapshotKept() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript first = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))), NO_CONDITIONS));
        assertEquals(ScriptStatus.RUNNING, first.tick(), "Первый тик строит подплан");
        assertEquals(ScriptStatus.RUNNING, first.tick(), "Вложенный скрипт работает");

        Map<String, Object> saved = first.saveState();
        AcquireScript restored = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))), NO_CONDITIONS));
        restored.restoreState(saved);

        assertEquals(first.attempts(), restored.attempts(),
                "restoreState не теряет попытку: перезапуск не даёт скрипту свежих пересборок");
        assertEquals(saved, restored.saveState(),
                "Снимок после восстановления совпадает с исходным: шаг и попытка не потеряны");
        assertEquals(2, restored.progress().get().done(),
                "Прогресс после восстановления — позиция вложенного шага до перезапуска, не ноль");
        assertEquals(ScriptStatus.RUNNING, restored.tick(),
                "Восстановленный скрипт продолжает с того же шага, а не начинает добычу заново");
        assertEquals(3, restored.progress().get().done(),
                "Тик после восстановления двигает тот же шаг: счётчик продолжился, а не начался заново");
    }

    @Test
    void saveRestore_MidRun_ResultKeepsSameNumbers() {
        AtomicInteger firstInventory = new AtomicInteger(1);
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryLive = firstInventory;
        AcquireScript first = new AcquireScript(ITEM, 3, alwaysGatherable(), supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(3, firstInventory))), NO_CONDITIONS));
        assertEquals(ScriptStatus.RUNNING, first.tick(), "Первый тик строит подплан");

        Map<String, Object> saved = first.saveState();
        AtomicInteger restoredInventory = new AtomicInteger(1);
        StubSupplies restoredSupplies = new StubSupplies(0, Map.of());
        restoredSupplies.inventoryLive = restoredInventory;
        AcquireScript restored = new AcquireScript(ITEM, 3, alwaysGatherable(), restoredSupplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(3, restoredInventory))), NO_CONDITIONS));
        restored.restoreState(saved);

        assertEquals(ScriptStatus.SUCCEEDED, run(restored, 20),
                "Восстановленный скрипт обязан довести цель до конца");
        Map<String, Object> fields = ((ScriptResult.Data) restored.result().orElseThrow()).fields();
        assertEquals(1, fields.get("from_inventory"),
                "startInventory сохраняется через saveState: перезапуск не теряет число из инвентаря");
        assertEquals(0, fields.get("from_chests"),
                "chestPlanned сохраняется через saveState: перезапуск не теряет число из сундуков");
        assertEquals(2, fields.get("gathered"),
                "gathered — остаток после инвентаря и сундуков, перезапуск его не меняет");
    }

    @Test
    void firstAttemptGathersThenFails_SecondSucceeds_StartInventoryNotOverwritten() {
        AtomicInteger inventory = new AtomicInteger(1);
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryLive = inventory;
        AtomicInteger buildCount = new AtomicInteger();
        AcquireScript script = new AcquireScript(ITEM, 3, alwaysGatherable(), supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> {
                    buildCount.incrementAndGet();
                    // Первая попытка добила 1 предмет и упала; вторая добывает 2.
                    return ScriptRegistry.of(Map.of("gather", params ->
                            buildCount.get() == 1
                                    ? new GatherThenFailInstance(inventory)
                                    : new CountingInstance(2, inventory)));
                }, NO_CONDITIONS));

        assertEquals(ScriptStatus.SUCCEEDED, run(script, 40),
                "Первая попытка добывает и падает, вторая успешна — acquire обязан дойти до конца");
        Map<String, Object> fields = ((ScriptResult.Data) script.result().orElseThrow()).fields();
        assertEquals(1, fields.get("from_inventory"),
                "startInventory зафиксирован на первой попытке: пересборка не считает частичную добычу "
                        + "стартовым инвентарём");
        assertEquals(3, fields.get("gathered"),
                "gathered считает обе попытки: 1 предмет до пересборки и 2 после, стартовые числа не съедены");
        assertEquals(2, fields.get("attempts"),
                "Обе попытки учтены: первая упала, вторая успешна");
    }

    @Test
    void savedRunnerState_AfterFirstTick_SnapshotDecodesBack() {
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, new AtomicInteger()))), NO_CONDITIONS));
        script.tick();

        String snapshot = (String) script.saveState().get("runner");

        assertTrue(snapshot != null && TaskSnapshotCodec.decode(snapshot).plan() instanceof SequenceNode,
                "Снимок вложенной задачи обязан сохраняться строкой и разбираться обратно в то же дерево шагов");
    }

    @Test
    void restore_WithoutRunnerSnapshot_SubplanRebuildsOnTick() {
        AcquireScript restored = script(1, new StubSupplies(0, Map.of()), alwaysGatherable(),
                new AtomicInteger());
        restored.restoreState(Map.of("attempts", 1));

        assertEquals(1, restored.attempts(), "Попытка читается из снимка, даже если снимка вложенной задачи нет");
        assertEquals(ScriptStatus.RUNNING, run(restored, 5),
                "Без снимка подплан строится заново на первом тике: восстановление не ломает скрипт");
    }

    @Test
    void inventoryWithFuel_ResolverSeesFuel_NoGatherForFuel() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryOverride = Map.of("coal", 1);
        AcquireScript script = new AcquireScript("iron_ingot", 1, knowledge, supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(plan.isPresent(), "План обязан быть построен");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(!scripts.contains("gather:coal"),
                "Топливо в инвентаре — резолвер не должен планировать его добычу: " + scripts);
    }

    @Test
    void emptyInventory_ResolverPlans_GatherForFuel() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        AcquireScript script = new AcquireScript("iron_ingot", 1, knowledge, new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(plan.isPresent(), "План обязан быть построен");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(scripts.contains("gather:coal"),
                "Топлива нет в инвентаре — резолвер обязан спланировать его добычу: " + scripts);
    }

    @Test
    void nearbyFurnace_Present_PlanSkipsConstruction() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryOverride = Map.of("coal", 1);
        supplies.stations = Set.of("furnace");
        AcquireScript script = new AcquireScript("iron_ingot", 1, knowledge, supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(plan.isPresent(), "План обязан быть построен");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(!scripts.contains("craft:furnace"),
                "Печь уже стоит в мире — подплан не вправе строить вторую: " + scripts);
        assertTrue(!scripts.contains("gather:cobblestone"),
                "Без постройки печи план не должен добывать булыжник под неё: " + scripts);
    }

    @Test
    void nearbyFurnace_Absent_PlanBuildsFurnace() {
        FuelAwareKnowledge knowledge = new FuelAwareKnowledge();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryOverride = Map.of("coal", 1);
        AcquireScript script = new AcquireScript("iron_ingot", 1, knowledge, supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS));

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(plan.isPresent(), "План обязан быть построен");
        List<String> scripts = scriptsOf(plan.get());
        assertTrue(scripts.contains("craft:furnace"),
                "Рядом нет печи — резолвер обязан запланировать её постройку: " + scripts);
        assertTrue(scripts.contains("gather:cobblestone"),
                "Под постройку печи обязан быть запланирован булыжник: " + scripts);
    }

    @Test
    void succeededAcquire_Runner_CarriesDataResultWithNumbers() {
        AtomicInteger gatheredFromWorld = new AtomicInteger();
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryLive = gatheredFromWorld;
        AcquireScript acquire = new AcquireScript(ITEM, 1, alwaysGatherable(), supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(1, gatheredFromWorld))), NO_CONDITIONS));
        TaskRunner runner = TaskRunner.start(new ScriptCall("acquire", Map.of("item", ITEM, "count", 1)),
                ScriptRegistry.of(Map.of("acquire", params -> acquire)), NO_CONDITIONS);

        assertEquals(RunStatus.SUCCEEDED, runToCompletion(runner),
                "Сбора одного предмета обязана хватить для успеха задачи");
        assertTrue(runner.successResult().orElse(null) instanceof ScriptResult.Data,
                "успешный acquire обязан класть итог в задачу, фактически " + runner.successResult());
        Map<String, Object> fields = ((ScriptResult.Data) runner.successResult().orElseThrow()).fields();
        assertEquals(ITEM, fields.get("item"), "итог называет предмет: по нему стратег узнаёт, что добыто");
        assertEquals(1, fields.get("count"), "итог называет запрошенную цель");
        assertEquals(0, fields.get("from_inventory"), "из инвентаря ничего не взято");
        assertEquals(0, fields.get("from_chests"), "из сундуков ничего не взято");
        assertEquals(1, fields.get("gathered"), "один предмет добыт из мира");
        assertEquals(1, fields.get("attempts"), "первая попытка успешна — пересборок не было");
    }

    @Test
    void failedAcquire_Result_IsEmpty() {
        AcquireScript script = new AcquireScript(ITEM, 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of("gather", params -> new AlwaysFailingInstance())), NO_CONDITIONS));

        assertEquals(ScriptStatus.FAILED, run(script, 40),
                "Шаг падает на каждой пересборке — acquire обязан честно завершиться FAILED");
        assertTrue(script.result().isEmpty(),
                "провал обязан отдавать пустой итог: цифры успеха врут, когда цель не достигнута");
    }

    @Test
    void constructor_BlankItem_IsRejectedWithReason() {
        String reason = assertThrows(IllegalArgumentException.class,
                () -> new AcquireScript("  ", 1, alwaysGatherable(), new StubSupplies(0, Map.of()), CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(() -> ScriptRegistry.of(Map.of()), NO_CONDITIONS)),
                "Пустой item обязан отвергаться: без имени предмета скрипт не знает, что добыть")
                .getMessage();

        assertTrue(reason.contains("Предмет"), "Ошибка называет предмет: по ней видно, чего не хватило");
    }

    private static Supplier<ScriptRegistry> countingRegistry(AtomicInteger calls, int ticks) {
        return () -> {
            calls.incrementAndGet();
            return ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(ticks, new AtomicInteger())));
        };
    }

    private static ScriptStatus run(AcquireScript script, int maxTicks) {
        ScriptStatus status = ScriptStatus.RUNNING;
        for (int i = 0; i < maxTicks && status == ScriptStatus.RUNNING; i++) {
            status = script.tick();
        }
        return status;
    }

    private static RunStatus runToCompletion(TaskRunner runner) {
        RunStatus status = RunStatus.RUNNING;
        for (int i = 0; i < 20 && status == RunStatus.RUNNING; i++) {
            status = runner.tick();
        }
        return status;
    }

    private static AcquireScript script(int count, StubSupplies supplies, StubKnowledge knowledge,
            AtomicInteger registryCalls) {
        return new AcquireScript(ITEM, count, knowledge, supplies, CATALOG, ConditionCatalog.empty(), new AcquireScript.Registries(countingRegistry(registryCalls, 10), NO_CONDITIONS));
    }

    private static StubKnowledge alwaysGatherable() {
        return new StubKnowledge();
    }

    private static List<ScriptCall> withdrawSteps(int steps) {
        List<ScriptCall> calls = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            calls.add(new ScriptCall("withdraw", Map.of(
                    "container", i + ",64," + i,
                    "item", ITEM,
                    "count", 1,
                    "radius", CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
        }
        return calls;
    }

    private static final class StubSupplies implements AcquireSupplies {
        private final int inventory;
        private final Map<String, Integer> chests;
        private List<ScriptCall> chestSteps;
        private Map<String, Integer> inventoryOverride = Map.of();
        private Set<String> stations = Set.of();
        private AtomicInteger inventoryLive;

        StubSupplies(int inventory, Map<String, Integer> chests) {
            this.inventory = inventory;
            this.chests = new LinkedHashMap<>(chests);
        }

        @Override
        public int inInventory(String item) {
            return inventoryLive == null ? inventory : inventoryLive.get();
        }

        @Override
        public Map<String, Integer> inventory() {
            return inventoryOverride;
        }

        @Override
        public Set<String> nearbyStations() {
            return stations;
        }

        @Override
        public List<ScriptCall> withdraw(String item, int maxCount) {
            if (chestSteps != null) {
                return chestSteps;
            }
            List<ScriptCall> steps = new ArrayList<>();
            int left = maxCount;
            for (Map.Entry<String, Integer> chest : chests.entrySet()) {
                int available = Math.min(chest.getValue(), left);
                if (available <= 0) {
                    continue;
                }
                steps.add(new ScriptCall("withdraw", Map.of(
                        "container", chest.getKey(),
                        "item", item,
                        "count", available,
                        "radius", CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
                left -= available;
            }
            return steps;
        }
    }

    private static final class StubKnowledge implements ResourceKnowledge {
        private final List<String> gatherQueries = new ArrayList<>();

        @Override
        public boolean isGatherable(String item) {
            gatherQueries.add(item);
            return true;
        }

        @Override
        public boolean isCraftable(String item) {
            return false;
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public boolean isSmeltable(String item) {
            return false;
        }

        @Override
        public String smeltInputFor(String item) {
            throw new AssertionError("Скрипт в тесте не плавит: резолвер не должен спрашивать вход плавки");
        }
    }

    private static List<String> scriptsOf(TaskNode node) {
        List<String> scripts = new ArrayList<>();
        collect(node, scripts);
        return scripts;
    }

    private static void collect(TaskNode node, List<String> scripts) {
        switch (node) {
            case ScriptCall call -> scripts.add(call.script() + ":" + call.params().getOrDefault("block",
                    call.params().getOrDefault("item", "?")));
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, scripts));
            default -> throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName()
                    + " не разбирается: тест ждёт sequence и script");
        }
    }

    private static final class FuelAwareKnowledge implements ResourceKnowledge {
        @Override
        public boolean isGatherable(String item) {
            return item.equals("raw_iron") || item.equals("coal") || item.equals("cobblestone");
        }

        @Override
        public boolean isCraftable(String item) {
            return item.equals("furnace");
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of(new Ingredient("cobblestone", 8));
        }

        @Override
        public boolean isSmeltable(String item) {
            return item.equals("iron_ingot");
        }

        @Override
        public String smeltInputFor(String item) {
            return "raw_iron";
        }

        @Override
        public Optional<String> stationFor(String item) {
            return item.equals("iron_ingot") ? Optional.of("furnace") : Optional.empty();
        }

        @Override
        public int burnTicks(String item) {
            return item.equals("coal") ? 1600 : 0;
        }
    }

    private static final class AlwaysFailingInstance implements ScriptInstance {
        @Override
        public ScriptStatus tick() {
            return ScriptStatus.FAILED;
        }

        @Override
        public String failureReason() {
            return "нет блока в радиусе";
        }
    }

    private static final class GatherThenFailInstance implements ScriptInstance {
        private final AtomicInteger shared;
        private boolean gathered;

        GatherThenFailInstance(AtomicInteger sharedTicks) {
            this.shared = sharedTicks;
        }

        @Override
        public ScriptStatus tick() {
            if (!gathered) {
                gathered = true;
                shared.incrementAndGet();
                return ScriptStatus.RUNNING;
            }
            return ScriptStatus.FAILED;
        }

        @Override
        public String failureReason() {
            return "блок пропал после частичной добычи";
        }
    }

    private static final class CountingInstance implements ScriptInstance {
        private final int target;
        private final AtomicInteger shared;
        private int done;

        CountingInstance(int target, AtomicInteger sharedTicks) {
            this.target = target;
            this.shared = sharedTicks;
        }

        @Override
        public ScriptStatus tick() {
            done++;
            shared.incrementAndGet();
            return done >= target ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
        }

        @Override
        public Optional<Progress> progress() {
            return Optional.of(new Progress(Math.min(done, target), target));
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("done", done);
        }

        @Override
        public void restoreState(Map<String, Object> state) {
            done = (Integer) state.getOrDefault("done", 0);
        }
    }

    private static final class RecordingInstance implements ScriptInstance {
        private final List<String> calls;

        RecordingInstance(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public ScriptStatus tick() {
            return ScriptStatus.RUNNING;
        }

        @Override
        public void suspend() {
            calls.add("suspend");
        }

        @Override
        public void resume() {
            calls.add("resume");
        }

        @Override
        public void cancel() {
            calls.add("cancel");
        }
    }
}

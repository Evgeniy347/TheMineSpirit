package com.theminespirit.core.planner;

import static com.theminespirit.core.planner.AcquireScriptTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ConditionCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AcquireScriptTests {
    @Test
    void inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan() {
        AtomicInteger registryCalls = new AtomicInteger();
        AcquireScript script = script(2, new StubSupplies(5, Map.of()), alwaysGatherable(), registryCalls);

        ScriptStatus status = script.tick();

        assertEquals(
                ScriptStatus.SUCCEEDED,
                status,
                "Цель уже в инвентаре — acquire обязан завершиться сразу, мир не трогаем Иначе тест inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan перестанет ловить эту поломку. При смене inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan проверь это утверждение.");
        assertEquals(
                0,
                registryCalls.get(),
                "Реестр скриптов не должен запрашиваться: подплан не строится, когда хватает инвентаря Иначе тест inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan перестанет ловить эту поломку. При смене inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan проверь это утверждение.");
        assertNull(
                script.failureReason(),
                "Успешный acquire не несёт причины провала Иначе тест inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan перестанет ловить эту поломку. При смене inventoryCoversGoal_FirstTick_SucceedsWithoutSubplan проверь это утверждение.");
    }

    @Test
    void gatherAt_InventoryCoversLayer_NoWalkOutside() {
        AcquireScript script = script(4, new StubSupplies(5, Map.of()), alwaysGatherable(), new AtomicInteger())
                .gatherAt(new BlockCoord(30, 64, 30));

        assertEquals(
                Optional.empty(),
                script.buildSubplan(),
                "Земли в инвентаре хватает на слой — добор пропускается, дух не ходит за ограду (INC-68) Иначе тест gatherAt_InventoryCoversLayer_NoWalkOutside перестанет ловить эту поломку. При смене gatherAt_InventoryCoversLayer_NoWalkOutside проверь это утверждение.");
    }

    @Test
    void gatherAt_Shortfall_WalksToPlaceBeforeGather() {
        AcquireScript script = script(4, new StubSupplies(1, Map.of()), alwaysGatherable(), new AtomicInteger())
                .gatherAt(new BlockCoord(30, 64, 30));

        List<TaskNode> steps = ((SequenceNode) script.buildSubplan().orElseThrow()).steps();

        assertEquals(
                new ScriptCall("goto", Map.of("x", 30, "y", 64, "z", 30)),
                steps.get(0),
                "Не хватает — сначала к месту добычи за оградой, а не копать землю участка (INC-68) Иначе тест gatherAt_Shortfall_WalksToPlaceBeforeGather перестанет ловить эту поломку. При смене gatherAt_Shortfall_WalksToPlaceBeforeGather проверь это утверждение.");
    }

    @Test
    void gatherAtLeast_SmallShortfall_TripBringsMinimum() {
        AcquireScript script = script(4, new StubSupplies(1, Map.of()), alwaysGatherable(), new AtomicInteger())
                .gatherAt(new BlockCoord(30, 64, 30))
                .gatherAtLeast(32);

        assertTrue(
                script.buildSubplan().orElseThrow().toString().contains("count=32"),
                "Не хватает 3 — ходка за ограду приносит не меньше 32: ходок по 2–3 блока нет (INC-73) Иначе тест gatherAtLeast_SmallShortfall_TripBringsMinimum перестанет ловить эту поломку. При смене gatherAtLeast_SmallShortfall_TripBringsMinimum проверь это утверждение.");
    }

    @Test
    void shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw() {
        AcquireScript script =
                script(3, new StubSupplies(1, Map.of("chestA", 10)), alwaysGatherable(), new AtomicInteger());

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(
                plan.isPresent(),
                "Не хватает 2 из 3 — подплан обязан быть построен Иначе тест shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw перестанет ловить эту поломку. При смене shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw проверь это утверждение.");
        List<TaskNode> steps = ((SequenceNode) plan.get()).steps();
        assertEquals(
                "withdraw",
                ((ScriptCall) steps.get(0)).script(),
                "Снабжение идёт первым: сначала сундуки, потом мир Иначе тест shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw перестанет ловить эту поломку. При смене shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw проверь это утверждение.");
        assertEquals(
                2,
                ((ScriptCall) steps.get(0)).params().get("count"),
                "withdraw берёт ровно остаток после инвентаря: 3 − 1 инвентарь Иначе тест shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw перестанет ловить эту поломку. При смене shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw проверь это утверждение.");
        assertEquals(
                "chestA",
                ((ScriptCall) steps.get(0)).params().get("container"),
                "Шаг обязан называть ближний сундук Иначе тест shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw перестанет ловить эту поломку. При смене shortfallExists_ChestCoversRemainder_PlanStartsWithWithdraw проверь это утверждение.");
    }

    @Test
    void withdrawCoversRemainder_RemainderCovered_ResolverNotCalled() {
        LocalStubKnowledge knowledge = alwaysGatherable();
        AcquireScript script = script(3, new StubSupplies(1, Map.of("chestA", 10)), knowledge, new AtomicInteger());

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(
                plan.isPresent(),
                "Остаток покрыт сундуком — подплан обязан быть Иначе тест withdrawCoversRemainder_RemainderCovered_ResolverNotCalled перестанет ловить эту поломку. При смене withdrawCoversRemainder_RemainderCovered_ResolverNotCalled проверь это утверждение.");
        List<TaskNode> steps = ((SequenceNode) plan.get()).steps();
        assertEquals(
                1,
                steps.size(),
                "Инвентарь + сундук покрывают цель — gather/craft/smelt не планируются Иначе тест withdrawCoversRemainder_RemainderCovered_ResolverNotCalled перестанет ловить эту поломку. При смене withdrawCoversRemainder_RemainderCovered_ResolverNotCalled проверь это утверждение.");
        assertTrue(
                knowledge.gatherQueries.isEmpty(),
                "Резолвер не вызывается, когда withdraw покрывает остаток: мир не трогаем без нужды Иначе тест withdrawCoversRemainder_RemainderCovered_ResolverNotCalled перестанет ловить эту поломку. При смене withdrawCoversRemainder_RemainderCovered_ResolverNotCalled проверь это утверждение.");
    }

    @Test
    void chestsEmpty_RemainderUncovered_ResolverPlansGather() {
        LocalStubKnowledge knowledge = alwaysGatherable();
        AcquireScript script = script(3, new StubSupplies(1, Map.of()), knowledge, new AtomicInteger());

        Optional<TaskNode> plan = script.buildSubplan();

        assertTrue(
                plan.isPresent(),
                "Остаток 2 — подплан обязан быть Иначе тест chestsEmpty_RemainderUncovered_ResolverPlansGather перестанет ловить эту поломку. При смене chestsEmpty_RemainderUncovered_ResolverPlansGather проверь это утверждение.");
        List<TaskNode> steps = ((SequenceNode) plan.get()).steps();
        assertEquals(
                List.of(ITEM),
                knowledge.gatherQueries,
                "Резолвер вызывается на остаток 3 − 1 = 2, когда сундуков нет Иначе тест chestsEmpty_RemainderUncovered_ResolverPlansGather перестанет ловить эту поломку. При смене chestsEmpty_RemainderUncovered_ResolverPlansGather проверь это утверждение.");
        assertEquals(
                "gather",
                ((ScriptCall) steps.get(steps.size() - 1)).script(),
                "Шаг добычи идёт последним, после снабжения Иначе тест chestsEmpty_RemainderUncovered_ResolverPlansGather перестанет ловить эту поломку. При смене chestsEmpty_RemainderUncovered_ResolverPlansGather проверь это утверждение.");
    }

    @Test
    void stepKeepsFailing_AttemptsReachLimit_RebuildStops() {
        AtomicInteger builds = new AtomicInteger();
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> {
                            builds.incrementAndGet();
                            return ScriptRegistry.of(Map.of("gather", params -> new AlwaysFailingInstance()));
                        },
                        NO_CONDITIONS));

        ScriptStatus status = run(script, 40);

        assertEquals(
                ScriptStatus.FAILED,
                status,
                "Шаг падает на каждой пересборке — acquire обязан честно завершиться FAILED Иначе тест stepKeepsFailing_AttemptsReachLimit_RebuildStops перестанет ловить эту поломку. При смене stepKeepsFailing_AttemptsReachLimit_RebuildStops проверь это утверждение.");
        assertEquals(
                CatalogLimits.ACQUIRE_MAX_ATTEMPTS,
                script.attempts(),
                "Пересборок не больше ACQUIRE_MAX_ATTEMPTS: мир перечитывается ограниченное число раз Иначе тест stepKeepsFailing_AttemptsReachLimit_RebuildStops перестанет ловить эту поломку. При смене stepKeepsFailing_AttemptsReachLimit_RebuildStops проверь это утверждение.");
        assertEquals(
                CatalogLimits.ACQUIRE_MAX_ATTEMPTS,
                builds.get(),
                "Каждая попытка строит новый подплан и берёт свежий реестр — но не больше предела Иначе тест stepKeepsFailing_AttemptsReachLimit_RebuildStops перестанет ловить эту поломку. При смене stepKeepsFailing_AttemptsReachLimit_RebuildStops проверь это утверждение.");
        String reason = script.failureReason();
        assertTrue(
                reason != null && reason.contains(ITEM),
                "FAILED обязан нести причину с числами: по ней видно, что именно не добыли Иначе тест stepKeepsFailing_AttemptsReachLimit_RebuildStops перестанет ловить эту поломку. При смене stepKeepsFailing_AttemptsReachLimit_RebuildStops проверь это утверждение.");
        assertTrue(
                reason.contains("×1"),
                "Причина называет цель: сколько просили Иначе тест stepKeepsFailing_AttemptsReachLimit_RebuildStops перестанет ловить эту поломку. При смене stepKeepsFailing_AttemptsReachLimit_RebuildStops проверь это утверждение.");
        assertTrue(
                reason.contains("попыток " + CatalogLimits.ACQUIRE_MAX_ATTEMPTS),
                "Причина называет число попыток: «похоже, работает» не отдаём Иначе тест stepKeepsFailing_AttemptsReachLimit_RebuildStops перестанет ловить эту поломку. При смене stepKeepsFailing_AttemptsReachLimit_RebuildStops проверь это утверждение.");
    }
}

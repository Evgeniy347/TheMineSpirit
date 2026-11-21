package com.theminespirit.core.planner;

import static com.theminespirit.core.planner.AcquireScriptTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.api.ConditionCatalog;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AcquireScriptPart3Tests {
    @Test
    void cancel_AfterNestedStarted_InsteadOfFailed() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript script = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))),
                        NO_CONDITIONS));
        assertEquals(
                ScriptStatus.RUNNING,
                script.tick(),
                "Первый тик строит подплан Иначе тест cancel_AfterNestedStarted_InsteadOfFailed перестанет ловить эту поломку. При смене cancel_AfterNestedStarted_InsteadOfFailed проверь это утверждение.");
        assertEquals(
                ScriptStatus.RUNNING,
                script.tick(),
                "Вложенный скрипт работает Иначе тест cancel_AfterNestedStarted_InsteadOfFailed перестанет ловить эту поломку. При смене cancel_AfterNestedStarted_InsteadOfFailed проверь это утверждение.");
        script.cancel();
        assertEquals(
                ScriptStatus.CANCELLED,
                script.tick(),
                "Отмена вложенного раннера обязана давать CANCELLED, а не FAILED: отмена — не провал Иначе тест cancel_AfterNestedStarted_InsteadOfFailed перестанет ловить эту поломку. При смене cancel_AfterNestedStarted_InsteadOfFailed проверь это утверждение.");
        assertNull(
                script.failureReason(),
                "CANCELLED не несёт причины провала: отмена — не ошибка Иначе тест cancel_AfterNestedStarted_InsteadOfFailed перестанет ловить эту поломку. При смене cancel_AfterNestedStarted_InsteadOfFailed проверь это утверждение.");
    }

    @Test
    void saveRestore_BetweenTicks_AttemptAndSnapshotKept() {
        AtomicInteger ticks = new AtomicInteger();
        AcquireScript first = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))),
                        NO_CONDITIONS));
        assertEquals(
                ScriptStatus.RUNNING,
                first.tick(),
                "Первый тик строит подплан Иначе тест saveRestore_BetweenTicks_AttemptAndSnapshotKept перестанет ловить эту поломку. При смене saveRestore_BetweenTicks_AttemptAndSnapshotKept проверь это утверждение.");
        assertEquals(
                ScriptStatus.RUNNING,
                first.tick(),
                "Вложенный скрипт работает Иначе тест saveRestore_BetweenTicks_AttemptAndSnapshotKept перестанет ловить эту поломку. При смене saveRestore_BetweenTicks_AttemptAndSnapshotKept проверь это утверждение.");
        Map<String, Object> saved = first.saveState();
        AcquireScript restored = new AcquireScript(
                ITEM,
                1,
                alwaysGatherable(),
                new StubSupplies(0, Map.of()),
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(5, ticks))),
                        NO_CONDITIONS));
        restored.restoreState(saved);
        assertEquals(
                first.attempts(),
                restored.attempts(),
                "restoreState не теряет попытку: перезапуск не даёт скрипту свежих пересборок Иначе тест saveRestore_BetweenTicks_AttemptAndSnapshotKept перестанет ловить эту поломку. При смене saveRestore_BetweenTicks_AttemptAndSnapshotKept проверь это утверждение.");
        assertEquals(
                saved,
                restored.saveState(),
                "Снимок после восстановления совпадает с исходным: шаг и попытка не потеряны Иначе тест saveRestore_BetweenTicks_AttemptAndSnapshotKept перестанет ловить эту поломку. При смене saveRestore_BetweenTicks_AttemptAndSnapshotKept проверь это утверждение.");
        assertEquals(
                2,
                restored.progress().get().done(),
                "Прогресс после восстановления — позиция вложенного шага до перезапуска, не ноль Иначе тест saveRestore_BetweenTicks_AttemptAndSnapshotKept перестанет ловить эту поломку. При смене saveRestore_BetweenTicks_AttemptAndSnapshotKept проверь это утверждение.");
        assertEquals(
                ScriptStatus.RUNNING,
                restored.tick(),
                "Восстановленный скрипт продолжает с того же шага, а не начинает добычу заново Иначе тест saveRestore_BetweenTicks_AttemptAndSnapshotKept перестанет ловить эту поломку. При смене saveRestore_BetweenTicks_AttemptAndSnapshotKept проверь это утверждение.");
        assertEquals(
                3,
                restored.progress().get().done(),
                "Тик после восстановления двигает тот же шаг: счётчик продолжился, а не начался заново Иначе тест saveRestore_BetweenTicks_AttemptAndSnapshotKept перестанет ловить эту поломку. При смене saveRestore_BetweenTicks_AttemptAndSnapshotKept проверь это утверждение.");
    }

    @Test
    void saveRestore_MidRun_ResultKeepsSameNumbers() {
        AtomicInteger firstInventory = new AtomicInteger(1);
        StubSupplies supplies = new StubSupplies(0, Map.of());
        supplies.inventoryLive = firstInventory;
        AcquireScript first = new AcquireScript(
                ITEM,
                3,
                alwaysGatherable(),
                supplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(3, firstInventory))),
                        NO_CONDITIONS));
        assertEquals(
                ScriptStatus.RUNNING,
                first.tick(),
                "Первый тик строит подплан Иначе тест saveRestore_MidRun_ResultKeepsSameNumbers перестанет ловить эту поломку. При смене saveRestore_MidRun_ResultKeepsSameNumbers проверь это утверждение.");
        Map<String, Object> saved = first.saveState();
        AtomicInteger restoredInventory = new AtomicInteger(1);
        StubSupplies restoredSupplies = new StubSupplies(0, Map.of());
        restoredSupplies.inventoryLive = restoredInventory;
        AcquireScript restored = new AcquireScript(
                ITEM,
                3,
                alwaysGatherable(),
                restoredSupplies,
                CATALOG,
                ConditionCatalog.empty(),
                new AcquireScript.Registries(
                        () -> ScriptRegistry.of(Map.of("gather", params -> new CountingInstance(3, restoredInventory))),
                        NO_CONDITIONS));
        restored.restoreState(saved);
        assertEquals(
                ScriptStatus.SUCCEEDED,
                run(restored, 20),
                "Восстановленный скрипт обязан довести цель до конца Иначе тест saveRestore_MidRun_ResultKeepsSameNumbers перестанет ловить эту поломку. При смене saveRestore_MidRun_ResultKeepsSameNumbers проверь это утверждение.");
        Map<String, Object> fields = ((ScriptResult.Data) restored.result().orElseThrow()).fields();
        assertEquals(
                1,
                fields.get("from_inventory"),
                "startInventory сохраняется через saveState: перезапуск не теряет число из инвентаря Иначе тест saveRestore_MidRun_ResultKeepsSameNumbers перестанет ловить эту поломку. При смене saveRestore_MidRun_ResultKeepsSameNumbers проверь это утверждение.");
        assertEquals(
                0,
                fields.get("from_chests"),
                "chestPlanned сохраняется через saveState: перезапуск не теряет число из сундуков Иначе тест saveRestore_MidRun_ResultKeepsSameNumbers перестанет ловить эту поломку. При смене saveRestore_MidRun_ResultKeepsSameNumbers проверь это утверждение.");
        assertEquals(
                2,
                fields.get("gathered"),
                "gathered — остаток после инвентаря и сундуков, перезапуск его не меняет Иначе тест saveRestore_MidRun_ResultKeepsSameNumbers перестанет ловить эту поломку. При смене saveRestore_MidRun_ResultKeepsSameNumbers проверь это утверждение.");
    }
}

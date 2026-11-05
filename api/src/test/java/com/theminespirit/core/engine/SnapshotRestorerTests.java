package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ConditionSpec;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotRestorerTests {

    @Test
    void withValidatedPlan_FillsOptionalDefault_SoRestoredScriptReceivesIt() {
        ScriptCatalog catalog = ScriptCatalog.of(List.of(new ScriptSpec(
                "follow",
                "иди за целью",
                List.of(ParamSpec.integer("radius", false, 1, 16, 5)),
                List.of(),
                List.of())));
        TaskSnapshot snapshot = running(new ScriptCall("follow", Map.of()));

        TaskSnapshot validated = SnapshotRestorer.withValidatedPlan(snapshot, catalog, ConditionCatalog.empty());
        assertEquals(
                5,
                ((ScriptCall) validated.plan()).params().get("radius"),
                "проверенный план обязан подставить умолчание необязательного параметра: адаптер восстанавливает "
                        + "именно проверенный план, а не исходный снимок, иначе скрипт упадёт на requireInt");

        List<Map<String, Object>> seenParams = new ArrayList<>();
        ScriptRegistry registry = ScriptRegistry.of(Map.of("follow", params -> {
            seenParams.add(params);
            return () -> ScriptStatus.SUCCEEDED;
        }));
        TaskRunner runner = TaskRunner.restore(validated, registry, ConditionRegistry.of(Map.of()));
        assertEquals(RunStatus.SUCCEEDED, runner.tick(), "восстановленный скрипт обязан исполниться");
        assertEquals(
                5,
                seenParams.get(0).get("radius"),
                "восстановленный скрипт обязан получить подставленное умолчание, а не пустой набор параметров");
    }

    @Test
    void withValidatedPlan_ConditionCatalogGiven_UsesIt() {
        ScriptCatalog scripts = ScriptCatalog.of(List.of(
                new ScriptSpec("wait", "жди", List.of(ParamSpec.integer("ticks", true)), List.of(), List.of())));
        ConditionCatalog withCheck =
                ConditionCatalog.of(List.of(new ConditionSpec("seen", "кого-то видел", List.of())));
        TaskNode plan =
                new ConditionNode("seen", Map.of(), new ScriptCall("wait", Map.of("ticks", Integer.valueOf(1))), null);
        TaskSnapshot snapshot = running(plan);

        TaskSnapshot validated = SnapshotRestorer.withValidatedPlan(snapshot, scripts, withCheck);
        assertEquals(
                RunStatus.RUNNING,
                validated.status(),
                "проверка из переданного каталога условий обязана быть принята, план остаётся работающим");

        assertThrows(
                PlanValidationException.class,
                () -> SnapshotRestorer.withValidatedPlan(snapshot, scripts, ConditionCatalog.empty()),
                "с пустым каталогом условий проверка плана обязана упасть: значит каталог действительно "
                        + "передаётся в проверку, а не подменяется пустым");
    }

    private static TaskSnapshot running(TaskNode plan) {
        return new TaskSnapshot(
                TaskSnapshot.VERSION, RunStatus.RUNNING, null, plan, new TaskProgress.ScriptAt(Map.of()));
    }
}

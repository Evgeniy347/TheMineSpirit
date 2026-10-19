package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.policy.PolicyId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskRunnerSuspendTests {
    private static final class Probe implements ScriptInstance {
        private final int total;
        private final PolicyExemptions exemptions;
        int ticks;
        int suspends;
        int resumes;
        int cancels;

        Probe(int total, PolicyExemptions exemptions) {
            this.total = total;
            this.exemptions = exemptions;
        }

        @Override
        public ScriptStatus tick() {
            ticks++;
            return ticks >= total ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
        }

        @Override
        public void suspend() {
            suspends++;
        }

        @Override
        public void resume() {
            resumes++;
        }

        @Override
        public void cancel() {
            cancels++;
        }

        @Override
        public PolicyExemptions policyExemptions() {
            return exemptions;
        }
    }

    private static final class Fixture {
        final List<Probe> created = new ArrayList<>();

        ScriptRegistry registry(String id, int total, PolicyExemptions exemptions) {
            return ScriptRegistry.of(Map.of(id, params -> {
                Probe probe = new Probe(total, exemptions);
                created.add(probe);
                return probe;
            }));
        }
    }

    private static ScriptCall call(String id) {
        return new ScriptCall(id, Map.of());
    }

    private static ConditionRegistry noConditions() {
        return ConditionRegistry.of(Map.of());
    }

    @Test
    void tick_Suspended_DoesNotTickScriptAndStaysRunning() {
        Fixture fixture = new Fixture();
        TaskRunner runner =
                TaskRunner.start(call("walk"), fixture.registry("walk", 3, PolicyExemptions.empty()), noConditions());
        runner.tick();

        runner.suspend();
        for (int i = 0; i < 20; i++) {
            assertEquals(
                    RunStatus.RUNNING,
                    runner.tick(),
                    "Приостановленная задача остаётся RUNNING: политика не отменяет работу");
        }

        assertEquals(
                1,
                fixture.created.get(0).ticks,
                "Приостановленный скрипт не тикает: иначе его тайм-аут идёт, пока политика владеет духом");
        assertTrue(
                runner.isSuspended(),
                "Признак приостановки виден снаружи: по нему отчёт различает «ждёт политику» и «работает»");
    }

    @Test
    void resume_SuspendedScript_ContinuesWhereItStopped() {
        Fixture fixture = new Fixture();
        TaskRunner runner =
                TaskRunner.start(call("walk"), fixture.registry("walk", 3, PolicyExemptions.empty()), noConditions());
        runner.tick();
        runner.suspend();
        runner.tick();

        runner.resume();

        assertEquals(
                RunStatus.RUNNING,
                runner.tick(),
                "Второй тик скрипта из трёх после возобновления: задача продолжается");
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "Третий тик завершает: счёт тиков скрипта не сбрасывался приостановкой");
        assertEquals(
                1,
                fixture.created.size(),
                "Скрипт тот же, новый не создавался: приостановка не отменяет и не пересоздаёт скрипт");
    }

    @Test
    void suspend_LiveScript_IsNotifiedOncePerSuspension() {
        Fixture fixture = new Fixture();
        TaskRunner runner =
                TaskRunner.start(call("walk"), fixture.registry("walk", 5, PolicyExemptions.empty()), noConditions());
        runner.tick();

        runner.suspend();
        runner.suspend();
        runner.resume();
        runner.resume();

        Probe probe = fixture.created.get(0);
        assertEquals(
                1,
                probe.suspends,
                "Повторная приостановка не вызывает suspend() скрипта второй раз: остановка навигации идемпотентна, но счёт вызовов должен быть честным");
        assertEquals(1, probe.resumes, "Повторное возобновление не вызывает resume() второй раз");
    }

    @Test
    void suspend_SequenceOfScripts_KeepsCurrentScriptName() {
        Fixture fixture = new Fixture();
        TaskNode plan = new SequenceNode(List.of(call("walk"), call("walk")));
        TaskRunner runner =
                TaskRunner.start(plan, fixture.registry("walk", 2, PolicyExemptions.empty()), noConditions());
        runner.tick();

        runner.suspend();

        assertEquals(
                "walk",
                runner.currentScript().orElseThrow(),
                "Приостановленная задача сохраняет текущий скрипт: по нему видно, что она не отменена");
        assertEquals(
                1,
                fixture.created.get(0).suspends,
                "Скрипт внутри sequence получает suspend(): иначе навигация продолжит работу, пока политика владеет духом");
    }

    @Test
    void snapshot_Suspended_EqualsSnapshotOfRunningTask() {
        Fixture fixture = new Fixture();
        TaskRunner runner =
                TaskRunner.start(call("walk"), fixture.registry("walk", 5, PolicyExemptions.empty()), noConditions());
        runner.tick();
        TaskSnapshot running = runner.snapshot().orElseThrow();

        runner.suspend();

        assertEquals(
                running,
                runner.snapshot().orElseThrow(),
                "Снимок приостановленной задачи совпадает с обычным: сохранение мира во время рефлекса не теряет задачу");
    }

    @Test
    void cancel_Suspended_CancelsScriptAndEndsCancelled() {
        Fixture fixture = new Fixture();
        TaskRunner runner =
                TaskRunner.start(call("walk"), fixture.registry("walk", 5, PolicyExemptions.empty()), noConditions());
        runner.tick();
        runner.suspend();

        runner.cancel();

        assertEquals(RunStatus.CANCELLED, runner.status(), "Отмена приостановленной задачи штатная");
        assertEquals(1, fixture.created.get(0).cancels, "Скрипт получил cancel()");
        assertFalse(runner.isSuspended(), "Отменённая задача не числится приостановленной");
    }

    @Test
    void suspend_ParallelScripts_NotifiesEveryRunningScript() {
        Fixture fixture = new Fixture();
        TaskNode plan = new ParallelNode(ParallelMode.ALL, List.of(call("walk"), call("walk")));
        TaskRunner runner =
                TaskRunner.start(plan, fixture.registry("walk", 5, PolicyExemptions.empty()), noConditions());
        runner.tick();

        runner.suspend();

        assertEquals(
                List.of(1, 1),
                fixture.created.stream().map(probe -> probe.suspends).toList(),
                "Оба параллельных скрипта обязаны остановить навигацию: иначе один из них продолжит идти, пока политика владеет духом");
    }

    @Test
    void policyExemptions_ParallelScripts_AreUnionOfRunningOnes() {
        Fixture water = new Fixture();
        Fixture portal = new Fixture();
        ScriptRegistry registry = ScriptRegistry.of(Map.of(
                "swim",
                        params -> {
                            Probe probe = new Probe(5, PolicyExemptions.of(PolicyId.WATER));
                            water.created.add(probe);
                            return probe;
                        },
                "enter",
                        params -> {
                            Probe probe = new Probe(5, PolicyExemptions.of(PolicyId.PORTAL));
                            portal.created.add(probe);
                            return probe;
                        }));
        TaskRunner runner = TaskRunner.start(
                new ParallelNode(ParallelMode.ALL, List.of(call("swim"), call("enter"))), registry, noConditions());
        runner.tick();

        assertEquals(
                PolicyExemptions.of(PolicyId.WATER, PolicyId.PORTAL),
                runner.policyExemptions(),
                "Исключения параллельных скриптов объединяются: каждый скрипт объявляет своё узкое исключение");
    }

    @Test
    void policyExemptions_FinishedTask_IsEmpty() {
        Fixture fixture = new Fixture();
        TaskRunner runner =
                TaskRunner.start(call("walk"), fixture.registry("walk", 1, PolicyExemptions.empty()), noConditions());
        runner.tick();

        assertEquals(
                PolicyExemptions.empty(),
                runner.policyExemptions(),
                "У завершённой задачи исключений нет: иначе исключение WATER пережило бы скрипт, который его объявил");
    }

    @Test
    void policyExemptions_RunningScriptWithWater_IsReported() {
        Fixture fixture = new Fixture();
        TaskRunner runner = TaskRunner.start(
                call("swim"), fixture.registry("swim", 5, PolicyExemptions.of(PolicyId.WATER)), noConditions());
        runner.tick();

        assertEquals(
                PolicyExemptions.of(PolicyId.WATER),
                runner.policyExemptions(),
                "Исключение живого скрипта доступно рантайму политик");
    }
}

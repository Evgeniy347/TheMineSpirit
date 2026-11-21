package com.theminespirit.core.engine;

import static com.theminespirit.core.engine.TaskRunnerTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskRunnerPart2Tests {
    @Test
    void tick_ConditionCheckFalse_RunsElseBranch() {
        CountingFactory thenFactory = new CountingFactory(TestScripts.succeeding(1));
        CountingFactory elseFactory = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = new ConditionNode(
                CHECK, Map.of(), new ScriptCall("then_script", Map.of()), new ScriptCall("else_script", Map.of()));
        TaskRunner runner = TaskRunner.start(
                plan,
                ScriptRegistry.of(
                        Map.of("then_script", thenFactory.asFactory(), "else_script", elseFactory.asFactory())),
                TestScripts.condition(CHECK, false));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "ложная проверка condition обязана исполнять ветку else Иначе тест tick_ConditionCheckFalse_RunsElseBranch перестанет ловить эту поломку. При смене tick_ConditionCheckFalse_RunsElseBranch проверь это утверждение.");
        assertEquals(
                0,
                thenFactory.created,
                "ложная проверка не должна исполнять ветку then Иначе тест tick_ConditionCheckFalse_RunsElseBranch перестанет ловить эту поломку. При смене tick_ConditionCheckFalse_RunsElseBranch проверь это утверждение.");
        assertEquals(
                1,
                elseFactory.created,
                "ложная проверка обязана создать экземпляр ветки else Иначе тест tick_ConditionCheckFalse_RunsElseBranch перестанет ловить эту поломку. При смене tick_ConditionCheckFalse_RunsElseBranch проверь это утверждение.");
    }

    @Test
    void tick_ConditionWithoutElseAndFalseCheck_SucceedsWithoutRunningThen() {
        CountingFactory factory = new CountingFactory(TestScripts.succeeding(1));
        TaskNode plan = new ConditionNode(CHECK, Map.of(), script(SCRIPT), null);
        TaskRunner runner = TaskRunner.start(
                plan, ScriptRegistry.of(Map.of(SCRIPT, factory.asFactory())), TestScripts.condition(CHECK, false));
        assertEquals(
                RunStatus.SUCCEEDED,
                runner.tick(),
                "condition без else при ложной проверке трактуется как успех: нечего делать — это не провал Иначе тест tick_ConditionWithoutElseAndFalseCheck_SucceedsWithoutRunningThen перестанет ловить эту поломку. При смене tick_ConditionWithoutElseAndFalseCheck_SucceedsWithoutRunningThen проверь это утверждение.");
        assertEquals(
                0,
                factory.created,
                "при ложной проверке без else ветка then не исполняется Иначе тест tick_ConditionWithoutElseAndFalseCheck_SucceedsWithoutRunningThen перестанет ловить эту поломку. При смене tick_ConditionWithoutElseAndFalseCheck_SucceedsWithoutRunningThen проверь это утверждение.");
    }

    @Test
    void tick_ConditionUnknownCheck_ReturnsFailureNamingCheck() {
        TaskNode plan = new ConditionNode("ghost_check", Map.of(), script(SCRIPT), null);
        TaskRunner runner = TaskRunner.start(
                plan, TestScripts.singleRegistry(SCRIPT, TestScripts.succeeding(1)), ConditionRegistry.of(Map.of()));
        RunStatus status = runner.tick();
        assertEquals(
                RunStatus.FAILED,
                status,
                "неизвестная проверка обязана проваливать узел: иначе условие молча выберет ветку наугад При смене tick_ConditionUnknownCheck_ReturnsFailureNamingCheck проверь это утверждение.");
        assertTrue(
                runner.failureReason().orElse("").contains("ghost_check"),
                "причина обязана называть неизвестную проверку 'ghost_check'. Фактическое: "
                        + runner.failureReason()
                                .orElse(
                                        " Иначе тест tick_ConditionUnknownCheck_ReturnsFailureNamingCheck перестанет ловить эту поломку. При смене tick_ConditionUnknownCheck_ReturnsFailureNamingCheck проверь это утверждение."));
    }

    @Test
    void tick_UnknownScript_ReturnsFailureWithoutThrowing() {
        TaskRunner runner =
                TaskRunner.start(script("ghost"), ScriptRegistry.of(Map.of()), TestScripts.condition(CHECK, true));
        RunStatus status = runner.tick();
        assertEquals(
                RunStatus.FAILED,
                status,
                "неизвестный скрипт обязан проваливать узел, а не бросать исключение наружу: движок не должен "
                        + "ронять серверный тик из-за плана Иначе тест tick_UnknownScript_ReturnsFailureWithoutThrowing перестанет ловить эту поломку. При смене tick_UnknownScript_ReturnsFailureWithoutThrowing проверь это утверждение.");
        assertTrue(
                runner.failureReason().orElse("").contains("ghost"),
                "причина обязана называть неизвестный скрипт 'ghost'. Фактическое: "
                        + runner.failureReason()
                                .orElse(
                                        " Иначе тест tick_UnknownScript_ReturnsFailureWithoutThrowing перестанет ловить эту поломку. При смене tick_UnknownScript_ReturnsFailureWithoutThrowing проверь это утверждение."));
    }

    @Test
    void tick_ScriptFactoryThrows_ReturnsFailureNamingScriptAndException() {
        ScriptFactory throwing = params -> {
            throw new IllegalArgumentException("нет параметра x");
        };
        TaskRunner runner = TaskRunner.start(
                script("goto"), ScriptRegistry.of(Map.of("goto", throwing)), TestScripts.condition(CHECK, true));
        RunStatus status = runner.tick();
        assertEquals(
                RunStatus.FAILED,
                status,
                "исключение из фабрики скрипта обязано проваливать узел, а не вылетать из тика сущности: "
                        + "иначе сервер упадёт с ошибкой 'Ticking entity' При смене tick_ScriptFactoryThrows_ReturnsFailureNamingScriptAndException проверь это утверждение.");
        String reason = runner.failureReason().orElse("");
        assertTrue(
                reason.contains("goto"),
                "причина обязана называть скрипт 'goto', из которого прилетело исключение. Фактическое:  Иначе тест tick_ScriptFactoryThrows_ReturnsFailureNamingScriptAndException перестанет ловить эту поломку. При смене tick_ScriptFactoryThrows_ReturnsFailureNamingScriptAndException проверь это утверждение."
                        + reason);
        assertTrue(
                reason.contains("нет параметра x"),
                "причина обязана нести текст исключения 'нет параметра x'. Фактическое:  Иначе тест tick_ScriptFactoryThrows_ReturnsFailureNamingScriptAndException перестанет ловить эту поломку. При смене tick_ScriptFactoryThrows_ReturnsFailureNamingScriptAndException проверь это утверждение."
                        + reason);
    }

    @Test
    void tick_ScriptInstanceThrows_ReturnsFailureNamingScriptAndException() {
        ScriptFactory throwing = params -> () -> {
            throw new IllegalStateException("сломанный тик");
        };
        TaskRunner runner = TaskRunner.start(
                script("goto"), ScriptRegistry.of(Map.of("goto", throwing)), TestScripts.condition(CHECK, true));
        RunStatus status = runner.tick();
        assertEquals(
                RunStatus.FAILED,
                status,
                "исключение из tick скрипта обязано проваливать узел, а не вылетать из тика сущности: "
                        + "иначе сервер упадёт с ошибкой 'Ticking entity' При смене tick_ScriptInstanceThrows_ReturnsFailureNamingScriptAndException проверь это утверждение.");
        String reason = runner.failureReason().orElse("");
        assertTrue(
                reason.contains("goto"),
                "причина обязана называть скрипт 'goto', в котором сломался тик. Фактическое:  Иначе тест tick_ScriptInstanceThrows_ReturnsFailureNamingScriptAndException перестанет ловить эту поломку. При смене tick_ScriptInstanceThrows_ReturnsFailureNamingScriptAndException проверь это утверждение."
                        + reason);
        assertTrue(
                reason.contains("сломанный тик"),
                "причина обязана нести текст исключения 'сломанный тик'. Фактическое:  Иначе тест tick_ScriptInstanceThrows_ReturnsFailureNamingScriptAndException перестанет ловить эту поломку. При смене tick_ScriptInstanceThrows_ReturnsFailureNamingScriptAndException проверь это утверждение."
                        + reason);
    }

    @Test
    void tick_ScriptFailsWithOwnReason_ReportsThatReason() {
        ScriptFactory factory = params -> new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                return ScriptStatus.FAILED;
            }

            @Override
            public String failureReason() {
                return "не дошёл до (1, 2, 3) за 40 тиков";
            }
        };
        TaskRunner runner = TaskRunner.start(
                script("goto"), ScriptRegistry.of(Map.of("goto", factory)), TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "скрипт, назвавший причину провала, обязан провалить узел Иначе тест tick_ScriptFailsWithOwnReason_ReportsThatReason перестанет ловить эту поломку. При смене tick_ScriptFailsWithOwnReason_ReportsThatReason проверь это утверждение.");
        assertEquals(
                "не дошёл до (1, 2, 3) за 40 тиков",
                runner.failureReason().orElse(null),
                "причина провала обязана быть текстом самого скрипта: общее «завершился с ошибкой» скрывает, "
                        + "почему goto не дошёл, и игрок не знает, что чинить Иначе тест tick_ScriptFailsWithOwnReason_ReportsThatReason перестанет ловить эту поломку. При смене tick_ScriptFailsWithOwnReason_ReportsThatReason проверь это утверждение.");
    }

    @Test
    void tick_ScriptFailsWithoutReason_ReportsGenericScriptReason() {
        ScriptFactory factory = params -> () -> ScriptStatus.FAILED;
        TaskRunner runner = TaskRunner.start(
                script("goto"), ScriptRegistry.of(Map.of("goto", factory)), TestScripts.condition(CHECK, true));
        assertEquals(
                RunStatus.FAILED,
                runner.tick(),
                "скрипт без причины, вернувший FAILED, обязан провалить узел Иначе тест tick_ScriptFailsWithoutReason_ReportsGenericScriptReason перестанет ловить эту поломку. При смене tick_ScriptFailsWithoutReason_ReportsGenericScriptReason проверь это утверждение.");
        assertEquals(
                "скрипт 'goto' завершился с ошибкой",
                runner.failureReason().orElse(null),
                "скрипт без собственной причины провала обязан дать общий текст с именем скрипта: пустая причина "
                        + "оставила бы в логе задачу без объяснения Иначе тест tick_ScriptFailsWithoutReason_ReportsGenericScriptReason перестанет ловить эту поломку. При смене tick_ScriptFailsWithoutReason_ReportsGenericScriptReason проверь это утверждение.");
    }
}

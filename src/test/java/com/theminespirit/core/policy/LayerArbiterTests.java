package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.tactics.Layer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LayerArbiterTests {
    static String run(String script) {
        LayerArbiter arbiter = new LayerArbiter();
        StringBuilder owners = new StringBuilder();
        for (char step : script.toCharArray()) {
            boolean reflex = step == 'r' || step == 'x';
            boolean tactics = step == 't';
            boolean task = step != 'i' && step != 'x';
            PolicyVerdict verdict = reflex ? new PolicyVerdict.Reflex(PolicyId.LAVA_FIRE, new ReflexAction.StopMoving(), "тест") : new PolicyVerdict.Clear();
            owners.append(letter(arbiter.step(verdict, tactics, task)));
        }
        return owners.toString();
    }

    static char letter(Layer layer) {
        return switch (layer) {
            case POLICY -> 'P';
            case TACTICS -> 'T';
            case STRATEGY -> 'S';
            case IDLE -> 'I';
        };
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of("нет задачи — IDLE", "ii", "II"),
                Arguments.of("есть задача — STRATEGY сразу", "ss", "SS"),
                Arguments.of("рефлекс при бездействии — POLICY сразу", "ix", "IP"),
                Arguments.of("рефлекс прерывает задачу — POLICY сразу", "sr", "SP"),
                Arguments.of("рефлекс прерывает тактику — POLICY сразу", "tr", "TP"),
                Arguments.of("тактика прерывает стратегию сразу", "st", "ST"),
                Arguments.of("политика держит владение 9 безопасных тиков", "sr" + "s".repeat(9), "S" + "P".repeat(10)),
                Arguments.of("на 10-й безопасный тик задача возобновляется", "sr" + "s".repeat(10), "S" + "P".repeat(10) + "S"),
                Arguments.of("новый рефлекс обнуляет отсчёт", "r" + "s".repeat(9) + "r" + "s".repeat(10), "P".repeat(20) + "S"),
                Arguments.of("после политики без задачи — IDLE через задержку", "x" + "i".repeat(10), "P".repeat(10) + "I"),
                Arguments.of("тактика держит владение 9 тиков после конца боя", "t" + "i".repeat(9), "T".repeat(10)),
                Arguments.of("тактика отдаёт владение стратегии на 10-й тик", "t" + "s".repeat(10), "T".repeat(10) + "S"),
                Arguments.of("конец задачи без прерывателя — IDLE сразу", "ssi", "SSI"),
                Arguments.of("бой сразу после рефлекса ждёт задержки", "rt", "PP")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void step_Script_GivesExpectedOwners(String name, String script, String expected) {
        assertEquals(expected, run(script), "Сценарий «" + name + "» (" + script + "): политика всегда выигрывает, нижний слой ждёт 10 тиков, пока верхний не уступил");
    }

    @Test
    void delay_Constant_IsTenTicks() {
        assertEquals(10, LayerArbiter.RESUME_DELAY_TICKS, "Возобновление после 10 подряд безопасных тиков (полсекунды): достаточно, чтобы дух вышел из зоны опасности");
    }

    @Test
    void owner_BeforeFirstStep_IsIdle() {
        assertEquals(Layer.IDLE, new LayerArbiter().owner(), "Без шагов никто не владеет тиком, кроме IDLE");
    }
}

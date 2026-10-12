package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ExplosionAvoidance.ThreatFact;
import com.theminespirit.core.policy.ExplosionAvoidance.ThreatType;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExplosionAvoidanceTests {
    private static final int SIDE = 21;
    private static final BlockCoord SELF = new BlockCoord(10, 1, 10);

    static AsciiWorld openRoom() {
        String floor = String.join("/", Collections.nCopies(SIDE, "#".repeat(SIDE)));
        String air = String.join("/", Collections.nCopies(SIDE, ".".repeat(SIDE)));
        return AsciiWorld.of(new BlockCoord(0, 0, 0), Cell.SOLID, floor, air, air);
    }

    private static ThreatFact fact(ThreatType type, int dx, int dz, boolean swelling) {
        return new ThreatFact(type, new BlockCoord(SELF.x() + dx, SELF.y(), SELF.z() + dz), swelling);
    }

    private static double distance(BlockCoord a, BlockCoord b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of("крипер в 6", ThreatType.CREEPER, 6, 0, false, 7.0, true),
                Arguments.of("крипер в 8", ThreatType.CREEPER, 8, 0, false, 7.0, false),
                Arguments.of("крипер ровно в 7", ThreatType.CREEPER, 7, 0, false, 7.0, false),
                Arguments.of("крипер по диагонали (4;5): 6.4", ThreatType.CREEPER, 4, 5, false, 7.0, true),
                Arguments.of("крипер по диагонали (5;5): 7.07", ThreatType.CREEPER, 5, 5, false, 7.0, false),
                Arguments.of("заряженный крипер в 8", ThreatType.CHARGED_CREEPER, 8, 0, false, 13.0, true),
                Arguments.of("заряженный крипер в 10", ThreatType.CHARGED_CREEPER, 10, 0, false, 13.0, true),
                Arguments.of("заряженный крипер в 13", ThreatType.CHARGED_CREEPER, 13, 0, false, 13.0, false),
                Arguments.of("TNT в 7", ThreatType.PRIMED_TNT, 7, 0, false, 9.0, true),
                Arguments.of("TNT в 8: радиус урона 8 по центру, нужен запас на смещение духа в клетке", ThreatType.PRIMED_TNT, 8, 0, false, 9.0, true),
                Arguments.of("TNT в 9", ThreatType.PRIMED_TNT, 9, 0, false, 9.0, false),
                Arguments.of("набухающий крипер в 8", ThreatType.CREEPER, 8, 0, true, 10.0, true),
                Arguments.of("набухающий крипер в 10", ThreatType.CREEPER, 10, 0, true, 10.0, false),
                Arguments.of("набухающий заряженный в 15", ThreatType.CHARGED_CREEPER, 15, 0, true, 16.0, true),
                Arguments.of("набухающий заряженный в 16", ThreatType.CHARGED_CREEPER, 16, 0, true, 16.0, false),
                Arguments.of("крипер далеко (в 30 блоках за комнатой)", ThreatType.CREEPER, 30, 0, false, 7.0, false),
                Arguments.of("крипер вплотную", ThreatType.CREEPER, 1, 0, false, 7.0, true),
                Arguments.of("крипер в той же клетке", ThreatType.CREEPER, 0, 0, false, 7.0, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void evaluate_OneThreat_FleesToTheHoldDistanceOrStaysClear(String name, ThreatType type, int dx, int dz, boolean swelling,
                                                               double expectedHold, boolean flees) {
        ThreatFact fact = fact(type, dx, dz, swelling);

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(SELF, List.of(fact), openRoom());

        if (!flees) {
            assertEquals(new PolicyVerdict.Clear(), verdict, "Строка «" + name + "»: угроза дальше расстояния держания " + expectedHold + ", дух не должен бежать");
            return;
        }
        PolicyVerdict.Reflex reflex = assertInstanceOf(PolicyVerdict.Reflex.class, verdict, "Строка «" + name + "»: угроза ближе " + expectedHold + ", ждали рефлекс");
        assertEquals(PolicyId.EXPLOSIVE, reflex.policy(), "Строка «" + name + "»: рефлекс принадлежит политике EXPLOSIVE");
        ReflexAction.Flee flee = assertInstanceOf(ReflexAction.Flee.class, reflex.action(), "Строка «" + name + "»: в открытой комнате бегство возможно, причина: " + reflex.reason());
        assertTrue(distance(flee.cell(), fact.pos()) >= expectedHold,
                "Строка «" + name + "»: после бегства расстояние до угрозы не меньше " + expectedHold + ". Цель: " + flee.cell());
    }

    @Test
    void evaluate_TwoCreepersOnOppositeSides_FleesPerpendicularlyToBothHolds() {
        ThreatFact west = fact(ThreatType.CREEPER, -5, 0, false);
        ThreatFact east = fact(ThreatType.CREEPER, 5, 0, false);

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(SELF, List.of(west, east), openRoom());

        PolicyVerdict.Reflex reflex = assertInstanceOf(PolicyVerdict.Reflex.class, verdict, "Два крипера в пяти блоках — рефлекс обязателен");
        ReflexAction.Flee flee = assertInstanceOf(ReflexAction.Flee.class, reflex.action(), "Выход вбок есть, причина: " + reflex.reason());
        assertTrue(distance(flee.cell(), west.pos()) >= 7.0 && distance(flee.cell(), east.pos()) >= 7.0,
                "Бежать нужно так, чтобы выдержать 7 блоков до обоих, а не убежать от одного в лапы другого. Цель: " + flee.cell());
    }

    @Test
    void evaluate_OneViolatedAndOneFarThreat_PlansAroundBoth() {
        ThreatFact near = fact(ThreatType.CREEPER, 3, 0, false);
        ThreatFact far = fact(ThreatType.CREEPER, -9, 0, false);

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(SELF, List.of(near, far), openRoom());

        ReflexAction.Flee flee = assertInstanceOf(ReflexAction.Flee.class, ((PolicyVerdict.Reflex) verdict).action(), "Выход есть");
        assertTrue(distance(flee.cell(), near.pos()) >= 7.0 && distance(flee.cell(), far.pos()) >= 7.0,
                "Дальний крипер тоже держится на 7: бегство от ближнего не должно вести к нему. Цель: " + flee.cell());
    }

    @Test
    void evaluate_CreeperAtTheMouthOfTheDeadEnd_StopsMoving() {
        AsciiWorld corridor = AsciiWorld.of(new BlockCoord(0, 0, 0), Cell.SOLID, "#########", "#@.1....#", "#.......#");

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(corridor.marker('@'),
                List.of(new ThreatFact(ThreatType.CREEPER, corridor.marker('1'), false)), corridor);

        PolicyVerdict.Reflex reflex = assertInstanceOf(PolicyVerdict.Reflex.class, verdict, "Крипер в двух блоках — рефлекс обязателен");
        assertEquals(new ReflexAction.StopMoving(), reflex.action(), "Дух в тупике, крипер у выхода: пройти мимо нельзя, стоять лучше, чем идти в крипера");
    }

    @Test
    void evaluate_CreeperBehindTheWall_StillTriggersTheReflex() {
        String floor = "#############/#############/#############";
        String feet = "#.....@.....#/#############/#.....1.....#";
        String head = "#...........#/#############/#...........#";
        AsciiWorld walled = AsciiWorld.of(new BlockCoord(0, 0, 0), Cell.SOLID, floor, feet, head);

        PolicyVerdict verdict = ExplosionAvoidance.evaluate(walled.marker('@'),
                List.of(new ThreatFact(ThreatType.CREEPER, walled.marker('1'), false)), walled);

        PolicyVerdict.Reflex reflex = assertInstanceOf(PolicyVerdict.Reflex.class, verdict, "Взрыв крипера рушит стену: два блока камня не защита");
        ReflexAction.Flee flee = assertInstanceOf(ReflexAction.Flee.class, reflex.action(), "Вдоль стены бежать можно, причина: " + reflex.reason());
        assertEquals(new BlockCoord(11, 1, 0), flee.cell(), "Коридор слишком короткий для 7 блоков (до x=11 расстояние 5.4): дух уходит в самую дальнюю клетку вдоль стены, восточную раньше западной");
    }

    @Test
    void evaluate_SwellingCreeper_ReasonSaysUrgent() {
        PolicyVerdict verdict = ExplosionAvoidance.evaluate(SELF, List.of(fact(ThreatType.CREEPER, 2, 0, true)), openRoom());

        PolicyVerdict.Reflex reflex = assertInstanceOf(PolicyVerdict.Reflex.class, verdict, "Набухающий крипер в двух блоках — рефлекс обязателен");
        assertTrue(reflex.reason().startsWith("срочно, угроза набухает: CREEPER"),
                "Набухание помечено в причине как срочное: по логу видно, что дух бежал от готового взорваться. Причина: " + reflex.reason());
    }

    @Test
    void evaluate_NoThreats_IsClear() {
        PolicyVerdict verdict = ExplosionAvoidance.evaluate(SELF, List.of(), openRoom());

        assertEquals(new PolicyVerdict.Clear(), verdict, "Без угроз рефлекса нет");
    }
}

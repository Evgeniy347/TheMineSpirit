package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class OutsideGateTests {
    private static OutsideGate gate() {
        return new OutsideGate(OutsideRules.defaults(), 32);
    }

    @Test
    void admitExternal_LoneSpirit_Refused() {
        assertInstanceOf(
                OutsideGate.Refused.class,
                gate().admitExternal(1, false),
                "Внешняя работа в одиночку не назначается, даже когда цель ещё не известна");
    }

    @Test
    void admitExternal_EveningLoneSpirit_RefusedWithNightReason() {
        OutsideGate.Decision decision = gate().admitExternal(1, false, true);

        OutsideGate.Refused refused = assertInstanceOf(
                OutsideGate.Refused.class,
                decision,
                "Вечером и ночью внешняя работа не назначается: иначе дух уйдёт за границу навстречу мобам");
        assertEquals(
                OutsideGate.NIGHT_REASON,
                refused.reason(),
                "Причина отказа обязана называть ночь: по ней строится журнал решений поселения");
    }

    @Test
    void admitExternal_EveningPartyWithPlayer_StillRefused() {
        assertInstanceOf(
                OutsideGate.Refused.class,
                gate().admitExternal(4, true, true),
                "Ночью внешняя работа не назначается даже группой или при игроке: единственное исключение — "
                        + "следование за игроком, а не работа рядом с ним");
    }

    @Test
    void admitExternal_TargetInsideSettlement_InsideWithBoundaryNote() {
        OutsideGate.Inside inside = assertInstanceOf(
                OutsideGate.Inside.class,
                gate().admitExternal(1, false, false, new Point(-189, 63, -518), new Point(-181, 73, -520)),
                "Шахта поселения рядом с домом лежит внутри границы: одиночка получает работу без выхода наружу");

        assertTrue(
                inside.note().contains("радиус 32"),
                "Запись обязана называть проверенную границу с радиусом, фактически: " + inside.note());
        assertTrue(
                inside.note().contains("-189.0 63.0 -518.0") && inside.note().contains("8.2"),
                "Запись обязана называть цель и расстояние до дома, фактически: " + inside.note());
    }

    @Test
    void admitExternal_TargetAtSettlementRadius_Inside() {
        assertInstanceOf(
                OutsideGate.Inside.class,
                gate().admitExternal(1, false, false, new Point(32, 64, 0), new Point(0, 64, 0)),
                "Точка ровно на границе радиуса — ещё своя: за границей считается только расстояние больше радиуса");
    }

    @Test
    void admitExternal_TargetBeyondSettlementRadius_RefusedWithBoundary() {
        OutsideGate.Refused refused = assertInstanceOf(
                OutsideGate.Refused.class,
                gate().admitExternal(1, false, false, new Point(33, 64, 0), new Point(0, 64, 0)),
                "Точка за радиусом — внешняя: в одиночку туда не назначают");

        assertTrue(
                refused.reason().contains("радиус 32")
                        && refused.reason().contains("33.0 64.0 0.0")
                        && refused.reason().contains("цель за границей"),
                "Отказ обязан называть границу, цель и её положение, фактически: " + refused.reason());
    }

    @Test
    void admitExternal_UnknownTargetLoneSpirit_RefusalNamesBoundary() {
        OutsideGate.Refused refused = assertInstanceOf(
                OutsideGate.Refused.class,
                gate().admitExternal(1, false, false, null, null),
                "Без цели и дома границу поселения проверить нечем: работа считается внешней");

        assertTrue(
                refused.reason().contains("радиус 32") && refused.reason().contains("цель неизвестна"),
                "Отказ обязан объяснять, какую границу проверяли и почему цель внешняя, фактически: "
                        + refused.reason());
    }

    @Test
    void admitExternal_UnknownTargetEvening_RefusalNamesNightAndBoundary() {
        OutsideGate.Refused refused = assertInstanceOf(
                OutsideGate.Refused.class,
                gate().admitExternal(4, true, true, null, null),
                "Ночью внешняя работа с неизвестной целью не назначается даже группой");

        assertTrue(
                refused.reason().startsWith(OutsideGate.NIGHT_REASON)
                        && refused.reason().contains("радиус 32"),
                "Отказ обязан называть ночь и проверенную границу, фактически: " + refused.reason());
    }

    @Property(tries = 50)
    void admitExternal_EveningParty_IsNeverAllowed(@ForAll("parties") int party, @ForAll boolean player) {
        assertInstanceOf(
                OutsideGate.Refused.class,
                gate().admitExternal(party, player, true),
                "Вечером внешняя работа отвергается при любом составе и любом сопровождении: ночь — жёсткая граница");
    }

    @Property(tries = 100)
    void admitExternal_AnyPartyAtThreshold_Allowed(@ForAll("parties") int party) {
        assertInstanceOf(
                OutsideGate.Allowed.class,
                gate().admitExternal(Math.max(party, 2), false),
                "Любая группа от порога допускается наружу независимо от случайного состава");
    }

    @Provide
    Arbitrary<Integer> parties() {
        return Arbitraries.integers().between(2, 10);
    }
}

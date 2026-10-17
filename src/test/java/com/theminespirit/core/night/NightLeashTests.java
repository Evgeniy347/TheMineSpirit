package com.theminespirit.core.night;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.doors.DoorTuning;
import com.theminespirit.core.doors.DoorTuning.DoorRules;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class NightLeashTests {
    private static final BlockCoord HOME = new BlockCoord(0, 64, 0);
    private static final int RADIUS = 32;

    private final NightLeash leash = new NightLeash(new DoorRules(0.5, 60, 2.0, RADIUS, 100));

    @Test
    void checkTarget_DaytimeOutsideRadius_IsAllowed() {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(100, 64, 0), HOME, RADIUS, false, true, false);

        assertInstanceOf(NightLeash.Allowed.class, decision,
                "Днём граница поселения не держит: иначе дух не выйдет на дневную работу за его пределами");
    }

    @Test
    void checkTarget_NightInsideSinceEveningOutsideRadius_IsRefusedWithReason() {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(100, 64, 0), HOME, RADIUS, true, true, false);

        NightLeash.Refused refused = assertInstanceOf(NightLeash.Refused.class, decision,
                "Ночью дух, заставший вечер внутри, не выходит за границу поселения: иначе он уходит в темноту "
                        + "навстречу мобам вопреки решению владельца");
        assertEquals(NightLeash.OUTSIDE_SETTLEMENT_REASON, refused.reason(),
                "Причина отказа обязана называть ночь и границу поселения: по ней строится итог задачи");
    }

    @Test
    void checkTarget_NightInsideSinceEveningAtRadius_IsAllowed() {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(RADIUS, 64, 0), HOME, RADIUS, true, true, false);

        assertInstanceOf(NightLeash.Allowed.class, decision,
                "Точка ровно на границе поселения — ещё внутри: иначе дух не дойдёт до стены своего дома");
    }

    @Test
    void checkTarget_NightFollowingPlayerOutsideRadius_IsAllowed() {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(100, 64, 0), HOME, RADIUS, true, true, true);

        assertInstanceOf(NightLeash.Allowed.class, decision,
                "Следование за игроком — единственное исключение: иначе дух бросит игрока, ушедшего в темноту");
    }

    @Test
    void checkTarget_NightOutsideSinceEveningOutsideRadius_IsAllowed() {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(100, 64, 0), HOME, RADIUS, true, false, false);

        assertInstanceOf(NightLeash.Allowed.class, decision,
                "Кого вечер застал в походе, тот ночует вне поселения: поводок его не держит у чужой границы");
    }

    @Test
    void checkTarget_NightWithoutHome_IsAllowed() {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(100, 64, 0), null, RADIUS, true, true, false);

        assertInstanceOf(NightLeash.Allowed.class, decision,
                "Без дома нет поселения: иначе дух без дома застыл бы ночью на месте");
    }

    @Test
    void checkTarget_NightInvalidRadius_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> leash.checkTarget(new BlockCoord(1, 64, 0), HOME, 0, true, true, false),
                "Радиус меньше 1 запретил бы любое движение молча: отказ обязан быть явной ошибкой");
    }

    @Test
    void isEvening_DayFractionAtThreshold_IsEvening() {
        assertTrue(leash.isEvening(0.5),
                "Доля суток на пороге — уже вечер: граница обязана сработать на этом же тике");
    }

    @Test
    void isEvening_DayFractionBelowThreshold_IsDay() {
        assertFalse(leash.isEvening(0.49),
                "День до порога — не вечер: иначе дух запирается задолго до заката");
    }

    @Test
    void isEvening_DayFractionAboveOne_Throws() {
        assertThrows(IllegalArgumentException.class, () -> leash.isEvening(1.5),
                "Доля суток вне 0..1 означала бы непонятное время дня: ошибка обязана быть явной");
    }

    @Test
    void insideSettlement_ExactlyAtRadius_IsInside() {
        assertTrue(leash.insideSettlement(new BlockCoord(RADIUS, 64, 0), HOME, RADIUS),
                "Точка ровно на радиусе — внутри поселения: иначе дом на границе не считался бы своим");
    }

    @Test
    void insideSettlement_BeyondRadius_IsOutside() {
        assertFalse(leash.insideSettlement(new BlockCoord(RADIUS + 1, 64, 0), HOME, RADIUS),
                "Точка за радиусом — вне поселения: иначе граница не отличала бы своих от чужих");
    }

    @Test
    void insideSettlement_DeepBelowHomeWithinRadius_IsInside() {
        assertTrue(leash.insideSettlement(new BlockCoord(0, HOME.y() - RADIUS - 40, 0), HOME, RADIUS),
                "Шахта поселения — часть территории: ствол строго под домом остаётся своим, иначе ночной поводок "
                        + "запер бы шахтёра на глубине");
    }

    @Test
    void insideSettlement_BelowHomeBeyondHorizontalRadius_IsOutside() {
        assertFalse(leash.insideSettlement(new BlockCoord(RADIUS + 1, HOME.y() - 100, 0), HOME, RADIUS),
                "Глубина не расширяет границу вширь: штольня в стороне за радиусом — уже вне поселения");
    }

    @Test
    void armedNext_DayInside_Disarms() {
        assertFalse(leash.armedNext(false, true, true),
                "Днём поводок снят: иначе дух, переживший ночь, не выйдет на работу утром");
    }

    @Test
    void armedNext_EveningInside_Arms() {
        assertTrue(leash.armedNext(true, false, true),
                "Вечером внутри поселения поводок взводится: с этого момента дух не выходит за границу");
    }

    @Test
    void armedNext_EveningAlreadyArmed_StaysArmed() {
        assertTrue(leash.armedNext(true, true, false),
                "Дух, взведённый вечером, остаётся под поводком и ночью вне поселения: исключение — только следование");
    }

    @Test
    void armedNext_EveningOutsideNotArmed_StaysOff() {
        assertFalse(leash.armedNext(true, false, false),
                "Дух, застигнутый вечером в походе, под поводок не попадает: иначе он не мог бы заночевать в лагере");
    }

    @Test
    void settlementRadius_DefaultRules_MatchesDoorsTable() {
        int radius = NightLeash.withDefaultRules().settlementRadius();

        assertEquals(32, radius,
                "Радиус поселения — данные дверей (doors.json → settlementRadius): смена числа здесь развела бы "
                        + "границу дверей и ночной поводок");
        assertEquals(DoorTuning.loadDefault().settlementRadius(), radius,
                "Ночной поводок обязан читать тот же радиус, что и двери: иначе у поселения две разные границы");
    }
}

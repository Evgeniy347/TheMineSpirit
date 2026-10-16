package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApplicabilityTests {

    private static final EatNeed HUNGRY = new EatNeed(10, 20, 20, 20);
    private static final EatNeed HURT = new EatNeed(20, 5, 20, 20);
    private static final EatNeed FULL = new EatNeed(20, 20, 20, 20);
    private static final SpiritView FREE = SpiritView.free();

    @Test
    void eat_HungrySpirit_Applicable() {
        assertTrue(Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, FREE),
                "Голодный дух берёт EAT_TO_FULL: сытость ниже предела, дух свободен — ест");
    }

    @Test
    void eat_HurtSpirit_Applicable() {
        assertTrue(Applicability.applicable(BoardCase.EAT_TO_FULL, HURT, FREE),
                "Раненый дух берёт EAT_TO_FULL: здоровье ниже предела, еда лечит лучше ожидания");
    }

    @Test
    void eat_FullSpirit_NotApplicable() {
        assertFalse(Applicability.applicable(BoardCase.EAT_TO_FULL, FULL, FREE),
                "Сытый и здоровый дух не берёт EAT_TO_FULL: задание не должно висеть на доске "
                        + "ради уже сытого духа");
    }

    @Test
    void eat_InCombat_NotApplicable() {
        SpiritView fighting = new SpiritView(true, false, false);

        assertFalse(Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, fighting),
                "Дух в бою не ест: еда под огнём не поднимает сытость, а отвлекает от боя");
    }

    @Test
    void eat_OnExpedition_NotApplicable() {
        SpiritView away = new SpiritView(false, true, false);

        assertFalse(Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, away),
                "Дух в походе не берёт досковое задание: поход важнее еды по расписанию");
    }

    @Test
    void eat_BoardBusy_NotApplicable() {
        SpiritView busy = new SpiritView(false, false, true);

        assertFalse(Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, busy),
                "Дух, занятый досковым заданием, не берёт второе: один дух — одно задание");
    }

    @Test
    void restock_BelowThreshold_Applicable() {
        assertTrue(Applicability.applicable(BoardCase.RESTOCK_FOOD, new EatNeed(16, 20, 20, 20), FREE),
                "Личной еды 16 меньше половины стака (32) — дух идёт пополнять запас");
    }

    @Test
    void restock_AtThreshold_NotApplicable() {
        assertFalse(Applicability.applicable(BoardCase.RESTOCK_FOOD, new EatNeed(32, 20, 20, 20), FREE),
                "Личной еды ровно половина стака — пополнять не нужно: порог Reached, а не перейдён");
    }

    @Test
    void otherPermanent_AnyCase_Applicable() {
        for (BoardCase boardCase : EvergreenRules.permanentCases()) {
            if (boardCase == BoardCase.EAT_TO_FULL || boardCase == BoardCase.RESTOCK_FOOD) {
                continue;
            }
            assertTrue(Applicability.applicable(boardCase, FULL, FREE),
                    "Постоянная проверка " + boardCase + " применима по гейту ресурсов: "
                            + "применимость к духу не отсекает её, иначе проверка молча исчезла бы");
        }
    }

    @Test
    void eat_RaisedFood_StaysInapplicable() {
        EatNeed justAte = new EatNeed(20, 20, 20, 20);

        assertFalse(Applicability.applicable(BoardCase.EAT_TO_FULL, justAte, FREE),
                "После еды до полной сытости задание не возвращается в выдачу: гистерезис держит "
                        + "его до complete, иначе источник переиздавал бы сытое задание");
    }

    @Test
    void applicable_NullArgs_AreRejected() {
        assertThrows(NullPointerException.class,
                () -> Applicability.applicable(null, HUNGRY, FREE),
                "Кейс null обязан отвергаться: без него неизвестно, чьё постоянное задание проверяем");
        assertThrows(NullPointerException.class,
                () -> Applicability.applicable(BoardCase.EAT_TO_FULL, null, FREE),
                "Сытость null обязана отвергаться: нужду считают по ней");
        assertThrows(NullPointerException.class,
                () -> Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, null),
                "Состояние null обязано отвергаться: применимость считают по нему");
    }
}

package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicabilityTests {

    private static final EatNeed HUNGRY = new EatNeed(10, 20, 20, 20);
    private static final EatNeed HURT = new EatNeed(20, 5, 20, 20);
    private static final EatNeed FULL = new EatNeed(20, 20, 20, 20);
    private static final SpiritView FREE = SpiritView.free();

    @Test
    void eat_HungrySpirit_Applicable() {
        assertTrue(
                Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, FREE),
                "Голодный дух берёт EAT_TO_FULL: сытость ниже предела, дух свободен — ест"
                        + " Иначе тест eat_HungrySpirit_Applicable перестанет ловить эту поломку. При смене eat_HungrySpirit_Applicable проверь это утверждение.");
    }

    @Test
    void eat_HurtSpirit_Applicable() {
        assertTrue(
                Applicability.applicable(BoardCase.EAT_TO_FULL, HURT, FREE),
                "Раненый дух берёт EAT_TO_FULL: здоровье ниже предела, еда лечит лучше ожидания"
                        + " Иначе тест eat_HurtSpirit_Applicable перестанет ловить эту поломку. При смене eat_HurtSpirit_Applicable проверь это утверждение.");
    }

    @Test
    void eat_FullSpirit_NotApplicable() {
        assertFalse(
                Applicability.applicable(BoardCase.EAT_TO_FULL, FULL, FREE),
                "Сытый и здоровый дух не берёт EAT_TO_FULL: задание не должно висеть на доске " + "ради уже сытого духа"
                        + " Иначе тест eat_FullSpirit_NotApplicable перестанет ловить эту поломку. При смене eat_FullSpirit_NotApplicable проверь это утверждение.");
    }

    @Test
    void eat_InCombat_NotApplicable() {
        SpiritView fighting = new SpiritView(true, false, false);

        assertFalse(
                Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, fighting),
                "Дух в бою не ест: еда под огнём не поднимает сытость, а отвлекает от боя"
                        + " Иначе тест eat_InCombat_NotApplicable перестанет ловить эту поломку. При смене eat_InCombat_NotApplicable проверь это утверждение.");
    }

    @Test
    void eat_OnExpedition_NotApplicable() {
        SpiritView away = new SpiritView(false, true, false);

        assertFalse(
                Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, away),
                "Дух в походе не берёт досковое задание: поход важнее еды по расписанию"
                        + " Иначе тест eat_OnExpedition_NotApplicable перестанет ловить эту поломку. При смене eat_OnExpedition_NotApplicable проверь это утверждение.");
    }

    @Test
    void eat_BoardBusy_NotApplicable() {
        SpiritView busy = new SpiritView(false, false, true);

        assertFalse(
                Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, busy),
                "Дух, занятый досковым заданием, не берёт второе: один дух — одно задание"
                        + " Иначе тест eat_BoardBusy_NotApplicable перестанет ловить эту поломку. При смене eat_BoardBusy_NotApplicable проверь это утверждение.");
    }

    @Test
    void restock_AnyHunger_ApplicableWhenFree() {
        for (int food : List.of(0, 16, 20)) {
            assertTrue(
                    Applicability.applicable(BoardCase.RESTOCK_FOOD, new EatNeed(food, 20, 20, 20), FREE),
                    "Нужду в пополнении еды решает граф (событие hungry) по личному запасу; применимость не сравнивает сытость "
                            + food + " (0..20) с порогом стака — такое сравнение всегда истинно"
                            + " Иначе тест restock_AnyHunger_ApplicableWhenFree перестанет ловить эту поломку. При смене restock_AnyHunger_ApplicableWhenFree проверь это утверждение.");
        }
    }

    @Test
    void otherPermanent_AnyCase_Applicable() {
        for (BoardCase boardCase : List.of(
                BoardCase.REPAIR_FENCE, BoardCase.REPAIR_BUILDING, BoardCase.FILL_CRATER, BoardCase.ENCHANT_SHELVES)) {
            assertTrue(
                    Applicability.applicable(boardCase, FULL, FREE),
                    "Постоянная проверка " + boardCase + " применима по гейту ресурсов: "
                            + "применимость к духу не отсекает её, иначе проверка молча исчезла бы"
                            + " При смене otherPermanent_AnyCase_Applicable проверь это утверждение.");
        }
    }

    @Test
    void eat_RaisedFood_StaysInapplicable() {
        EatNeed justAte = new EatNeed(20, 20, 20, 20);

        assertFalse(
                Applicability.applicable(BoardCase.EAT_TO_FULL, justAte, FREE),
                "После еды до полной сытости задание не возвращается в выдачу: гистерезис держит "
                        + "его до complete, иначе источник переиздавал бы сытое задание"
                        + " При смене eat_RaisedFood_StaysInapplicable проверь это утверждение.");
    }

    @Test
    void applicable_NullArgs_AreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> Applicability.applicable(null, HUNGRY, FREE),
                "Кейс null обязан отвергаться: без него неизвестно, чьё постоянное задание проверяем"
                        + " Иначе тест applicable_NullArgs_AreRejected перестанет ловить эту поломку. При смене applicable_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> Applicability.applicable(BoardCase.EAT_TO_FULL, null, FREE),
                "Сытость null обязана отвергаться: нужду считают по ней"
                        + " Иначе тест applicable_NullArgs_AreRejected перестанет ловить эту поломку. При смене applicable_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> Applicability.applicable(BoardCase.EAT_TO_FULL, HUNGRY, null),
                "Состояние null обязано отвергаться: применимость считают по нему"
                        + " Иначе тест applicable_NullArgs_AreRejected перестанет ловить эту поломку. При смене applicable_NullArgs_AreRejected проверь это утверждение.");
    }
}

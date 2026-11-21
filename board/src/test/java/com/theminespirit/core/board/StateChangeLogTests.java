package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StateChangeLogTests {

    @Test
    void changed_SameStateTwice_SecondIsSuppressed() {
        StateChangeLog log = new StateChangeLog();

        assertTrue(
                log.changed("storage", "refused"),
                "первое состояние канала пишется: без него причина не видна"
                        + " Иначе тест changed_SameStateTwice_SecondIsSuppressed перестанет ловить эту поломку. При смене changed_SameStateTwice_SecondIsSuppressed проверь это утверждение.");
        assertFalse(
                log.changed("storage", "refused"),
                "[storage] пишется только при смене состояния: та же причина каждые 5 с — шум, а не новость"
                        + " Иначе тест changed_SameStateTwice_SecondIsSuppressed перестанет ловить эту поломку. При смене changed_SameStateTwice_SecondIsSuppressed проверь это утверждение.");
    }

    @Test
    void changed_DifferentState_IsReported() {
        StateChangeLog log = new StateChangeLog();
        log.changed("storage", "refused");

        assertTrue(
                log.changed("storage", "chosen@-171,71,-487"),
                "смена «некуда положить» на выбранный сундук — новое состояние, о нём надо написать"
                        + " Иначе тест changed_DifferentState_IsReported перестанет ловить эту поломку. При смене changed_DifferentState_IsReported проверь это утверждение.");
    }

    @Test
    void changed_OtherChannel_DoesNotSuppressFirstState() {
        StateChangeLog log = new StateChangeLog();
        log.changed("storage:oak_planks", "refused");

        assertTrue(
                log.changed("food:apple", "refused"),
                "каналы [storage] и [food] независимы: причина еды не глушится причиной сдачи"
                        + " Иначе тест changed_OtherChannel_DoesNotSuppressFirstState перестанет ловить эту поломку. При смене changed_OtherChannel_DoesNotSuppressFirstState проверь это утверждение.");
    }

    @Test
    void forget_NextSameState_IsReportedAgain() {
        StateChangeLog log = new StateChangeLog();
        log.changed("storage", "refused");
        log.forget("storage");

        assertTrue(
                log.changed("storage", "refused"),
                "после сброса канала прежнее состояние снова новое"
                        + " Иначе тест forget_NextSameState_IsReportedAgain перестанет ловить эту поломку. При смене forget_NextSameState_IsReportedAgain проверь это утверждение.");
    }
}

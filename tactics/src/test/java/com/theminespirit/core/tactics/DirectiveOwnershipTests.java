package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DirectiveOwnershipTests {

    @Test
    void clear_CoordinatorAfterScript_Refused() {
        DirectiveOwnership ownership = new DirectiveOwnership();
        ownership.appliedBy(DirectiveOwner.SCRIPT);

        assertFalse(
                ownership.mayClear(DirectiveOwner.COORDINATOR),
                "координатор не снимает директиву теста, игрока или скрипта: иначе его уборка "
                        + "приказа стирает PASSIVE и снимает чужую задачу"
                        + " При смене clear_CoordinatorAfterScript_Refused проверь это утверждение.");
        assertTrue(
                ownership.mayClear(DirectiveOwner.SCRIPT),
                "владелец снимает свою директиву: иначе скрипт не вернёт тактику после работы"
                        + " При смене clear_CoordinatorAfterScript_Refused проверь это утверждение.");
    }

    @Test
    void clear_CoordinatorAfterOwnDirective_Allowed() {
        DirectiveOwnership ownership = new DirectiveOwnership();
        ownership.appliedBy(DirectiveOwner.COORDINATOR);

        assertTrue(
                ownership.mayClear(DirectiveOwner.COORDINATOR),
                "координатор снимает только свой приказ: уборка после боя не должна зависеть от чужого"
                        + " Иначе тест clear_CoordinatorAfterOwnDirective_Allowed перестанет ловить эту поломку. При смене clear_CoordinatorAfterOwnDirective_Allowed проверь это утверждение.");
        assertFalse(
                ownership.mayClear(DirectiveOwner.SCRIPT),
                "скрипт не снимает директиву координатора: владелец у директивы один"
                        + " Иначе тест clear_CoordinatorAfterOwnDirective_Allowed перестанет ловить эту поломку. При смене clear_CoordinatorAfterOwnDirective_Allowed проверь это утверждение.");
    }

    @Test
    void clear_AfterOwnerReleased_RefusedForEveryone() {
        DirectiveOwnership ownership = new DirectiveOwnership();
        ownership.appliedBy(DirectiveOwner.COORDINATOR);
        ownership.cleared();

        assertFalse(
                ownership.mayClear(DirectiveOwner.COORDINATOR),
                "освобождённую директиву повторно не снимают: иначе ложное «снял» сбивает учёт владельца"
                        + " При смене clear_AfterOwnerReleased_RefusedForEveryone проверь это утверждение.");
        assertFalse(
                ownership.mayClear(DirectiveOwner.SCRIPT),
                "освобождённую директиву скрипт не снимает: владельца уже нет"
                        + " Иначе тест clear_AfterOwnerReleased_RefusedForEveryone перестанет ловить эту поломку. При смене clear_AfterOwnerReleased_RefusedForEveryone проверь это утверждение.");
    }

    @Test
    void appliedBy_NullOwner_Rejected() {
        DirectiveOwnership ownership = new DirectiveOwnership();

        assertThrows(
                NullPointerException.class,
                () -> ownership.appliedBy(null),
                "директива без владельца не принимается: её снял бы первый встречный"
                        + " Иначе тест appliedBy_NullOwner_Rejected перестанет ловить эту поломку. При смене appliedBy_NullOwner_Rejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> ownership.mayClear(null),
                "снятие без владельца не проверить: кто снимает — часть правила"
                        + " Иначе тест appliedBy_NullOwner_Rejected перестанет ловить эту поломку. При смене appliedBy_NullOwner_Rejected проверь это утверждение.");
    }
}

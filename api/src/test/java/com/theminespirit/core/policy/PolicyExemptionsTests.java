package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.policy.api.PolicyExemptions;
import com.theminespirit.core.policy.api.PolicyId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyExemptionsTests {
    @Test
    void empty_EmptyPolicy_NoExemptions() {
        PolicyExemptions ex = PolicyExemptions.empty();

        assertFalse(
                ex.has(PolicyId.WATER),
                "пустые исключения не содержат WATER"
                        + " Иначе тест empty_EmptyPolicy_NoExemptions перестанет ловить эту поломку. При смене empty_EmptyPolicy_NoExemptions проверь это утверждение.");
        assertFalse(
                ex.has(PolicyId.PORTAL),
                "пустые исключения не содержат PORTAL"
                        + " Иначе тест empty_EmptyPolicy_NoExemptions перестанет ловить эту поломку. При смене empty_EmptyPolicy_NoExemptions проверь это утверждение.");
        assertTrue(
                ex.all().isEmpty(),
                "пустые исключения — пустое множество"
                        + " Иначе тест empty_EmptyPolicy_NoExemptions перестанет ловить эту поломку. При смене empty_EmptyPolicy_NoExemptions проверь это утверждение.");
    }

    @Test
    void of_AllowedPolicy_HasExemption() {
        PolicyExemptions ex = PolicyExemptions.of(PolicyId.WATER, PolicyId.PORTAL);

        assertTrue(
                ex.has(PolicyId.WATER),
                "WATER в исключениях"
                        + " Иначе тест of_AllowedPolicy_HasExemption перестанет ловить эту поломку. При смене of_AllowedPolicy_HasExemption проверь это утверждение.");
        assertTrue(
                ex.has(PolicyId.PORTAL),
                "PORTAL в исключениях"
                        + " Иначе тест of_AllowedPolicy_HasExemption перестанет ловить эту поломку. При смене of_AllowedPolicy_HasExemption проверь это утверждение.");
        assertFalse(
                ex.has(PolicyId.DROP),
                "DROP не в исключениях"
                        + " Иначе тест of_AllowedPolicy_HasExemption перестанет ловить эту поломку. При смене of_AllowedPolicy_HasExemption проверь это утверждение.");
        assertEquals(
                2,
                ex.all().size(),
                "два исключения"
                        + " Иначе тест of_AllowedPolicy_HasExemption перестанет ловить эту поломку. При смене of_AllowedPolicy_HasExemption проверь это утверждение.");
    }

    @Test
    void of_LavaFire_ThrowsException() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.LAVA_FIRE),
                "LAVA_FIRE не может иметь исключение — защищает жизнь духа"
                        + " Иначе тест of_LavaFire_ThrowsException перестанет ловить эту поломку. При смене of_LavaFire_ThrowsException проверь это утверждение.");

        assertTrue(
                e.getMessage().contains("LAVA_FIRE"),
                "Ошибка обязана называть запрещённую политику. Фактическое: " + e.getMessage()
                        + " Иначе тест of_LavaFire_ThrowsException перестанет ловить эту поломку. При смене of_LavaFire_ThrowsException проверь это утверждение.");
    }

    @Test
    void of_Air_ThrowsException() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.AIR),
                "AIR не может иметь исключение — защищает жизнь духа"
                        + " Иначе тест of_Air_ThrowsException перестанет ловить эту поломку. При смене of_Air_ThrowsException проверь это утверждение.");

        assertTrue(
                e.getMessage().contains("AIR"),
                "Ошибка обязана называть AIR. Фактическое: " + e.getMessage()
                        + " Иначе тест of_Air_ThrowsException перестанет ловить эту поломку. При смене of_Air_ThrowsException проверь это утверждение.");
    }

    @Test
    void of_Explosive_ThrowsException() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.EXPLOSIVE),
                "EXPLOSIVE не может иметь исключение — защищает жизнь духа"
                        + " Иначе тест of_Explosive_ThrowsException перестанет ловить эту поломку. При смене of_Explosive_ThrowsException проверь это утверждение.");

        assertTrue(
                e.getMessage().contains("EXPLOSIVE"),
                "Ошибка обязана называть EXPLOSIVE. Фактическое: " + e.getMessage()
                        + " Иначе тест of_Explosive_ThrowsException перестанет ловить эту поломку. При смене of_Explosive_ThrowsException проверь это утверждение.");
    }

    @Test
    void of_MixedAllowedAndForbidden_ThrowsException() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.WATER, PolicyId.AIR),
                "смесь разрешённой и запрещённой политики обязана падать"
                        + " Иначе тест of_MixedAllowedAndForbidden_ThrowsException перестанет ловить эту поломку. При смене of_MixedAllowedAndForbidden_ThrowsException проверь это утверждение.");

        assertTrue(
                e.getMessage().contains("AIR"),
                "При смеси разрешённой и запрещённой — ошибка о запрещённой. Фактическое: " + e.getMessage()
                        + " Иначе тест of_MixedAllowedAndForbidden_ThrowsException перестанет ловить эту поломку. При смене of_MixedAllowedAndForbidden_ThrowsException проверь это утверждение.");
    }

    @Test
    void equals_SameExemptions_AreEqual() {
        PolicyExemptions ex1 = PolicyExemptions.of(PolicyId.WATER, PolicyId.PORTAL);
        PolicyExemptions ex2 = PolicyExemptions.of(PolicyId.PORTAL, PolicyId.WATER);

        assertEquals(
                ex1,
                ex2,
                "Порядок не важен: PolicyExemptions — множество"
                        + " Иначе тест equals_SameExemptions_AreEqual перестанет ловить эту поломку. При смене equals_SameExemptions_AreEqual проверь это утверждение.");
    }

    @Test
    void equals_DifferentExemptions_AreNotEqual() {
        PolicyExemptions ex1 = PolicyExemptions.of(PolicyId.WATER);
        PolicyExemptions ex2 = PolicyExemptions.of(PolicyId.PORTAL);

        assertFalse(
                ex1.equals(ex2),
                "Разные исключения не равны"
                        + " Иначе тест equals_DifferentExemptions_AreNotEqual перестанет ловить эту поломку. При смене equals_DifferentExemptions_AreNotEqual проверь это утверждение.");
    }

    @Test
    void of_NoArguments_EqualsEmpty() {
        PolicyExemptions none = PolicyExemptions.of();

        assertEquals(
                PolicyExemptions.empty(),
                none,
                "of() без аргументов — те же пустые исключения, что и empty(); скрипт без исключений не должен падать при создании"
                        + " Иначе тест of_NoArguments_EqualsEmpty перестанет ловить эту поломку. При смене of_NoArguments_EqualsEmpty проверь это утверждение.");
        assertTrue(
                none.all().isEmpty(),
                "of() без аргументов не добавляет исключений"
                        + " Иначе тест of_NoArguments_EqualsEmpty перестанет ловить эту поломку. При смене of_NoArguments_EqualsEmpty проверь это утверждение.");
    }

    @Test
    void of_DuplicateIds_CollapsesToOne() {
        PolicyExemptions ex = PolicyExemptions.of(PolicyId.WATER, PolicyId.WATER);

        assertEquals(
                Set.of(PolicyId.WATER),
                ex.all(),
                "повтор политики в списке исключений — одно исключение, а не ошибка создания"
                        + " Иначе тест of_DuplicateIds_CollapsesToOne перестанет ловить эту поломку. При смене of_DuplicateIds_CollapsesToOne проверь это утверждение.");
    }

    @Test
    void of_NullId_ThrowsNullPointer() {
        assertThrows(
                NullPointerException.class,
                () -> PolicyExemptions.of(PolicyId.WATER, null),
                "null в списке исключений — ошибка вызывающего, а не тихий пропуск"
                        + " Иначе тест of_NullId_ThrowsNullPointer перестанет ловить эту поломку. При смене of_NullId_ThrowsNullPointer проверь это утверждение.");
    }

    @Test
    void all_ReturnedSet_IsImmutable() {
        PolicyExemptions ex = PolicyExemptions.of(PolicyId.WATER);

        assertThrows(
                UnsupportedOperationException.class,
                () -> ex.all().add(PolicyId.AIR),
                "изменение набора снаружи обошло бы запрет исключений для LAVA_FIRE, AIR и EXPLOSIVE"
                        + " Иначе тест all_ReturnedSet_IsImmutable перестанет ловить эту поломку. При смене all_ReturnedSet_IsImmutable проверь это утверждение.");
    }

    @Test
    void union_TwoSets_ContainsBoth() {
        PolicyExemptions merged = PolicyExemptions.of(PolicyId.WATER).union(PolicyExemptions.of(PolicyId.PORTAL));

        assertEquals(
                Set.of(PolicyId.WATER, PolicyId.PORTAL),
                merged.all(),
                "Объединение параллельных скриптов даёт оба исключения"
                        + " Иначе тест union_TwoSets_ContainsBoth перестанет ловить эту поломку. При смене union_TwoSets_ContainsBoth проверь это утверждение.");
    }

    @Test
    void union_WithEmpty_ReturnsTheSameSet() {
        PolicyExemptions water = PolicyExemptions.of(PolicyId.WATER);

        assertEquals(
                water,
                water.union(PolicyExemptions.empty()),
                "Пустое исключение ничего не добавляет"
                        + " Иначе тест union_WithEmpty_ReturnsTheSameSet перестанет ловить эту поломку. При смене union_WithEmpty_ReturnsTheSameSet проверь это утверждение.");
        assertEquals(
                water,
                PolicyExemptions.empty().union(water),
                "К пустому исключению добавляется всё"
                        + " Иначе тест union_WithEmpty_ReturnsTheSameSet перестанет ловить эту поломку. При смене union_WithEmpty_ReturnsTheSameSet проверь это утверждение.");
    }
}

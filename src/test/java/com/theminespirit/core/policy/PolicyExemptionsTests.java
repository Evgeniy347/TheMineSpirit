package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyExemptionsTests {
    @Test
    void empty_EmptyPolicy_NoExemptions() {
        PolicyExemptions ex = PolicyExemptions.empty();

        assertFalse(ex.has(PolicyId.WATER), "пустые исключения не содержат WATER");
        assertFalse(ex.has(PolicyId.PORTAL), "пустые исключения не содержат PORTAL");
        assertTrue(ex.all().isEmpty(), "пустые исключения — пустое множество");
    }

    @Test
    void of_AllowedPolicy_HasExemption() {
        PolicyExemptions ex = PolicyExemptions.of(PolicyId.WATER, PolicyId.PORTAL);

        assertTrue(ex.has(PolicyId.WATER), "WATER в исключениях");
        assertTrue(ex.has(PolicyId.PORTAL), "PORTAL в исключениях");
        assertFalse(ex.has(PolicyId.DROP), "DROP не в исключениях");
        assertEquals(2, ex.all().size(), "два исключения");
    }

    @Test
    void of_LavaFire_ThrowsException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.LAVA_FIRE),
                "LAVA_FIRE не может иметь исключение — защищает жизнь духа");

        assertTrue(e.getMessage().contains("LAVA_FIRE"),
                "Ошибка обязана называть запрещённую политику. Фактическое: " + e.getMessage());
    }

    @Test
    void of_Air_ThrowsException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.AIR),
                "AIR не может иметь исключение — защищает жизнь духа");

        assertTrue(e.getMessage().contains("AIR"), "Ошибка обязана называть AIR. Фактическое: " + e.getMessage());
    }

    @Test
    void of_Explosive_ThrowsException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.EXPLOSIVE),
                "EXPLOSIVE не может иметь исключение — защищает жизнь духа");

        assertTrue(e.getMessage().contains("EXPLOSIVE"), "Ошибка обязана называть EXPLOSIVE. Фактическое: " + e.getMessage());
    }

    @Test
    void of_MixedAllowedAndForbidden_ThrowsException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PolicyExemptions.of(PolicyId.WATER, PolicyId.AIR),
                "смесь разрешённой и запрещённой политики обязана падать");

        assertTrue(e.getMessage().contains("AIR"),
                "При смеси разрешённой и запрещённой — ошибка о запрещённой. Фактическое: " + e.getMessage());
    }

    @Test
    void equals_SameExemptions_AreEqual() {
        PolicyExemptions ex1 = PolicyExemptions.of(PolicyId.WATER, PolicyId.PORTAL);
        PolicyExemptions ex2 = PolicyExemptions.of(PolicyId.PORTAL, PolicyId.WATER);

        assertEquals(ex1, ex2, "Порядок не важен: PolicyExemptions — множество");
    }

    @Test
    void equals_DifferentExemptions_AreNotEqual() {
        PolicyExemptions ex1 = PolicyExemptions.of(PolicyId.WATER);
        PolicyExemptions ex2 = PolicyExemptions.of(PolicyId.PORTAL);

        assertFalse(ex1.equals(ex2), "Разные исключения не равны");
    }

    @Test
    void of_NoArguments_EqualsEmpty() {
        PolicyExemptions none = PolicyExemptions.of();

        assertEquals(PolicyExemptions.empty(), none, "of() без аргументов — те же пустые исключения, что и empty(); скрипт без исключений не должен падать при создании");
        assertTrue(none.all().isEmpty(), "of() без аргументов не добавляет исключений");
    }

    @Test
    void of_DuplicateIds_CollapsesToOne() {
        PolicyExemptions ex = PolicyExemptions.of(PolicyId.WATER, PolicyId.WATER);

        assertEquals(Set.of(PolicyId.WATER), ex.all(), "повтор политики в списке исключений — одно исключение, а не ошибка создания");
    }

    @Test
    void of_NullId_ThrowsNullPointer() {
        assertThrows(NullPointerException.class, () -> PolicyExemptions.of(PolicyId.WATER, null),
                "null в списке исключений — ошибка вызывающего, а не тихий пропуск");
    }

    @Test
    void all_ReturnedSet_IsImmutable() {
        PolicyExemptions ex = PolicyExemptions.of(PolicyId.WATER);

        assertThrows(UnsupportedOperationException.class, () -> ex.all().add(PolicyId.AIR),
                "изменение набора снаружи обошло бы запрет исключений для LAVA_FIRE, AIR и EXPLOSIVE");
    }
}

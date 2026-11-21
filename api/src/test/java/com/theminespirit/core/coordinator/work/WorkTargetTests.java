package com.theminespirit.core.coordinator.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WorkTargetTests {
    private static final BlockCoord ANCHOR = new BlockCoord(12, 64, -8);

    @Test
    void target_ConstructorArgs_AreExposed() {
        WorkTarget vein = new WorkTarget(WorkKind.VEIN, ANCHOR);

        assertEquals(
                WorkKind.VEIN,
                vein.kind(),
                "Вид цели — клетка, дерево или жила: входит в ключ брони"
                        + " Иначе тест target_ConstructorArgs_AreExposed перестанет ловить эту поломку. При смене target_ConstructorArgs_AreExposed проверь это утверждение.");
        assertEquals(
                ANCHOR,
                vein.anchor(),
                "Якорь цели — где клетка, ствол или жила: входит в ключ брони"
                        + " Иначе тест target_ConstructorArgs_AreExposed перестанет ловить эту поломку. При смене target_ConstructorArgs_AreExposed проверь это утверждение.");
    }

    @Test
    void equals_SameFields_IsTrue() {
        assertEquals(
                new WorkTarget(WorkKind.TREE, ANCHOR),
                new WorkTarget(WorkKind.TREE, ANCHOR),
                "Цели с одинаковыми видом и якорем — одна и та же цель: бронь по ней общая"
                        + " Иначе тест equals_SameFields_IsTrue перестанет ловить эту поломку. При смене equals_SameFields_IsTrue проверь это утверждение.");
    }

    @Test
    void equals_DifferentAnchor_IsFalse() {
        assertNotEquals(
                new WorkTarget(WorkKind.TREE, ANCHOR),
                new WorkTarget(WorkKind.TREE, new BlockCoord(13, 64, -8)),
                "Цели с разными якорями — разные цели: бронь по одной не занимает другую"
                        + " Иначе тест equals_DifferentAnchor_IsFalse перестанет ловить эту поломку. При смене equals_DifferentAnchor_IsFalse проверь это утверждение.");
    }

    @Test
    void equals_DifferentKindSameAnchor_IsFalse() {
        assertNotEquals(
                new WorkTarget(WorkKind.CELL, ANCHOR),
                new WorkTarget(WorkKind.TREE, ANCHOR),
                "Клетка и дерево в одной точке — разные цели: стройка и рубка бронируются раздельно"
                        + " Иначе тест equals_DifferentKindSameAnchor_IsFalse перестанет ловить эту поломку. При смене equals_DifferentKindSameAnchor_IsFalse проверь это утверждение.");
    }

    @Test
    void hashCode_SameFields_IsEqual() {
        assertEquals(
                new WorkTarget(WorkKind.VEIN, ANCHOR).hashCode(),
                new WorkTarget(WorkKind.VEIN, ANCHOR).hashCode(),
                "Одинаковые цели дают одинаковый hash: иначе реестр броней их не сопоставит в Map"
                        + " При смене hashCode_SameFields_IsEqual проверь это утверждение.");
    }

    @Test
    void constructor_NullAnchor_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new WorkTarget(WorkKind.CELL, null),
                "Цель без якоря не опознает клетку, ствол или жилу: бронь стала бы безадресной"
                        + " Иначе тест constructor_NullAnchor_Throws перестанет ловить эту поломку. При смене constructor_NullAnchor_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("anchor"),
                "Сообщение называет поле anchor: без этого при падении не видно, что не так с целью"
                        + " При смене constructor_NullAnchor_Throws проверь это утверждение.");
    }

    @Test
    void constructor_NullKind_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkTarget(null, ANCHOR),
                "Цель без вида не говорит, клетка это, дерево или жила: бронь стала бы безвидовой"
                        + " Иначе тест constructor_NullKind_Throws перестанет ловить эту поломку. При смене constructor_NullKind_Throws проверь это утверждение.");
    }
}

package com.theminespirit.core.coordinator;

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

        assertEquals(WorkKind.VEIN, vein.kind(), "Вид цели — клетка, дерево или жила: входит в ключ брони");
        assertEquals(ANCHOR, vein.anchor(), "Якорь цели — где клетка, ствол или жила: входит в ключ брони");
    }

    @Test
    void equals_SameFields_IsTrue() {
        assertEquals(new WorkTarget(WorkKind.TREE, ANCHOR), new WorkTarget(WorkKind.TREE, ANCHOR),
                "Цели с одинаковыми видом и якорем — одна и та же цель: бронь по ней общая");
    }

    @Test
    void equals_DifferentAnchor_IsFalse() {
        assertNotEquals(new WorkTarget(WorkKind.TREE, ANCHOR), new WorkTarget(WorkKind.TREE, new BlockCoord(13, 64,
                -8)),
                "Цели с разными якорями — разные цели: бронь по одной не занимает другую");
    }

    @Test
    void equals_DifferentKindSameAnchor_IsFalse() {
        assertNotEquals(new WorkTarget(WorkKind.CELL, ANCHOR), new WorkTarget(WorkKind.TREE, ANCHOR),
                "Клетка и дерево в одной точке — разные цели: стройка и рубка бронируются раздельно");
    }

    @Test
    void hashCode_SameFields_IsEqual() {
        assertEquals(new WorkTarget(WorkKind.VEIN, ANCHOR).hashCode(), new WorkTarget(WorkKind.VEIN, ANCHOR)
                .hashCode(),
                "Одинаковые цели дают одинаковый hash: иначе реестр броней их не сопоставит в Map");
    }

    @Test
    void constructor_NullAnchor_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new WorkTarget(WorkKind.CELL, null),
                "Цель без якоря не опознает клетку, ствол или жилу: бронь стала бы безадресной");

        assertTrue(error.getMessage().contains("anchor"),
                "Сообщение называет поле anchor: без этого при падении не видно, что не так с целью");
    }

    @Test
    void constructor_NullKind_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new WorkTarget(null, ANCHOR),
                "Цель без вида не говорит, клетка это, дерево или жила: бронь стала бы безвидовой");
    }
}

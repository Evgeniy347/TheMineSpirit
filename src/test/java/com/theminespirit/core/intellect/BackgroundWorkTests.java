package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackgroundWorkTests {
    @Test
    void preemptible_ChopAndWait_True() {
        assertTrue(
                BackgroundWork.preemptible("chop_tree") && BackgroundWork.preemptible("wait"),
                "Рубка и отдых днём — фон: задание доски (инструменты, шахта) не ждёт их конца");
    }

    @Test
    void preemptible_Sleep_False() {
        assertFalse(BackgroundWork.preemptible("sleep"), "Сон ночью не вытесняется: иначе доска будит духа под мобов");
    }
}

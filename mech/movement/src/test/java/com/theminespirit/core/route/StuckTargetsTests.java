package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StuckTargetsTests {
    private static final UUID SPIRIT = new UUID(1, 1);
    private static final BlockCoord TARGET = new BlockCoord(-173, 69, -489);

    @Test
    void recent_NotedJustNow_IsTrue() {
        StuckTargets stuck = new StuckTargets();
        stuck.note(SPIRIT, TARGET, 100);
        assertTrue(
                stuck.recent(SPIRIT, TARGET, 130, 600),
                "Цель, провалившая пройденный шаг, в карантине: иначе дух получает тот же маршрут снова и "
                        + "топчется каждые 30 тиков (окно 55, spirit-5: 18 обрывов у -173,69,-489). При смене "
                        + "карантина проверь другую цель того же духа: она не должна блокироваться");
    }

    @Test
    void recent_Expired_IsFalse() {
        StuckTargets stuck = new StuckTargets();
        stuck.note(SPIRIT, TARGET, 100);
        assertFalse(
                stuck.recent(SPIRIT, TARGET, 800, 600),
                "По истечении срока цель снова доступна: иначе дух навсегда бросит точку, к которой путь "
                        + "появился после копания. При смене TTL проверь возврат к прежней точке: маршрут строится");
    }

    @Test
    void recent_OtherTarget_IsFalse() {
        StuckTargets stuck = new StuckTargets();
        stuck.note(SPIRIT, TARGET, 100);
        assertFalse(
                stuck.recent(SPIRIT, new BlockCoord(-174, 70, -488), 130, 600),
                "Карантин привязан к конкретной цели: иначе провал одного шага запретил бы духу все маршруты "
                        + "поселения. При смене ключа проверь живую задачу рядом: соседняя точка идёт");
    }

    @Test
    void recent_OtherSpirit_IsFalse() {
        StuckTargets stuck = new StuckTargets();
        stuck.note(SPIRIT, TARGET, 100);
        assertFalse(
                stuck.recent(new UUID(2, 2), TARGET, 130, 600),
                "Карантин личный для духа: иначе один застрявший дух запретил бы маршрут к точке всем "
                        + "остальным. При смене ключа проверь второго духа: его маршрут строится");
    }
}

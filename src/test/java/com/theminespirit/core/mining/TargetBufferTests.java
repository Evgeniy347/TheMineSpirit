package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetBufferTests {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 0, 0);

    @Test
    void offer_HundredFound_CapsActiveAndKeepsSurplus() {
        TargetBuffer buffer = new TargetBuffer();

        buffer.offer(line(100));

        assertEquals(TargetBuffer.CAPACITY, buffer.targets(ORIGIN).size(),
                "в работе обязано быть не больше " + TargetBuffer.CAPACITY + " цели: список плана ограничен, "
                        + "иначе планировщик читает сотни клеток за вызов");
        assertEquals(36, (buffer.size() - buffer.targets(ORIGIN).size()),
                "найденное сверх " + TargetBuffer.CAPACITY + " обязано ждать в избытке, а не пропадать: "
                        + "иначе дальние блоки не вернутся никогда и у края радиуса будет ложное «не осталось»");
        assertEquals(100, buffer.size(),
                "буфер обязан помнить все сто найденных целей вместе: ни одна не теряется при переполнении");
    }

    @Test
    void remove_WhileSurplusRemains_TopsActiveUpToCapacity() {
        TargetBuffer buffer = new TargetBuffer();
        buffer.offer(line(100));

        buffer.remove(buffer.targets(ORIGIN).get(0));

        assertEquals(TargetBuffer.CAPACITY, buffer.targets(ORIGIN).size(),
                "после одного изъятия рабочее множество обязано снова заполниться из избытка до "
                        + TargetBuffer.CAPACITY + ": простаивающая ёмкость замедляет работу на ровном месте");
        assertEquals(35, (buffer.size() - buffer.targets(ORIGIN).size()),
                "избыток обязан уменьшиться ровно на одну цель, отданную в работу");
        assertEquals(99, buffer.size(),
                "общее число целей обязано уменьшиться ровно на одну: изъятая цель не считается");
    }

    @Test
    void remove_DrainingActive_KeepsSurplusAvailable() {
        TargetBuffer buffer = new TargetBuffer();
        buffer.offer(line(100));

        for (int i = 0; i < TargetBuffer.CAPACITY; i++) {
            buffer.remove(buffer.targets(ORIGIN).get(0));
        }

        assertEquals(36, buffer.targets(ORIGIN).size(),
                "после разбора первых " + TargetBuffer.CAPACITY + " целей избыток обязан перейти в работу");
        assertEquals(0, (buffer.size() - buffer.targets(ORIGIN).size()),
                "после разбора " + TargetBuffer.CAPACITY + " из ста избытка остаться не должно");
        assertFalse(buffer.isEmpty(),
                "тридцать шесть целей обязаны остаться доступными: иначе дальние блоки потеряны навсегда");
    }

    @Test
    void needsRefill_SevenActive_IsTrue() {
        TargetBuffer buffer = new TargetBuffer();
        buffer.offer(line(7));

        assertTrue(buffer.needsRefill(),
                "при семи целях в работе (< " + TargetBuffer.REFILL_THRESHOLD + ") обязан требоваться досбор: "
                        + "иначе поиск не повторяется и правило «осталось меньше восьми — искать ещё» мёртвое");

        buffer.offer(line(1));

        assertFalse(buffer.needsRefill(),
                "при восьми целях в работе досбор не нужен: достаточно материала впереди");
    }

    @Test
    void targets_FromPosition_ReturnsNearestFirst() {
        TargetBuffer buffer = new TargetBuffer();
        buffer.offer(List.of(new BlockCoord(10, 0, 0), new BlockCoord(5, 0, 0), new BlockCoord(1, 0, 0)));

        List<BlockCoord> ordered = buffer.targets(ORIGIN);

        assertEquals(List.of(new BlockCoord(1, 0, 0), new BlockCoord(5, 0, 0), new BlockCoord(10, 0, 0)), ordered,
                "буфер обязан отдавать ближайшие цели первыми: планировщик выбирает стойку для дальней цели "
                        + "раньше, чем для ближней, и тратит чтения впустую");
    }

    private static List<BlockCoord> line(int count) {
        List<BlockCoord> coords = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            coords.add(new BlockCoord(100 + i, 0, 0));
        }
        return coords;
    }
}

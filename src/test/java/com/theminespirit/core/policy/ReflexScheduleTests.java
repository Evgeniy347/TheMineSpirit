package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReflexScheduleTests {
    @Test
    void tierTwoDue_AnyOffset_FiresOncePerTenTicks() {
        for (int offset = 0; offset < 10; offset++) {
            int fired = 0;
            for (long tick = 1000; tick < 1100; tick++) {
                if (ReflexSchedule.tierTwoDue(tick, offset)) {
                    fired++;
                }
            }
            assertEquals(
                    10,
                    fired,
                    "За 100 тиков ярус 2 срабатывает ровно 10 раз при смещении " + offset
                            + ": запрос сущностей раз в 10 тиков");
        }
    }

    @Test
    void tierOneDue_StandingStill_FiresOncePerTenTicks() {
        int fired = 0;
        for (long tick = 0; tick < 100; tick++) {
            if (ReflexSchedule.tierOneDue(tick, 3, false, false)) {
                fired++;
            }
        }

        assertEquals(10, fired, "Неподвижный дух без опасности читает снимок 27 клеток раз в 10 тиков, не чаще");
    }

    @Test
    void tierOneDue_BlockChanged_FiresEvenOffPhase() {
        assertTrue(
                ReflexSchedule.tierOneDue(7, 0, true, false),
                "Смена блока под ногами вызывает чтение вне очереди: шаг в лаву нельзя ждать 10 тиков");
        assertFalse(ReflexSchedule.tierOneDue(7, 0, false, false), "Без смены блока и вне фазы ярус 1 молчит");
    }

    @Test
    void tierOneDue_Escalated_FiresEvenOffPhase() {
        assertTrue(
                ReflexSchedule.tierOneDue(7, 0, false, true),
                "Тревога яруса 0 (горим, в лаве, ранен) вызывает чтение вне очереди");
    }

    @Test
    void tierTwoDue_HundredRandomSpirits_NeverMoreThanTwentyOnOneTick() {
        Random random = new Random(2026);
        int[] offsets = new int[100];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = ReflexSchedule.offsetOf(new UUID(random.nextLong(), random.nextLong()));
        }

        int busiest = 0;
        for (long tick = 0; tick < 10; tick++) {
            int due = 0;
            for (int offset : offsets) {
                if (ReflexSchedule.tierTwoDue(tick, offset)) {
                    due++;
                }
            }
            busiest = Math.max(busiest, due);
        }
        assertTrue(
                busiest <= 20,
                "Сто духов с разными UUID разнесены по тикам: на самом загруженном тике не больше 20, иначе запросы сущностей встанут в один тик. Было: "
                        + busiest + ", смещения: " + Arrays.toString(offsets));
    }

    @Test
    void offsetOf_Uuid_IsStableAndWithinPeriod() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertEquals(
                ReflexSchedule.offsetOf(id),
                ReflexSchedule.offsetOf(UUID.fromString(id.toString())),
                "Смещение зависит только от UUID: после перезагрузки дух сохраняет свою фазу");
        assertTrue(
                ReflexSchedule.offsetOf(id) >= 0 && ReflexSchedule.offsetOf(id) < 10,
                "Смещение лежит в пределах периода 10 тиков");
    }
}

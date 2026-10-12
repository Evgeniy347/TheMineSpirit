package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenBucketTests {
    private static final long SECOND = 1_000_000_000L;
    private static final String KEY = "10.0.0.1";

    @Test
    void tryAcquire_BurstOverCapacity_DeniesFortyFirstToken() {
        TokenBucket bucket = new TokenBucket();

        int granted = 0;
        for (int i = 0; i < 100; i++) {
            if (bucket.tryAcquire(KEY, 0)) {
                granted++;
            }
        }

        assertEquals(40, granted,
                "всплеск обязан выдать ровно ёмкость 40: больше мгновенных запросов лимитер не пропускает");
        assertEquals(1, bucket.trackedKeys(),
                "ключ обязан остаться в таблице для подсчёта пополнения");
    }

    @Test
    void tryAcquire_WithElapsedTime_RefillsAtTwentyPerSecond() {
        TokenBucket bucket = new TokenBucket();
        for (int i = 0; i < 40; i++) {
            bucket.tryAcquire(KEY, 0);
        }

        assertFalse(bucket.tryAcquire(KEY, 0), "пустое ведро отказывает сразу");
        assertFalse(bucket.tryAcquire(KEY, SECOND / 25),
                "за 0.04 с набегает 0.8 токена — до запроса ещё не хватает");
        assertTrue(bucket.tryAcquire(KEY, SECOND / 10),
                "за 0.1 с набегает 2 токена — запрос обязан пройти");
    }

    @Test
    void tryAcquire_AfterLongIdle_CapsAtCapacity() {
        TokenBucket bucket = new TokenBucket();
        for (int i = 0; i < 40; i++) {
            bucket.tryAcquire(KEY, 0);
        }

        int granted = 0;
        for (int i = 0; i < 40; i++) {
            if (bucket.tryAcquire(KEY, 10 * SECOND)) {
                granted++;
            }
        }

        assertEquals(40, granted,
                "после долгого простоя ведро не может держать больше ёмкости: выдача ограничена 40");
    }

    @Test
    void tryAcquire_DifferentKeys_AreIndependent() {
        TokenBucket bucket = new TokenBucket();
        String other = "10.0.0.2";
        for (int i = 0; i < 40; i++) {
            bucket.tryAcquire(KEY, 0);
        }

        assertFalse(bucket.tryAcquire(KEY, 0), "первый ключ пуст");
        assertTrue(bucket.tryAcquire(other, 0),
                "лимит обязан быть по адресу клиента: расход одного ключа не трогает другой");
    }

    @Test
    void tryAcquire_MoreThan256Keys_EvictsOldest() {
        TokenBucket bucket = new TokenBucket();

        for (int i = 0; i < 300; i++) {
            bucket.tryAcquire("ip-" + i, 0);
        }

        assertEquals(256, bucket.trackedKeys(),
                "таблица обязана вытеснять старейший ключ за пределами 256: иначе память растёт с числом адресов");
    }

    @Test
    void retryAfterSeconds_WhenStarved_IsAtLeastOneAndGrowsWithWait() {
        TokenBucket bucket = new TokenBucket();
        for (int i = 0; i < 40; i++) {
            bucket.tryAcquire(KEY, 0);
        }

        long immediate = bucket.retryAfterSeconds(KEY, 0);
        long soon = bucket.retryAfterSeconds(KEY, SECOND / 25);

        assertTrue(immediate >= 1,
                "голодному клиенту Retry-After обязан быть хотя бы 1 секунда: 0 означал бы «повтори сейчас»");
        assertTrue(soon >= 1,
                "пока токенов меньше единицы, Retry-After обязан оставаться хотя бы 1 с");
        assertEquals(0, bucket.retryAfterSeconds("never-seen", 0),
                "ключ, которого нет в таблице, не получает Retry-After");
    }

    @Property(tries = 200)
    void tryAcquire_AnySchedule_NeverExceedsBudget(@ForAll("tickGaps") List<Long> gaps) {
        TokenBucket bucket = new TokenBucket();
        String key = "k";
        long now = 0;
        int issued = 0;
        long totalTicks = 0;
        for (long gap : gaps) {
            now += Math.max(gap, 1);
            totalTicks += Math.max(gap, 1);
            if (bucket.tryAcquire(key, now)) {
                issued++;
            }
        }

        double budget = TokenBucket.CAPACITY + totalTicks / 1e9 * TokenBucket.REFILL_PER_SECOND;
        assertTrue(issued <= budget,
                "выдано " + issued + " больше ёмкости + скорость×время = " + budget
                        + ": лимитер обязан держать общий бюджет");
    }

    @Provide
    Arbitrary<List<Long>> tickGaps() {
        return Arbitraries.longs().between(0, 1_000_000_000L).list().ofMinSize(1).ofMaxSize(80);
    }
}

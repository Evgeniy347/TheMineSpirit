package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class IdempotencyCacheTests {
    private static final String BOT = "11111111-1111-1111-1111-111111111111";
    private static final String FP_ONE = IdempotencyCache.fingerprint(BOT, "цель", null, Map.of());
    private static final String FP_TWO = IdempotencyCache.fingerprint(BOT, "другая", null, Map.of());

    @Test
    void check_SameRequestAfterRemember_ReplaysStoredResponse() {
        IdempotencyCache cache = new IdempotencyCache();
        String requestId = UUID.randomUUID().toString();
        cache.remember(requestId, FP_ONE, "{\"task_id\":\"t-1\"}");

        IdempotencyCache.Result result = cache.check(requestId, FP_ONE);

        IdempotencyCache.Result.Replay replay = assertInstanceOf(IdempotencyCache.Result.Replay.class, result,
                "Повтор с тем же request_id и телом обязан возвращать прежний ответ, а не считаться новым");
        assertEquals("{\"task_id\":\"t-1\"}", replay.storedResponse(),
                "Replay обязан нести дословно прежний ответ: клиент получит тот же task_id");
    }

    @Test
    void check_SameRequestIdDifferentBody_Conflicts() {
        IdempotencyCache cache = new IdempotencyCache();
        String requestId = UUID.randomUUID().toString();
        cache.remember(requestId, FP_ONE, "{\"task_id\":\"t-1\"}");

        IdempotencyCache.Result result = cache.check(requestId, FP_TWO);

        assertInstanceOf(IdempotencyCache.Result.Conflict.class, result,
                "Тот же request_id с другим телом обязан давать Conflict: иначе клиент не заметил бы переиспользование");
    }

    @Test
    void check_UnknownRequestId_IsNew() {
        IdempotencyCache cache = new IdempotencyCache();

        assertInstanceOf(IdempotencyCache.Result.New.class, cache.check(UUID.randomUUID().toString(), FP_ONE),
                "Неизвестный request_id обязан считаться новым запросом");
    }

    @Test
    void check_EvictedRequestId_IsNewAgain() {
        IdempotencyCache cache = new IdempotencyCache();
        String first = UUID.randomUUID().toString();
        cache.remember(first, FP_ONE, "старый");
        for (int i = 0; i < IdempotencyCache.CAPACITY; i++) {
            cache.remember(UUID.randomUUID().toString(), FP_ONE, "заполнитель");
        }

        assertEquals(IdempotencyCache.CAPACITY, cache.size(),
                "Память идемпотентности обязана держать не больше " + IdempotencyCache.CAPACITY + " записей");
        assertInstanceOf(IdempotencyCache.Result.New.class, cache.check(first, FP_ONE),
                "Вытесненный request_id считается новым: память ограничена, старейший ответ уходит");
    }

    @Test
    void remember_AlreadyKnown_DoesNotOverwriteFirstResponse() {
        IdempotencyCache cache = new IdempotencyCache();
        String requestId = UUID.randomUUID().toString();
        cache.remember(requestId, FP_ONE, "прежний");

        cache.remember(requestId, FP_ONE, "новый");

        IdempotencyCache.Result.Replay replay = (IdempotencyCache.Result.Replay) cache.check(requestId, FP_ONE);
        assertEquals("прежний", replay.storedResponse(),
                "Первый ответ закрепляется за request_id: повторная remember не подменяет его");
    }

    @Test
    void fingerprint_ParamOrderAndGoalChange_ChangesDigest() {
        String base = IdempotencyCache.fingerprint(BOT, "цель", "{\"a\":1}", Map.of("x", 1, "y", 2));
        String reordered = IdempotencyCache.fingerprint(BOT, "цель", "{\"a\":1}", Map.of("y", 2, "x", 1));
        String otherGoal = IdempotencyCache.fingerprint(BOT, "иная", "{\"a\":1}", Map.of("x", 1, "y", 2));

        assertEquals(base, reordered, "Порядок ключей params не должен менять отпечаток: тело то же");
        assertNotEquals(base, otherGoal, "Другая цель — другой отпечаток: Conflict обязан сработать");
    }

    @Test
    void fingerprint_UnicodeAndEmptyParts_AreStableHexSha256() {
        String first = IdempotencyCache.fingerprint("дух", "срубить дерево 🌲", null, Map.of("target", "Стив"));
        String second = IdempotencyCache.fingerprint("дух", "срубить дерево 🌲", null, Map.of("target", "Стив"));

        assertEquals(first, second, "Одни и те же данные обязаны давать один отпечаток");
        assertEquals(64, first.length(), "Отпечаток — SHA-256 в 64 hex-символах");
    }

    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(40);
    }

    @Property(tries = 100)
    void check_AnySequence_SizeNeverExceedsCapacityAndRecentSurvives(@ForAll("requestIds") String seed) {
        IdempotencyCache cache = new IdempotencyCache();
        int filler = Math.floorMod(seed.hashCode(), 200) + IdempotencyCache.CAPACITY;
        for (int i = 0; i < filler; i++) {
            cache.remember(seed + "-filler-" + i, FP_ONE, "заполнитель");
        }
        String recent = seed + "-recent";
        cache.remember(recent, FP_ONE, "ответ");

        assertTrue(cache.size() <= IdempotencyCache.CAPACITY,
                "Размер памяти идемпотентности обязан быть ≤ " + IdempotencyCache.CAPACITY + ": " + cache.size());
        assertInstanceOf(IdempotencyCache.Result.Replay.class, cache.check(recent, FP_ONE),
                "Недавний запрос обязан пережить вытеснение более старых: FIFO вытесняет только старейшие");
    }
}

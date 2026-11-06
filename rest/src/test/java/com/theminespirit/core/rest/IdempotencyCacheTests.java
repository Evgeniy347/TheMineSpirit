package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

        IdempotencyCache.Result.Replay replay = assertInstanceOf(
                IdempotencyCache.Result.Replay.class,
                result,
                "Повтор с тем же request_id и телом обязан возвращать прежний ответ, а не считаться новым");
        assertEquals(
                "{\"task_id\":\"t-1\"}",
                replay.storedResponse(),
                "Replay обязан нести дословно прежний ответ: клиент получит тот же task_id");
    }

    @Test
    void check_SameRequestIdDifferentBody_Conflicts() {
        IdempotencyCache cache = new IdempotencyCache();
        String requestId = UUID.randomUUID().toString();
        cache.remember(requestId, FP_ONE, "{\"task_id\":\"t-1\"}");

        IdempotencyCache.Result result = cache.check(requestId, FP_TWO);

        assertInstanceOf(
                IdempotencyCache.Result.Conflict.class,
                result,
                "Тот же request_id с другим телом обязан давать Conflict: иначе клиент не заметил бы переиспользование");
    }

    @Test
    void check_UnknownRequestId_IsNew() {
        IdempotencyCache cache = new IdempotencyCache();

        assertInstanceOf(
                IdempotencyCache.Result.New.class,
                cache.check(UUID.randomUUID().toString(), FP_ONE),
                "Неизвестный request_id обязан считаться новым запросом");
    }

    @Test
    void check_SecondCallWhileFirstInFlight_WaitsAndReplaysStoredResponse() throws Exception {
        IdempotencyCache cache = new IdempotencyCache();
        String requestId = UUID.randomUUID().toString();
        assertInstanceOf(
                IdempotencyCache.Result.New.class,
                cache.check(requestId, FP_ONE),
                "Первый запрос обязан бронировать request_id как взятый в работу");

        AtomicReference<IdempotencyCache.Result> waited = new AtomicReference<>();
        Thread duplicate = new Thread(() -> waited.set(cache.check(requestId, FP_ONE)));
        duplicate.setDaemon(true);
        duplicate.start();
        ThreadStates.awaitWaiting(duplicate, "Дубликат обязан ждать завершения первого запроса, а не получить New");

        cache.remember(requestId, FP_ONE, "{\"task_id\":\"t-1\"}");
        duplicate.join(TimeUnit.SECONDS.toMillis(5));

        IdempotencyCache.Result.Replay replay = assertInstanceOf(
                IdempotencyCache.Result.Replay.class,
                waited.get(),
                "После завершения первого запроса дубликат обязан получить повтор");
        assertEquals(
                "{\"task_id\":\"t-1\"}", replay.storedResponse(), "Повтор обязан нести ответ первого запроса дословно");
    }

    @Test
    void check_DuplicateWaitsLongerThanLimit_ThrowsInsteadOfHangingForever() throws Exception {
        IdempotencyCache cache = new IdempotencyCache(Duration.ofMillis(200));
        String requestId = UUID.randomUUID().toString();
        assertInstanceOf(
                IdempotencyCache.Result.New.class,
                cache.check(requestId, FP_ONE),
                "Первый запрос обязан бронировать request_id как взятый в работу");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread duplicate = new Thread(() -> {
            try {
                cache.check(requestId, FP_ONE);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        duplicate.setDaemon(true);
        duplicate.start();
        duplicate.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(
                duplicate.isAlive(),
                "Дубликат обязан прекратить ждать по пределу 200 мс, а не висеть вечно: "
                        + "первый запрос мог упасть, не вызвав remember/release");
        ApiException error = assertInstanceOf(
                ApiException.class,
                failure.get(),
                "Не дождавшийся повтора дубликат обязан получить ошибку моста, а не молчание");
        assertTrue(
                error.getMessage().contains(requestId),
                "Ошибка обязана называть request_id, по которому не дождались ответа: " + error.getMessage());
    }

    @Test
    void check_EvictedRequestId_IsNewAgain() {
        IdempotencyCache cache = new IdempotencyCache();
        String first = UUID.randomUUID().toString();
        cache.remember(first, FP_ONE, "старый");
        for (int i = 0; i < IdempotencyCache.CAPACITY; i++) {
            cache.remember(UUID.randomUUID().toString(), FP_ONE, "заполнитель");
        }

        assertEquals(
                IdempotencyCache.CAPACITY,
                cache.size(),
                "Память идемпотентности обязана держать не больше " + IdempotencyCache.CAPACITY + " записей");
        assertInstanceOf(
                IdempotencyCache.Result.New.class,
                cache.check(first, FP_ONE),
                "Вытесненный request_id считается новым: память ограничена, старейший ответ уходит");
    }

    @Test
    void remember_AlreadyKnown_DoesNotOverwriteFirstResponse() {
        IdempotencyCache cache = new IdempotencyCache();
        String requestId = UUID.randomUUID().toString();
        cache.remember(requestId, FP_ONE, "прежний");

        cache.remember(requestId, FP_ONE, "новый");

        IdempotencyCache.Result.Replay replay = (IdempotencyCache.Result.Replay) cache.check(requestId, FP_ONE);
        assertEquals(
                "прежний",
                replay.storedResponse(),
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
    void fingerprint_NewlineInGoalVersusNewlineInBot_DifferentDigests() {
        String newlineInGoal = IdempotencyCache.fingerprint("дух", "цель\nx", null, Map.of());
        String newlineInBot = IdempotencyCache.fingerprint("дух\nцель", "x", null, Map.of());

        assertNotEquals(
                newlineInGoal,
                newlineInBot,
                "Перевод строки в поле не должен сдвигать границы полей: разные тела не могут давать один отпечаток, "
                        + "иначе чужой запрос будет принят за повтор");
    }

    @Test
    void fingerprint_SamePlanWithOtherKeyOrder_SameDigest() {
        String first = IdempotencyCache.fingerprint(
                BOT, null, "{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64}}", Map.of());
        String reserialized = IdempotencyCache.fingerprint(
                BOT, null, "{\"params\":{\"y\":64,\"x\":1},\"script\":\"goto\",\"type\":\"script\"}", Map.of());

        assertEquals(
                first,
                reserialized,
                "Тот же план, сериализованный клиентом с другим порядком ключей, обязан давать тот же отпечаток: "
                        + "иначе повтор запроса получит ложный 409");
    }

    @Test
    void fingerprint_GoalAbsentVersusEmpty_DifferentDigests() {
        String absent = IdempotencyCache.fingerprint(BOT, null, null, Map.of());
        String empty = IdempotencyCache.fingerprint(BOT, "", null, Map.of());

        assertNotEquals(absent, empty, "Отсутствующая цель и пустая цель — разные тела");
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

        assertTrue(
                cache.size() <= IdempotencyCache.CAPACITY,
                "Размер памяти идемпотентности обязан быть ≤ " + IdempotencyCache.CAPACITY + ": " + cache.size());
        assertInstanceOf(
                IdempotencyCache.Result.Replay.class,
                cache.check(recent, FP_ONE),
                "Недавний запрос обязан пережить вытеснение более старых: FIFO вытесняет только старейшие");
    }
}

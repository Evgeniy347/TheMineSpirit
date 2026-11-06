package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HangVerdictTests {
    private static final long THRESHOLD = 120_000;
    private static final long NOW = 1_800_000_000_000L;

    static Stream<Arguments> verdicts() {
        return Stream.of(
                Arguments.of("тик только что", NOW, false, HangVerdict.OK),
                Arguments.of("тик на порог старше", NOW - THRESHOLD, false, HangVerdict.HUNG),
                Arguments.of("тик за порогом", NOW - THRESHOLD - 1, false, HangVerdict.HUNG),
                Arguments.of("тик на миллисекунду моложе порога", NOW - THRESHOLD + 1, false, HangVerdict.OK),
                Arguments.of("часы ушли назад", NOW + 5_000, false, HangVerdict.OK),
                Arguments.of("тиков ещё не было", 0, false, HangVerdict.UNKNOWN),
                Arguments.of("тик есть, но идёт пауза отладки", NOW - THRESHOLD * 10, true, HangVerdict.DEBUG_PAUSED),
                Arguments.of("пауза отладки без первого тика", 0, true, HangVerdict.DEBUG_PAUSED));
    }

    @ParameterizedTest(name = "{0} -> {3}")
    @MethodSource("verdicts")
    void decide_TableCase_ReturnsExpectedVerdict(
            String condition, long lastTickMillis, boolean debugPaused, HangVerdict expected) {
        HangVerdict verdict = HangVerdict.decide(NOW, lastTickMillis, THRESHOLD, debugPaused);

        assertEquals(
                expected,
                verdict,
                "Сторож зависания обязан различать живой тик, зависание, паузу отладки и отсутствие данных "
                        + "о тике: " + condition + ". Иначе он либо перезапустит сервер на брейкпоинте, либо "
                        + "пропустит настоящее зависание");
    }

    @Test
    void decide_ZeroThreshold_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> HangVerdict.decide(NOW, NOW - 1, 0, false),
                "Нулевой порог сделал бы любой простой зависанием и вызвал перезапуск живого сервера");

        assertTrue(
                error.getMessage().contains("больше нуля"),
                "Сообщение об ошибке обязано объяснять, что порог зависания должен быть больше нуля: "
                        + error.getMessage());
    }

    @Test
    void ageMillis_NoTickYet_ReturnsMinusOne() {
        HealthSnapshot snapshot = new HealthSnapshot(NOW, 0, 0, THRESHOLD, false);

        assertEquals(
                -1L,
                snapshot.ageMillis(),
                "Без первого тика возраст неизвестен: -1 отличает «ещё не тикал» от «тикал давно», иначе "
                        + "сторож примет старт сервера за зависание");
    }

    @Test
    void ageMillis_ClockWentBackwards_ReturnsZero() {
        HealthSnapshot snapshot = new HealthSnapshot(NOW, NOW + 5_000, 10, THRESHOLD, false);

        assertEquals(
                0L,
                snapshot.ageMillis(),
                "Возраст тика не бывает отрицательным: после перевода часов назад сторож не должен видеть "
                        + "бессмысленное число и принимать его за зависание");
    }

    @Test
    void snapshot_StaleTickWithDebugPause_ReportsDebugVerdict() {
        HealthSnapshot snapshot = new HealthSnapshot(NOW, NOW - THRESHOLD * 5, 100, THRESHOLD, true);

        assertEquals(
                HangVerdict.DEBUG_PAUSED,
                snapshot.verdict(),
                "Пауза отладки важнее зависания: иначе сторож перезапустит сервер, который агент остановил "
                        + "на брейкпоинте намеренно");
    }
}

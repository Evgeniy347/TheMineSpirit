package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.TargetSelector.Kind;
import com.theminespirit.core.tactics.TargetSelector.Target;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TargetSelectorTests {
    private static final String SPIRIT_UUID = "123e4567-e89b-12d3-a456-426614174000";

    @ParameterizedTest(name = "цель {0} разбирается в {1}")
    @CsvSource(delimiter = '|', value = {
            "hostile|HOSTILE",
            "animal|ANIMAL",
            "any|ANY",
            "nearest|NEAREST"
    })
    void parse_KeywordTable_ReturnsKindWithoutName(String raw, Kind expected) {
        Target target = TargetSelector.parse(raw);

        assertEquals(expected, target.kind(),
                "Ключевое слово '" + raw + "' обязано разбираться в вид " + expected
                        + ": иначе скрипт получит не тот вид цели и уйдёт не туда");
        assertTrue(target.name().isEmpty(),
                "Ключевое слово '" + raw + "' не называет существо, поэтому имя обязано быть пустым. Фактическое: "
                        + target.name());
    }

    @ParameterizedTest(name = "цель {0} в другом регистре принимается")
    @ValueSource(strings = {"HOSTILE", "AnImAl", "ANY", "NeArEsT"})
    void parse_KeywordUppercase_ReturnsSameKind(String raw) {
        assertEquals(TargetSelector.parse(raw.toLowerCase(Locale.ROOT)).kind(), TargetSelector.parse(raw).kind(),
                "Регистр ключевого слова обязан схлопываться: план, записанный в другом регистре, обязан читаться "
                        + "так же, как в нижнем");
    }

    @ParameterizedTest(name = "цель '{0}' сохраняет имя '{1}'")
    @CsvSource(delimiter = '|', value = {
            "player:Иван|Иван",
            "PLAYER:Steve|Steve",
            "Player:steve|steve",
            " player:Steve |Steve"
    })
    void parse_PlayerTarget_ReturnsKindAndName(String raw, String expectedName) {
        Target target = TargetSelector.parse(raw);

        assertEquals(Kind.PLAYER, target.kind(),
                "Цель '" + raw + "' обязана разбираться в игрока: иначе дух пойдёт не к тому игроку");
        assertEquals(Optional.of(expectedName), target.name(),
                "Имя игрока обязано сохранять регистр дословно: иначе ник не совпадёт с именем игрока в мире");
    }

    @ParameterizedTest(name = "дух по UUID {0} сохраняется")
    @ValueSource(strings = {
            "spirit:" + SPIRIT_UUID,
            "SPIRIT:" + SPIRIT_UUID
    })
    void parse_SpiritTarget_ReturnsKindAndUuid(String raw) {
        Target target = TargetSelector.parse(raw);

        assertEquals(Kind.SPIRIT, target.kind(),
                "Цель '" + raw + "' обязана разбираться в духа: иначе скрипт ищет среди игроков вместо семьи");
        assertEquals(Optional.of(SPIRIT_UUID), target.name(),
                "UUID духа обязан сохраняться дословно: сущность находится только по точному UUID");
    }

    @ParameterizedTest(name = "пустая цель '{0}' отказывает")
    @ValueSource(strings = {"", "   ", "\t"})
    void parse_EmptyTarget_IsRejected(String raw) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TargetSelector.parse(raw),
                "Пустая цель обязана отказывать: скрипт без цели не знает, кого искать, и молчание скроет ошибку плана");

        assertTrue(error.getMessage().contains("пуст"),
                "Отказ обязан говорить, что цель пустая. Фактическое: " + error.getMessage());
    }

    @ParameterizedTest(name = "неизвестная цель {0} отказывает со списком")
    @ValueSource(strings = {"enemy", "player", "spirit", "hostileman", "nearest:"})
    void parse_UnknownTarget_IsRejected(String raw) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TargetSelector.parse(raw),
                "Неизвестная цель обязана отказывать: молчаливая подмена вида увела бы скрипт к не тому существу");

        assertTrue(error.getMessage().contains("hostile") && error.getMessage().contains("player:")
                        && error.getMessage().contains("spirit:"),
                "Отказ обязан перечислить допустимые значения, чтобы план можно было поправить по тексту ошибки. "
                        + "Фактическое: " + error.getMessage());
    }

    @ParameterizedTest(name = "цель {0} без имени отказывает")
    @ValueSource(strings = {"player:", "spirit:", "player:   "})
    void parse_NamedTargetWithoutName_IsRejected(String raw) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TargetSelector.parse(raw),
                "Цель с пустым именем обязана отказывать: без имени не найти ни игрока, ни духа, "
                        + "и скрипт поехал бы к случайному существу");

        assertTrue(error.getMessage().contains("имя"),
                "Отказ обязан называть проблему с именем. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_SpiritWithInvalidUuid_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TargetSelector.parse("spirit:not-a-uuid"),
                "Spirit-цель с не-UUID обязана отказывать: по мусору сущность не найти, "
                        + "а ошибка всплывёт только в рантайме адаптера");

        assertTrue(error.getMessage().contains("UUID"),
                "Отказ обязан говорить, что значение не является UUID. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_NullTarget_IsRejected() {
        assertThrows(NullPointerException.class, () -> TargetSelector.parse(null),
                "TargetSelector.parse(null) обязан отказывать: параметр обязан прийти строкой, "
                        + "а null означает ошибку маппинга плана");
    }

    @Test
    void target_NameOnKeywordKind_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Target(Kind.HOSTILE, Optional.of("Steve")),
                "Имя у вида без имени обязано отказывать: hostile не указывает на конкретное существо, "
                        + "и молча брошенное имя ввело бы адаптер в заблуждение");

        assertTrue(error.getMessage().contains("HOSTILE"),
                "Отказ обязан называть несогласованный вид. Фактическое: " + error.getMessage());
    }

    @Test
    void target_NameMissingOnPlayer_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Target(Kind.PLAYER, Optional.empty()),
                "Цель PLAYER без имени обязана отказывать: найти игрока по пустому имени невозможно");

        assertTrue(error.getMessage().contains("PLAYER"),
                "Отказ обязан называть вид, которому не хватает имени. Фактическое: " + error.getMessage());
    }
}

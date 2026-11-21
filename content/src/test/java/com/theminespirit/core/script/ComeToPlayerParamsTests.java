package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ComeToPlayerParamsTests {
    @Test
    void from_PlayerPresent_ReturnsPlayer() {
        ComeToPlayerParams params = ComeToPlayerParams.from(Map.of("player", "Steve"));

        assertEquals(
                new ComeToPlayerParams("Steve"),
                params,
                "come_to_player обязан сохранить имя игрока: по нему адаптер выбирает, к кому идти"
                        + " Иначе тест from_PlayerPresent_ReturnsPlayer перестанет ловить эту поломку. При смене from_PlayerPresent_ReturnsPlayer проверь это утверждение.");
    }

    @Test
    void from_MissingPlayer_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ComeToPlayerParams.from(Map.of()),
                "come_to_player без игрока обязан отказывать: валидатор плана подставляет 'nearest' "
                        + "из каталога до запуска, а без него скрипт не знает, к кому идти"
                        + " Иначе тест from_MissingPlayer_IsRejected перестанет ловить эту поломку. При смене from_MissingPlayer_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("player"),
                "Отказ обязан называть недостающий параметр 'player'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingPlayer_IsRejected перестанет ловить эту поломку. При смене from_MissingPlayer_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BlankPlayer_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ComeToPlayerParams.from(Map.of("player", "  ")),
                "come_to_player с пустым именем обязан отказывать: пустую строку нельзя сопоставить с игроком"
                        + " Иначе тест from_BlankPlayer_IsRejected перестанет ловить эту поломку. При смене from_BlankPlayer_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ComeToPlayerParams.from(null),
                "ComeToPlayerParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

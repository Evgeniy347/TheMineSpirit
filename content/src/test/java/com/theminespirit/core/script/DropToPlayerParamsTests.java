package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DropToPlayerParamsTests {
    @Test
    void from_AllParamsPresent_KeepsThem() {
        DropToPlayerParams params =
                DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3, "to", "player:Steve"));

        assertEquals(
                new DropToPlayerParams("cobblestone", 3, "player:Steve"),
                params,
                "drop_to_player обязан сохранить предмет, число и адресата: по ним скрипт берёт груз и бросает его"
                        + " Иначе тест from_AllParamsPresent_KeepsThem перестанет ловить эту поломку. При смене from_AllParamsPresent_KeepsThem проверь это утверждение.");
    }

    @Test
    void from_CountAtCatalogBoundaries_IsAccepted() {
        assertEquals(
                new DropToPlayerParams("cobblestone", CatalogLimits.COUNT_MIN, "player:Steve"),
                DropToPlayerParams.from(
                        Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MIN, "to", "player:Steve")),
                "drop_to_player на нижней границе count обязан приниматься: один предмет бросают чаще всего"
                        + " Иначе тест from_CountAtCatalogBoundaries_IsAccepted перестанет ловить эту поломку. При смене from_CountAtCatalogBoundaries_IsAccepted проверь это утверждение.");
        assertEquals(
                new DropToPlayerParams("cobblestone", CatalogLimits.COUNT_MAX, "player:Steve"),
                DropToPlayerParams.from(
                        Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MAX, "to", "player:Steve")),
                "drop_to_player на верхней границе count обязан приниматься: граница каталога допустима"
                        + " Иначе тест from_CountAtCatalogBoundaries_IsAccepted перестанет ловить эту поломку. При смене from_CountAtCatalogBoundaries_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_CountBelowCatalogMinimum_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DropToPlayerParams.from(
                        Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MIN - 1, "to", "player:Steve")),
                "drop_to_player с count ниже минимума обязан отказывать: бросать нечего"
                        + " Иначе тест from_CountBelowCatalogMinimum_IsRejected перестанет ловить эту поломку. При смене from_CountBelowCatalogMinimum_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MIN)),
                "Отказ обязан называть нижнюю границу count. Фактическое: " + error.getMessage()
                        + " Иначе тест from_CountBelowCatalogMinimum_IsRejected перестанет ловить эту поломку. При смене from_CountBelowCatalogMinimum_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountAboveCatalogMaximum_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DropToPlayerParams.from(
                        Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MAX + 1, "to", "player:Steve")),
                "drop_to_player с count выше максимума обязан отказывать: столько за один бросок не унести"
                        + " Иначе тест from_CountAboveCatalogMaximum_IsRejected перестанет ловить эту поломку. При смене from_CountAboveCatalogMaximum_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MAX)),
                "Отказ обязан называть верхнюю границу count. Фактическое: " + error.getMessage()
                        + " Иначе тест from_CountAboveCatalogMaximum_IsRejected перестанет ловить эту поломку. При смене from_CountAboveCatalogMaximum_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingItem_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("count", 3, "to", "player:Steve")),
                "drop_to_player без предмета обязан отказывать: бросать нечего"
                        + " Иначе тест from_MissingItem_IsRejected перестанет ловить эту поломку. При смене from_MissingItem_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("item"),
                "Отказ обязан называть параметр 'item'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingItem_IsRejected перестанет ловить эту поломку. При смене from_MissingItem_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", " ", "count", 3, "to", "player:Steve")),
                "drop_to_player с пустым предметом обязан отказывать: пустой id не найдётся в инвентаре"
                        + " При смене from_BlankItem_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3)),
                "drop_to_player без адресата обязан отказывать: бросать некому"
                        + " Иначе тест from_MissingAddressee_IsRejected перестанет ловить эту поломку. При смене from_MissingAddressee_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("to"),
                "Отказ обязан называть параметр 'to'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingAddressee_IsRejected перестанет ловить эту поломку. При смене from_MissingAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_SpiritAddressee_IsAccepted() {
        String uuid = UUID.randomUUID().toString();
        DropToPlayerParams params =
                DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3, "to", "spirit:" + uuid));

        assertEquals(
                new DropToPlayerParams("cobblestone", 3, "spirit:" + uuid),
                params,
                "drop_to_player обязан принимать духа-адресата: бросить можно и рядом с духом"
                        + " Иначе тест from_SpiritAddressee_IsAccepted перестанет ловить эту поломку. При смене from_SpiritAddressee_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_MonsterKeywordAddressee_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3, "to", "hostile")),
                "drop_to_player с видом цели вместо существа обязан отказывать: бросать некому"
                        + " Иначе тест from_MonsterKeywordAddressee_IsRejected перестанет ловить эту поломку. При смене from_MonsterKeywordAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DropToPlayerParams.from(null),
                "DropToPlayerParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TakeParamsTests {
    @Test
    void from_AllParamsPresent_KeepsThem() {
        TakeParams params = TakeParams.from(Map.of("item", "stick", "count", 2, "from", "player:Steve"));

        assertEquals(
                new TakeParams("stick", 2, "player:Steve"),
                params,
                "take обязан сохранить предмет, число и источник: по ним скрипт ищет, у кого забрать груз"
                        + " Иначе тест from_AllParamsPresent_KeepsThem перестанет ловить эту поломку. При смене from_AllParamsPresent_KeepsThem проверь это утверждение.");
    }

    @Test
    void from_CountAtCatalogBoundaries_IsAccepted() {
        String uuid = UUID.randomUUID().toString();
        assertEquals(
                new TakeParams("stick", CatalogLimits.COUNT_MIN, "spirit:" + uuid),
                TakeParams.from(Map.of("item", "stick", "count", CatalogLimits.COUNT_MIN, "from", "spirit:" + uuid)),
                "take на нижней границе count обязан приниматься: один предмет забирают чаще всего"
                        + " Иначе тест from_CountAtCatalogBoundaries_IsAccepted перестанет ловить эту поломку. При смене from_CountAtCatalogBoundaries_IsAccepted проверь это утверждение.");
        assertEquals(
                new TakeParams("stick", CatalogLimits.COUNT_MAX, "player:Steve"),
                TakeParams.from(Map.of("item", "stick", "count", CatalogLimits.COUNT_MAX, "from", "player:Steve")),
                "take на верхней границе count обязан приниматься: граница каталога допустима"
                        + " Иначе тест from_CountAtCatalogBoundaries_IsAccepted перестанет ловить эту поломку. При смене from_CountAtCatalogBoundaries_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_CountBelowCatalogMinimum_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TakeParams.from(
                        Map.of("item", "stick", "count", CatalogLimits.COUNT_MIN - 1, "from", "player:Steve")),
                "take с count ниже минимума обязан отказывать: брать нечего"
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
                () -> TakeParams.from(
                        Map.of("item", "stick", "count", CatalogLimits.COUNT_MAX + 1, "from", "player:Steve")),
                "take с count выше максимума обязан отказывать: столько за один раз не забрать"
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
                () -> TakeParams.from(Map.of("count", 2, "from", "player:Steve")),
                "take без предмета обязан отказывать: брать нечего"
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
                () -> TakeParams.from(Map.of("item", " ", "count", 2, "from", "player:Steve")),
                "take с пустым предметом обязан отказывать: пустой id не найдётся в инвентаре"
                        + " При смене from_BlankItem_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingSource_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TakeParams.from(Map.of("item", "stick", "count", 2)),
                "take без источника обязан отказывать: брать не у кого"
                        + " Иначе тест from_MissingSource_IsRejected перестанет ловить эту поломку. При смене from_MissingSource_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("from"),
                "Отказ обязан называть параметр 'from'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingSource_IsRejected перестанет ловить эту поломку. При смене from_MissingSource_IsRejected проверь это утверждение.");
    }

    @Test
    void from_PlayerSource_IsAccepted() {
        TakeParams params = TakeParams.from(Map.of("item", "stick", "count", 2, "from", "player:Alex"));

        assertEquals(
                "player:Alex",
                params.from(),
                "take обязан принимать игрока-источник: игрок сам отдаёт предмет духу"
                        + " Иначе тест from_PlayerSource_IsAccepted перестанет ловить эту поломку. При смене from_PlayerSource_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_SpiritSource_IsAccepted() {
        String uuid = UUID.randomUUID().toString();
        TakeParams params = TakeParams.from(Map.of("item", "stick", "count", 2, "from", "spirit:" + uuid));

        assertEquals(
                "spirit:" + uuid,
                params.from(),
                "take обязан принимать духа-источник по UUID: забрать можно и у другого духа"
                        + " Иначе тест from_SpiritSource_IsAccepted перестанет ловить эту поломку. При смене from_SpiritSource_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_MonsterKeywordSource_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TakeParams.from(Map.of("item", "stick", "count", 2, "from", "hostile")),
                "take с видом цели вместо существа обязан отказывать: у моба предмет не забрать"
                        + " Иначе тест from_MonsterKeywordSource_IsRejected перестанет ловить эту поломку. При смене from_MonsterKeywordSource_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> TakeParams.from(null),
                "TakeParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

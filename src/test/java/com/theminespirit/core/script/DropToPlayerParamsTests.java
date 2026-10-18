package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DropToPlayerParamsTests {
    @Test
    void from_AllParamsPresent_KeepsThem() {
        DropToPlayerParams params = DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3,
                "to", "player:Steve"));

        assertEquals(new DropToPlayerParams("cobblestone", 3, "player:Steve"), params,
                "drop_to_player обязан сохранить предмет, число и адресата: по ним скрипт берёт груз и бросает его");
    }

    @Test
    void from_CountAtCatalogBoundaries_IsAccepted() {
        assertEquals(new DropToPlayerParams("cobblestone", CatalogLimits.COUNT_MIN, "player:Steve"),
                DropToPlayerParams.from(Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MIN,
                        "to", "player:Steve")),
                "drop_to_player на нижней границе count обязан приниматься: один предмет бросают чаще всего");
        assertEquals(new DropToPlayerParams("cobblestone", CatalogLimits.COUNT_MAX, "player:Steve"),
                DropToPlayerParams.from(Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MAX,
                        "to", "player:Steve")),
                "drop_to_player на верхней границе count обязан приниматься: граница каталога допустима");
    }

    @Test
    void from_CountBelowCatalogMinimum_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MIN - 1,
                        "to", "player:Steve")),
                "drop_to_player с count ниже минимума обязан отказывать: бросать нечего");

        assertTrue(error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MIN)),
                "Отказ обязан называть нижнюю границу count. Фактическое: " + error.getMessage());
    }

    @Test
    void from_CountAboveCatalogMaximum_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", "cobblestone", "count", CatalogLimits.COUNT_MAX + 1,
                        "to", "player:Steve")),
                "drop_to_player с count выше максимума обязан отказывать: столько за один бросок не унести");

        assertTrue(error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MAX)),
                "Отказ обязан называть верхнюю границу count. Фактическое: " + error.getMessage());
    }

    @Test
    void from_MissingItem_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("count", 3, "to", "player:Steve")),
                "drop_to_player без предмета обязан отказывать: бросать нечего");

        assertTrue(error.getMessage().contains("item"),
                "Отказ обязан называть параметр 'item'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", " ", "count", 3, "to", "player:Steve")),
                "drop_to_player с пустым предметом обязан отказывать: пустой id не найдётся в инвентаре");
    }

    @Test
    void from_MissingAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3)),
                "drop_to_player без адресата обязан отказывать: бросать некому");

        assertTrue(error.getMessage().contains("to"),
                "Отказ обязан называть параметр 'to'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_SpiritAddressee_IsAccepted() {
        String uuid = UUID.randomUUID().toString();
        DropToPlayerParams params = DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3,
                "to", "spirit:" + uuid));

        assertEquals(new DropToPlayerParams("cobblestone", 3, "spirit:" + uuid), params,
                "drop_to_player обязан принимать духа-адресата: бросить можно и рядом с духом");
    }

    @Test
    void from_MonsterKeywordAddressee_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DropToPlayerParams.from(Map.of("item", "cobblestone", "count", 3, "to", "hostile")),
                "drop_to_player с видом цели вместо существа обязан отказывать: бросать некому");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> DropToPlayerParams.from(null),
                "DropToPlayerParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать");
    }
}

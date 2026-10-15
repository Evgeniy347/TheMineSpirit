package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliverParamsTests {
    @Test
    void from_AllParamsPresent_KeepsThem() {
        DeliverParams params = DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "player:Steve"));

        assertEquals(new DeliverParams("cobblestone", 3, "player:Steve"), params,
                "deliver обязан сохранить предмет, число и адресата: по ним скрипт ищет груз и ведёт его к получателю");
    }

    @Test
    void from_CountAtBoundaries_IsAccepted() {
        assertEquals(new DeliverParams("cobblestone", 1, "player:Steve"),
                DeliverParams.from(Map.of("item", "cobblestone", "count", 1, "to", "player:Steve")),
                "deliver на нижней границе count=1 обязан приниматься: один предмет — самая частая доставка");
        assertEquals(new DeliverParams("cobblestone", 256, "player:Steve"),
                DeliverParams.from(Map.of("item", "cobblestone", "count", 256, "to", "player:Steve")),
                "deliver на верхней границе count=256 обязан приниматься: граница каталога допустима, "
                        + "больше в инвентарь не влезает");
    }

    @Test
    void from_MissingItem_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("count", 3, "to", "player:Steve")),
                "deliver без предмета обязан отказывать: нечего везти адресату");

        assertTrue(error.getMessage().contains("item"),
                "Отказ обязан называть недостающий параметр 'item'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", " ", "count", 3, "to", "player:Steve")),
                "deliver с пустым предметом обязан отказывать: пустой id не найдётся ни в одном слоте инвентаря");
    }

    @Test
    void from_MissingAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 3)),
                "deliver без адресата обязан отказывать: доставить некому");

        assertTrue(error.getMessage().contains("to"),
                "Отказ обязан называть недостающий параметр 'to'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_CountBelowRange_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 0, "to", "player:Steve")),
                "deliver с count=0 обязан отказывать: доставлять нечего, а 0 не является количеством");

        assertTrue(error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MIN)),
                "Отказ обязан называть нижнюю границу. Фактическое: " + error.getMessage());
    }

    @Test
    void from_CountAboveRange_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 257, "to", "player:Steve")),
                "deliver с count=257 обязан отказывать: больше максимума инвентарь не примет за один раз");

        assertTrue(error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MAX)),
                "Отказ обязан называть верхнюю границу. Фактическое: " + error.getMessage());
    }

    @Test
    void from_SpiritAddressee_IsAccepted() {
        String uuid = UUID.randomUUID().toString();
        DeliverParams params = DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "spirit:" + uuid));

        assertEquals(new DeliverParams("cobblestone", 3, "spirit:" + uuid), params,
                "deliver обязан принимать духа-адресата по UUID: передача бывает не только игроку");
    }

    @Test
    void from_MonsterKeywordAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "hostile")),
                "deliver с адресатом-видом hostile обязан отказывать: предмет некому вручить");

        assertTrue(error.getMessage().contains("to"),
                "Отказ обязан называть параметр 'to'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_UnparsableAddressee_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "кто-то непонятный")),
                "deliver с нераспознаваемым адресатом обязан отказывать: доставить некому");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> DeliverParams.from(null),
                "DeliverParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}

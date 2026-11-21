package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliverParamsTests {
    @Test
    void from_AllParamsPresent_KeepsThem() {
        DeliverParams params = DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "player:Steve"));

        assertEquals(
                new DeliverParams("cobblestone", 3, "player:Steve"),
                params,
                "deliver обязан сохранить предмет, число и адресата: по ним скрипт ищет груз и ведёт его к получателю"
                        + " Иначе тест from_AllParamsPresent_KeepsThem перестанет ловить эту поломку. При смене from_AllParamsPresent_KeepsThem проверь это утверждение.");
    }

    @Test
    void from_CountAtBoundaries_IsAccepted() {
        assertEquals(
                new DeliverParams("cobblestone", 1, "player:Steve"),
                DeliverParams.from(Map.of("item", "cobblestone", "count", 1, "to", "player:Steve")),
                "deliver на нижней границе count=1 обязан приниматься: один предмет — самая частая доставка"
                        + " Иначе тест from_CountAtBoundaries_IsAccepted перестанет ловить эту поломку. При смене from_CountAtBoundaries_IsAccepted проверь это утверждение.");
        assertEquals(
                new DeliverParams("cobblestone", 256, "player:Steve"),
                DeliverParams.from(Map.of("item", "cobblestone", "count", 256, "to", "player:Steve")),
                "deliver на верхней границе count=256 обязан приниматься: граница каталога допустима, "
                        + "больше в инвентарь не влезает"
                        + " Иначе тест from_CountAtBoundaries_IsAccepted перестанет ловить эту поломку. При смене from_CountAtBoundaries_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_MissingItem_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("count", 3, "to", "player:Steve")),
                "deliver без предмета обязан отказывать: нечего везти адресату"
                        + " Иначе тест from_MissingItem_IsRejected перестанет ловить эту поломку. При смене from_MissingItem_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("item"),
                "Отказ обязан называть недостающий параметр 'item'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingItem_IsRejected перестанет ловить эту поломку. При смене from_MissingItem_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", " ", "count", 3, "to", "player:Steve")),
                "deliver с пустым предметом обязан отказывать: пустой id не найдётся ни в одном слоте инвентаря"
                        + " При смене from_BlankItem_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 3)),
                "deliver без адресата обязан отказывать: доставить некому"
                        + " Иначе тест from_MissingAddressee_IsRejected перестанет ловить эту поломку. При смене from_MissingAddressee_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("to"),
                "Отказ обязан называть недостающий параметр 'to'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingAddressee_IsRejected перестанет ловить эту поломку. При смене from_MissingAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountBelowRange_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 0, "to", "player:Steve")),
                "deliver с count=0 обязан отказывать: доставлять нечего, а 0 не является количеством"
                        + " Иначе тест from_CountBelowRange_IsRejected перестанет ловить эту поломку. При смене from_CountBelowRange_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MIN)),
                "Отказ обязан называть нижнюю границу. Фактическое: " + error.getMessage()
                        + " Иначе тест from_CountBelowRange_IsRejected перестанет ловить эту поломку. При смене from_CountBelowRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountAboveRange_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 257, "to", "player:Steve")),
                "deliver с count=257 обязан отказывать: больше максимума инвентарь не примет за один раз"
                        + " Иначе тест from_CountAboveRange_IsRejected перестанет ловить эту поломку. При смене from_CountAboveRange_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(String.valueOf(CatalogLimits.COUNT_MAX)),
                "Отказ обязан называть верхнюю границу. Фактическое: " + error.getMessage()
                        + " Иначе тест from_CountAboveRange_IsRejected перестанет ловить эту поломку. При смене from_CountAboveRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_SpiritAddressee_IsAccepted() {
        String uuid = UUID.randomUUID().toString();
        DeliverParams params = DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "spirit:" + uuid));

        assertEquals(
                new DeliverParams("cobblestone", 3, "spirit:" + uuid),
                params,
                "deliver обязан принимать духа-адресата по UUID: передача бывает не только игроку"
                        + " Иначе тест from_SpiritAddressee_IsAccepted перестанет ловить эту поломку. При смене from_SpiritAddressee_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_MonsterKeywordAddressee_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "hostile")),
                "deliver с адресатом-видом hostile обязан отказывать: предмет некому вручить"
                        + " Иначе тест from_MonsterKeywordAddressee_IsRejected перестанет ловить эту поломку. При смене from_MonsterKeywordAddressee_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("to"),
                "Отказ обязан называть параметр 'to'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MonsterKeywordAddressee_IsRejected перестанет ловить эту поломку. При смене from_MonsterKeywordAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnparsableAddressee_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliverParams.from(Map.of("item", "cobblestone", "count", 3, "to", "кто-то непонятный")),
                "deliver с нераспознаваемым адресатом обязан отказывать: доставить некому"
                        + " Иначе тест from_UnparsableAddressee_IsRejected перестанет ловить эту поломку. При смене from_UnparsableAddressee_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DeliverParams.from(null),
                "DeliverParams.from(null) обязан отказывать: без карты параметров маппинг только читает план"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

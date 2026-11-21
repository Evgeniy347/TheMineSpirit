package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TillParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        TillParams params = TillParams.from(Map.of("radius", 8, "count", 16));

        assertEquals(
                new TillParams(8, 16),
                params,
                "till обязан сохранить радиус и число: по ним адаптер ищет землю и считает вспашку"
                        + " Иначе тест from_AllParamsPresent_ReturnsParams перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_MissingRadius_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TillParams.from(Map.of("count", 16)),
                "till без radius обязан отказывать: умолчание подставляет валидатор из каталога, а не маппинг"
                        + " Иначе тест from_MissingRadius_IsRejected перестанет ловить эту поломку. При смене from_MissingRadius_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TillParams.from(Map.of("radius", 8)),
                "till без count обязан отказывать: умолчание подставляет валидатор из каталога, а не маппинг"
                        + " Иначе тест from_MissingCount_IsRejected перестанет ловить эту поломку. При смене from_MissingCount_IsRejected проверь это утверждение.");
    }

    @Test
    void from_RadiusAboveFarmLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TillParams.from(Map.of("radius", 17, "count", 16)),
                "radius больше 16 обязан отвергаться: фермерский поиск шире грядки кладёт серверный тик"
                        + " Иначе тест from_RadiusAboveFarmLimit_IsRejected перестанет ловить эту поломку. При смене from_RadiusAboveFarmLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TillParams.from(Map.of("radius", 8, "count", 257)),
                "count больше 256 обязан отвергаться: лимит ограничивает работу за один запуск скрипта"
                        + " Иначе тест from_CountAboveLimit_IsRejected перестанет ловить эту поломку. При смене from_CountAboveLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> TillParams.from(null),
                "TillParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

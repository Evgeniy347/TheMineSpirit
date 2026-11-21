package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GatherParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        GatherParams params = GatherParams.from(Map.of("block", "cobblestone", "count", 18, "radius", 16));

        assertEquals(
                new GatherParams("cobblestone", 18, 16),
                params,
                "gather обязан сохранить блок, число и радиус: по ним адаптер ищет и собирает блоки"
                        + " Иначе тест from_AllParamsPresent_ReturnsParams перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_TemplateBlock_IsKept() {
        GatherParams params = GatherParams.from(Map.of("block", "*_log", "count", 4, "radius", 8));

        assertEquals(
                new GatherParams("*_log", 4, 8),
                params,
                "gather обязан принимать шаблон '*_log' как есть: разбор шаблона делает поиск, а не маппинг"
                        + " Иначе тест from_TemplateBlock_IsKept перестанет ловить эту поломку. При смене from_TemplateBlock_IsKept проверь это утверждение.");
    }

    @Test
    void from_MissingBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GatherParams.from(Map.of("count", 1, "radius", 16)),
                "gather без block обязан отказывать: без типа блока скрипт не знает, что собирать"
                        + " Иначе тест from_MissingBlock_IsRejected перестанет ловить эту поломку. При смене from_MissingBlock_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BlankBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GatherParams.from(Map.of("block", "   ", "count", 1, "radius", 16)),
                "Пустой блок обязан отвергаться: пробельный id не совпадёт ни с одним блоком мира"
                        + " Иначе тест from_BlankBlock_IsRejected перестанет ловить эту поломку. При смене from_BlankBlock_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GatherParams.from(Map.of("block", "stone", "radius", 16)),
                "gather без count обязан отказывать: у gather число обязательно, в отличие от mine_vein"
                        + " Иначе тест from_MissingCount_IsRejected перестанет ловить эту поломку. При смене from_MissingCount_IsRejected проверь это утверждение.");
    }

    @Test
    void from_LongCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GatherParams.from(Map.of("block", "stone", "count", 1L, "radius", 16)),
                "маппинг обязан принимать только Integer: long означает, что значение не прошло валидатор плана"
                        + " Иначе тест from_LongCount_IsRejected перестанет ловить эту поломку. При смене from_LongCount_IsRejected проверь это утверждение.");
    }

    @Test
    void from_RadiusOutOfRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GatherParams.from(Map.of("block", "stone", "count", 1, "radius", 33)),
                "radius больше 32 обязан отвергаться: лимит ограничивает поиск и нагрузку на серверный тик"
                        + " Иначе тест from_RadiusOutOfRange_IsRejected перестанет ловить эту поломку. При смене from_RadiusOutOfRange_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> GatherParams.from(null),
                "GatherParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

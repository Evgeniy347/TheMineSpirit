package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AcquireParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        AcquireParams params = AcquireParams.from(Map.of("item", "iron_ingot", "count", 3));

        assertEquals(
                new AcquireParams("iron_ingot", 3),
                params,
                "acquire обязан сохранить предмет и число: по ним скрипт строит подплан добычи"
                        + " Иначе тест from_AllParamsPresent_ReturnsParams перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsParams проверь это утверждение.");
    }

    @Test
    void from_MissingItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("count", 1)),
                "acquire без item обязан отказывать: без имени предмета скрипт не знает, что добыть"
                        + " Иначе тест from_MissingItem_IsRejected перестанет ловить эту поломку. При смене from_MissingItem_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BlankItem_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "   ", "count", 1)),
                "Пустой item обязан отвергаться: пробельный id не совпадёт ни с одним предметом"
                        + " Иначе тест from_BlankItem_IsRejected перестанет ловить эту поломку. При смене from_BlankItem_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick")),
                "acquire без count обязан отказывать: число обязательно, цель «сколько добыть» обязана быть числовой"
                        + " Иначе тест from_MissingCount_IsRejected перестанет ловить эту поломку. При смене from_MissingCount_IsRejected проверь это утверждение.");
    }

    @Test
    void from_LongCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick", "count", 1L)),
                "маппинг обязан принимать только Integer: long означает, что значение не прошло валидатор плана"
                        + " Иначе тест from_LongCount_IsRejected перестанет ловить эту поломку. При смене from_LongCount_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountBelowMinimum_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick", "count", 0)),
                "count=0 обязан отвергаться: нулевая цель добычи не имеет смысла"
                        + " Иначе тест from_CountBelowMinimum_IsRejected перестанет ловить эту поломку. При смене from_CountBelowMinimum_IsRejected проверь это утверждение.");
    }

    @Test
    void from_CountAboveMaximum_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireParams.from(Map.of("item", "stick", "count", 257)),
                "count больше 256 обязан отвергаться: лимит каталога не даёт плану разрастись"
                        + " Иначе тест from_CountAboveMaximum_IsRejected перестанет ловить эту поломку. При смене from_CountAboveMaximum_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> AcquireParams.from(null),
                "AcquireParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}

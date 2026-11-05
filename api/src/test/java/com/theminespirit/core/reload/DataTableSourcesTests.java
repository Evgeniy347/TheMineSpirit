package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.ObservationLimits;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataTableSourcesTests {
    private static final FeedTable FEED = new FeedTable(Map.of(
            Species.COW, List.of("minecraft:apple"),
            Species.SHEEP, List.of("minecraft:apple"),
            Species.PIG, List.of("minecraft:apple"),
            Species.CHICKEN, List.of("minecraft:apple")));

    @AfterEach
    void resetSources() {
        ObservationLimits.useSource(ObservationLimits::defaults);
        FeedTable.useSource(FeedTable::defaults);
        RespawnRules.useSource(RespawnRules::defaults);
        ConfigStamp.useCurrent(() -> "unknown");
    }

    @Test
    void observationLimits_AfterUseSource_LoadDefaultReturnsBoundTable() {
        ObservationLimits bound = new ObservationLimits(1, 2, 3, 4);

        ObservationLimits.useSource(() -> bound);

        assertEquals(
                bound,
                ObservationLimits.loadDefault(),
                "Загруженная таблица наблюдения обязана подменять умолчания: иначе /reload не меняет пределы скана");
    }

    @Test
    void feedTable_AfterUseSource_FeedForUsesBoundTable() {
        FeedTable.useSource(() -> FEED);

        assertEquals(
                List.of("minecraft:apple"),
                FeedTable.feedFor(Species.COW),
                "Таблица кормов обязана приходить из загруженных данных: иначе дух кормит стадо кормом из кода");
    }

    @Test
    void respawnRules_AfterUseSource_LoadDefaultReturnsBoundTable() {
        RespawnRules bound = new RespawnRules(42);

        RespawnRules.useSource(() -> bound);

        assertEquals(
                bound,
                RespawnRules.loadDefault(),
                "Загруженные правила возрождения обязаны подменять умолчания: иначе /reload их не меняет");
    }

    @Test
    void configStamp_AfterUseCurrent_ReturnsBoundHash() {
        ConfigStamp.useCurrent(() -> "hash-bound");

        assertEquals(
                "hash-bound",
                ConfigStamp.current(),
                "Хэш конфигурации обязан приходить из таблицы доски: иначе журнал не отличит версию конфигурации");
    }

    @Test
    void sources_NullSupplier_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> ObservationLimits.useSource(null),
                "Пустой источник пределов наблюдения обязан отвергаться: без него /reload молча теряется");
        assertThrows(
                NullPointerException.class,
                () -> FeedTable.useSource(null),
                "Пустой источник кормов обязан отвергаться: без него стадо остаётся без проверенной таблицы");
        assertThrows(
                NullPointerException.class,
                () -> RespawnRules.useSource(null),
                "Пустой источник возрождения обязан отвергаться: без него задержка молча теряется");
        assertThrows(
                NullPointerException.class,
                () -> ConfigStamp.useCurrent(null),
                "Пустой источник хэша обязан отвергаться: журнал не должен писать выдуманную версию");
    }

    @Test
    void configStamp_WithoutBinding_DefaultHashIsUnknown() {
        assertTrue(
                !ConfigStamp.current().isBlank(),
                "До загрузки таблицы хэш всё равно обязан быть непустым: пустая строка скрывает отсутствие версии");
    }
}

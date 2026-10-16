package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.DataTables;
import org.junit.jupiter.api.Test;

class BoardConfigTests {

    @Test
    void defaults_FromClasspath_PriorityMatchesRules() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(100, config.priorities().get("REPAIR_FENCE"),
                "Приоритет ремонта ограждений в таблице — 100: первый из источника (п. 4), "
                        + "иначе доска отсортирует выдачу не так, как принято в поселении");
        assertEquals(30, config.priorities().get("ENCHANT_SHELVES"),
                "Приоритет полок — 30: самый низкий, иначе чаровальня перебьёт стройки");
        assertTrue(config.priorities().size() >= BoardCase.values().length,
                "Каждый кейс обязан иметь строку в таблице: кейс-сирота роняет сортировку выдачи");
    }

    @Test
    void defaults_FromClasspath_IntervalsMatchTuning() {
        BoardConfig config = DataTables.INSTANCE.board().current();

        assertEquals(100, config.intervals().get("boardIntervalTicks"),
                "Пересборка раз в 100 тиков — образец DEMAND_INTERVAL_TICKS: число живёт в таблице, "
                        + "а не в константе, иначе /reload его не поменяет");
        assertEquals(200, config.intervals().get("herdScanIntervalTicks"),
                "Опрос животных раз в 200 тиков: каждый тик scan_entities грузил бы тик сервера");
        assertEquals(0, config.ttl().get("FILL_CRATER"),
                "Воронка живёт до выполнения (TTL 0): просроченная воронка — открытая яма навсегда");
    }

    @Test
    void fromJson_UnknownPriorityKey_FailsNamingCase() {
        String json = "{\"priorities\":{\"CLEAR_GROUNXYZ\":50}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени кейса обязана падать: молча потерянная строка превратила бы "
                        + "кейс в сироту, а сирота роняет сортировку выдачи");
        assertTrue(failure.getMessage().contains("CLEAR_GROUNXYZ"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_UnknownIntervalKey_FailsNamingKey() {
        String json = "{\"intervals\":{\"boardIntervalMillis\":100}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Опечатка в имени интервала обязана падать: молча потерянный ключ оставил бы "
                        + "доску на значениях по умолчанию без ведома настройщика");
        assertTrue(failure.getMessage().contains("boardIntervalMillis"),
                "Сообщение обязано называть неверный ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_NegativeValue_FailsNamingField() {
        String json = "{\"priorities\":{\"REPAIR_FENCE\":-5}}";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardConfig.fromJson(json, "board.json"),
                "Отрицательный приоритет обязан падать: сортировка выдачи сравнивает приоритеты "
                        + "числом, отрицательное значение перевернёт порядок ремонта и стройки");
        assertTrue(failure.getMessage().contains("REPAIR_FENCE"),
                "Сообщение обязано называть ключ: " + failure.getMessage());
    }

    @Test
    void fromJson_BrokenJson_FailsNamingOrigin() {
        assertThrows(IllegalStateException.class, () -> BoardConfig.fromJson("{", "data/custom.json"),
                "Битый JSON обязан падать с путём файла: настройщик обязан увидеть, какой файл "
                        + "исправить, а не гадать");
    }

    @Test
    void fromJson_EmptyTables_AreAllowed() {
        BoardConfig config = BoardConfig.fromJson("{}", "board.json");

        assertTrue(config.priorities().isEmpty() && config.ttl().isEmpty() && config.intervals().isEmpty(),
                "Пустые таблицы допустимы: недостающее добирают значения по умолчанию из "
                        + "BoardRules и BoardTuning");
        assertEquals(BoardRules.priorityOf(BoardCase.REPAIR_FENCE), 100,
                "Без строки в таблице работает приоритет по умолчанию: доска не падает на пустом файле");
    }

    @Test
    void noTtl_RepairAndCrater_LiveForever() {
        assertTrue(BoardTuning.noTtlFor(BoardCase.REPAIR_FENCE),
                "Ремонт ограждений живёт до выполнения: просроченный ремонт значил бы открытые ворота");
        assertTrue(BoardTuning.noTtlFor(BoardCase.FILL_CRATER),
                "Воронка живёт до выполнения: просроченная яма — открытая воронка навсегда");
        assertFalse(BoardTuning.noTtlFor(BoardCase.LURE_ANIMAL),
                "«Приманить» имеет TTL: ждавшая приманки задание обязано возвращаться в очередь, "
                        + "а не висеть вечно");
    }
}

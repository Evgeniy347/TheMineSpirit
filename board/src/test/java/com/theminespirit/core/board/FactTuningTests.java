package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.api.JsonResource;
import org.junit.jupiter.api.Test;

class FactTuningTests {
    @Test
    void fromJson_AllFieldsPresent_ValuesRead() {
        FactTuning facts = FactTuning.fromJson(
                JsonResource.parse(allFieldsJson().replace("\"animalRadius\":48", "\"animalRadius\":40"), "board.json"),
                "board.json");

        assertEquals(40, facts.animalRadius(), "радиус животных читается из данных");
        assertEquals(12, facts.penRadius(), "радиус загона читается из данных");
        assertEquals(8, facts.flowRadius(), "радиус потока читается из данных");
    }

    @Test
    void fromJson_ZeroRadius_Rejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FactTuning.fromJson(
                        JsonResource.parse(
                                allFieldsJson().replace("\"animalRadius\":48", "\"animalRadius\":0"), "board.json"),
                        "board.json"),
                "нулевой радиус ослепляет сканирование мира — таблица обязана падать");

        assertTrue(
                error.getMessage().contains("facts.animalRadius"),
                "Сообщение обязано назвать поле: «" + error.getMessage() + "»");
    }

    @Test
    void fromJson_UnknownField_Rejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> FactTuning.fromJson(
                        JsonResource.parse(allFieldsJson().replace("}", ",\"extraRadius\":1}"), "board.json"),
                        "board.json"),
                "Опечатка в имени поля обязана быть ошибкой: иначе правка данных молча не применяется");

        assertTrue(
                error.getMessage().contains("extraRadius"),
                "Сообщение обязано назвать неизвестное поле: «" + error.getMessage() + "»");
    }

    @Test
    void fromJson_MissingField_Rejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> FactTuning.fromJson(
                        JsonResource.parse(allFieldsJson().replace(",\"flowRadius\":8", ""), "board.json"),
                        "board.json"),
                "без радиуса потока факт не считается — таблица обязана падать");

        assertTrue(
                error.getMessage().contains("facts.flowRadius"),
                "Сообщение обязано назвать поле: «" + error.getMessage() + "»");
    }

    private static String allFieldsJson() {
        return "{\"animalRadius\":48,\"penRadius\":12,\"wildRadius\":16,\"villagerRadius\":48,"
                + "\"bedRadius\":20,\"hazardRadius\":3,\"flowRadius\":8,\"dropRadius\":8,"
                + "\"decayRadius\":16,\"playerRadius\":24,\"portalRadius\":24,\"hazardScanRadius\":4,"
                + "\"petRadius\":32,\"spaceRadius\":48,\"growRadius\":16,\"mineRadius\":8,"
                + "\"homeRadius\":32,\"looseRadius\":24,\"stationRadius\":8,\"fieldRadius\":24,"
                + "\"conflictRadius\":4,\"threatRadius\":16,\"farmScanRadius\":16,\"herdSurveyRadius\":32,\"lifeScanRadius\":16,\"nearbyPlayerRadius\":16,\"dropScanRadius\":24,\"areaScanRadius\":16,\"stationSearchRadius\":12,\"standSnapRadius\":16,\"homeSearchRadius\":16,\"hostileScanRadius\":16,\"chestSearchRadius\":16,\"settlementScanRadius\":64,\"enemyScanRadius\":16,\"workPointRadius\":32,\"furnaceSearchRadius\":4,\"surroundingsRadius\":32,\"petWorkRadius\":32,\"fluidSearchRadius\":16,\"treeCenterRadius\":16,\"woodScanRadius\":24,\"woodWideScanRadius\":48,\"woodScanHeight\":8,\"routeMaxDistance\":64,\"groundProbeDepth\":48,\"craterRimScanDepth\":24,\"stationReachRadius\":48}";
    }
}

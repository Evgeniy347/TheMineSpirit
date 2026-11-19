package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.api.JsonResource;
import org.junit.jupiter.api.Test;

class PlanTuningTests {
    @Test
    void fromJson_AllFieldsPresent_ValuesRead() {
        PlanTuning tuning = PlanTuning.fromJson(
                JsonResource.parse(
                        "{\"mineFloorTake\":32,\"leftOreRadius\":4,\"mineTunnelSize\":5,\"mineStepsSize\":3,"
                                + "\"supplyContainerRadius\":8,\"dismantleRadius\":4,\"relocateClearRadius\":4,"
                                + "\"charcoalLogs\":4,\"charcoalBatch\":2,\"torchesPerCraft\":4,\"torchBatch\":16,"
                                + "\"fenceParts\":4,\"oreCount\":8,\"oreRadius\":16,\"logsPerTree\":4,"
                                + "\"clearGrid\":3,\"clearHeight\":3,\"freeRadius\":16,\"freeDirtReach\":13,"
                                + "\"farmRadius\":8,\"farmCount\":4,\"containerRadius\":8,\"craterFillRadius\":1}",
                        "board.json"),
                "board.json");
        assertEquals(
                5,
                tuning.mineTunnelSize(),
                "Поле plans.mineTunnelSize не прочитано: сечение ствола шахты снова берётся из Java-константы, "
                        + "и правка данных не меняет план");
        assertEquals(
                16,
                tuning.torchBatch(),
                "Поле plans.torchBatch не прочитано: размер партии факелов останется в коде и разойдётся с данными");
    }

    @Test
    void fromJson_ZeroSize_Rejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlanTuning.fromJson(JsonResource.parse(zeroTunnelJson(), "board.json"), "board.json"),
                "Нулевое сечение ствола обязано быть ошибкой: пустой план поставит духа в тупик");
        assertEquals(
                "Поле plans.mineTunnelSize = 0: размер плана обязан быть положительным, иначе задание пустое и дух встанет",
                error.getMessage(),
                "Сообщение об ошибке обязано назвать поле и цену: без этого правку данных не найти");
    }

    @Test
    void fromJson_UnknownField_Rejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PlanTuning.fromJson(
                        JsonResource.parse(allFieldsJson().replace("}", ",\"mineTunnelSide\":5}"), "board.json"),
                        "board.json"),
                "Опечатка в имени поля обязана быть ошибкой: иначе правка данных молча не применяется");
        assertTrue(
                error.getMessage().contains("mineTunnelSide"),
                "Сообщение обязано назвать неизвестное поле: «" + error.getMessage() + "»");
    }

    @Test
    void fromJson_MissingField_Rejected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PlanTuning.fromJson(
                        JsonResource.parse(allFieldsJson().replace(",\"fenceParts\":4", ""), "board.json"),
                        "board.json"),
                "Отсутствующее поле плана обязано быть ошибкой: иначе план молча собирается без числа");
        assertTrue(
                error.getMessage().contains("plans.fenceParts"),
                "Сообщение обязано назвать отсутствующее поле: «" + error.getMessage() + "»");
    }

    private static String zeroTunnelJson() {
        return allFieldsJson().replace("\"mineTunnelSize\":5", "\"mineTunnelSize\":0");
    }

    private static String allFieldsJson() {
        return "{\"mineFloorTake\":32,\"leftOreRadius\":4,\"mineTunnelSize\":5,\"mineStepsSize\":3,"
                + "\"supplyContainerRadius\":8,\"dismantleRadius\":4,\"relocateClearRadius\":4,"
                + "\"charcoalLogs\":4,\"charcoalBatch\":2,\"torchesPerCraft\":4,\"torchBatch\":16,"
                + "\"fenceParts\":4,\"oreCount\":8,\"oreRadius\":16,\"logsPerTree\":4,"
                + "\"clearGrid\":3,\"clearHeight\":3,\"freeRadius\":16,\"freeDirtReach\":13,"
                + "\"farmRadius\":8,\"farmCount\":4,\"containerRadius\":8,\"craterFillRadius\":1}";
    }
}

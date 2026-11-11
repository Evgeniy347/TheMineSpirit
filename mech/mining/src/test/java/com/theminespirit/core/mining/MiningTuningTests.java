package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MiningTuningTests {
    @Test
    void maxApproachFailures_ThreeAttempts_BeforeExclusion() {
        assertEquals(
                3,
                MiningTuning.MAX_APPROACH_FAILURES,
                "Стоянка исключается, а цель бросается после трёх неудачных подходов: один-два срыва "
                        + "под нагрузкой — шум навигации, три подряд — недостижимость. Меньше — ложные "
                        + "исключения хороших стоянок, больше — сотни тиков на заведомо глухую цель");
    }

    @Test
    void maxStandDrop_BuildReachDown_MatchesData() throws IOException {
        JsonNode data = new ObjectMapper()
                .readTree(Files.readString(
                        Path.of("mod/src/main/resources/data/theminespirit/building/build.json"),
                        StandardCharsets.UTF_8));

        assertEquals(
                data.get("reachDown").asInt(),
                MiningTuning.MAX_STAND_DROP,
                "Глубина стоянки под целью обязана совпадать с досягаемостью вниз из build.json: иначе "
                        + "поиск снова сузит окно и цель на столбе останется «без стоянки», хотя дух до неё "
                        + "дотягивается");
    }
}

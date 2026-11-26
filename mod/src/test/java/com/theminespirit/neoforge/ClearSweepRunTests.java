package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClearSweepRunTests {
    @Test
    void pillar_NoHigherTargets_TriesAnotherStandAfterDescent() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/ClearSweepRun.java"));
        String compact = source.replaceAll("\\s+", "");
        int onPillar = compact.indexOf("if(onPillar){");
        int tried = compact.indexOf("triedAnchors.add(anchor==null?feet():anchor.coord());", onPillar);
        int descent = compact.indexOf("scaffold.startDescent();", onPillar);
        String after = source.substring(
                source.indexOf("private void afterDismantle"), source.indexOf("private boolean riseAllowed"));
        String breakTick = source.substring(source.indexOf("void breakTick"), source.indexOf("private void finish"));

        assertTrue(
                onPillar >= 0 && tried > onPillar && descent > tried,
                "Перед спуском с бесполезного столба базовая стоянка помечается провальной: иначе clear_area "
                        + "снова построит тот же столб и потратит 3600 тиков без снятия блока. При смене "
                        + "pillar_NoHigherTargets_TriesAnotherStandAfterDescent проверь живой wheat_field и INC-101.");
        assertFalse(
                after.contains("triedAnchors.clear"),
                "Спуск не стирает отвергнутую стоянку: иначе цикл подъёма и спуска повторяется бесконечно. "
                        + "При смене pillar_NoHigherTargets_TriesAnotherStandAfterDescent проверь afterDismantle.");
        assertTrue(
                breakTick.contains("triedAnchors.clear"),
                "После реального снятия блока стоянки можно пробовать заново: иначе уборка будет помнить "
                        + "устаревшие отказы. При смене pillar_NoHigherTargets_TriesAnotherStandAfterDescent проверь breakTick.");
    }
}

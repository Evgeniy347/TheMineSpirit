package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClearSweepRunTests {
    @Test
    void pick_TopmostTargets_AnchorDoesNotOpenLowerLayer() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/ClearSweepRun.java"));

        assertTrue(
                source.contains("ClearOrder.topmost(session.remaining")
                        && source.contains("ClearStands.best(session, triedAnchors, highest, triedColumns)"),
                "Стойка выбирается только для верхнего оставшегося слоя: иначе недостижимый верх открывает "
                        + "нижние ящики и расчистка идёт не сверху вниз. При смене "
                        + "pick_TopmostTargets_AnchorDoesNotOpenLowerLayer проверь ClearStands.best и живое поле.");
    }

    @Test
    void pillar_RejectedColumn_IsNotReusedBeforeBreak() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/ClearSweepRun.java"));
        String compact = source.replaceAll("\\s+", "");
        int onPillar = compact.indexOf("if(onPillar){");
        int guardedRise =
                compact.indexOf("if(!triedColumns.contains(column)&&scaffold.canRise()&&riseAllowed())", onPillar);
        int rejected = compact.indexOf("triedColumns.add(column);", onPillar);
        int descent = compact.indexOf("scaffold.startDescent();", onPillar);
        String after = source.substring(
                source.indexOf("private void afterDismantle"), source.indexOf("private boolean riseAllowed"));
        String breakTick = source.substring(source.indexOf("void breakTick"), source.indexOf("private void finish"));
        String stands = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/ClearStands.java"));

        assertTrue(
                onPillar >= 0 && guardedRise > onPillar && rejected > guardedRise && descent > rejected,
                "Отвергнутая колонка исключается перед спуском: иначе дух строит тот же столб на уровень ниже "
                        + "и снова тратит 3600 тиков без снятия блока. При смене "
                        + "pillar_RejectedColumn_IsNotReusedBeforeBreak проверь onPillar и живую (-120,68,-460).");
        assertFalse(
                after.contains("triedColumns.clear"),
                "Спуск не разрешает отвергнутую колонку повторно: иначе цикл бесполезных столбов сохраняется. "
                        + "При смене pillar_RejectedColumn_IsNotReusedBeforeBreak проверь afterDismantle.");
        assertTrue(
                breakTick.contains("triedColumns.clear"),
                "Только реально снятая цель снимает запрет колонок: иначе после прогресса дух не сможет "
                        + "вернуться и расчистить нижний слой. При смене pillar_RejectedColumn_IsNotReusedBeforeBreak "
                        + "проверь ветку BROKEN.");
        assertTrue(
                stands.contains("blockedColumns.stream()") && stands.contains("ClearAnchor.sameColumn(column, stand)"),
                "Отвергнутые колонки исключаются из выбора стоек: иначе запрет подъёма обходится новым якорем. "
                        + "При смене pillar_RejectedColumn_IsNotReusedBeforeBreak проверь ClearStands.best.");
    }

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

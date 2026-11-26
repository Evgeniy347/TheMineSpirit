package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StartupAuditGuardTests {
    private static final Path BOARD = SourceFiles.path("neoforge/board");

    @Test
    void boardRegistry_NoStartupWorldAudit_KeepsFirstTickSmall() {
        String source = SourceFiles.javaUnder(BOARD).get(BOARD.resolve("BoardRegistry.java"));

        assertEquals(
                List.of(),
                startupAudits(Map.of(BOARD.resolve("BoardRegistry.java"), source)),
                "Первый тик не должен аудировать весь этап с facts=null: иначе BoardRegistry.get заново "
                        + "обходит постройки и держит серверный поток 3–4 секунды при каждом запуске. "
                        + "Гейт применяет GraphSource.finish с готовыми facts. При смене StartupAuditGuardTests "
                        + "проверь BoardRegistry.get, GraphSource.finish и TickWatch после прогрева.");
        assertTrue(
                SourceFiles.javaUnder(BOARD)
                        .get(BOARD.resolve("GraphPosting.java"))
                        .contains("half.state().facts()"),
                "Гейт этапа остаётся подключённым к фактам графа: иначе удаление стартового аудита "
                        + "остановит проверку готовности построек. При смене StartupAuditGuardTests проверь "
                        + "StageWorldGate.apply в GraphPosting.flush.");
    }

    @Test
    void startupAudits_NullFactsCall_IsReported() {
        assertEquals(
                List.of("BoardRegistry.java"),
                startupAudits(Map.of(Path.of("BoardRegistry.java"), "StageWorldGate.apply(level, service, null);")),
                "Сторож ослеп: вызов StageWorldGate.apply из стартового пути обязан быть замечен, иначе "
                        + "полный аудит снова попадёт в первый тик. При смене StartupAuditGuardTests проверь "
                        + "реальный BoardRegistry и GraphSource.finish.");
    }

    @Test
    void startupAudits_GraphFactsCall_IsAccepted() {
        assertEquals(
                List.of(),
                startupAudits(Map.of(
                        Path.of("GraphSource.java"), "StageWorldGate.apply(level, service, half.state().facts());")),
                "Сторож не должен запрещать нормальный гейт с готовыми фактами: иначе проверка этапа "
                        + "исчезнет вместе со стартовым аудитом. При смене StartupAuditGuardTests проверь "
                        + "StageWorldGate.apply и AuditedWorld.caseReadyNow.");
    }

    private static List<String> startupAudits(Map<Path, String> sources) {
        List<String> offenders = new ArrayList<>();
        sources.forEach((file, text) -> {
            boolean startupRegistry =
                    "BoardRegistry.java".equals(file.getFileName().toString());
            if (text.contains("StageBootstrap.apply") || (startupRegistry && text.contains("StageWorldGate.apply"))) {
                offenders.add(file.toString());
            }
        });
        return offenders;
    }
}

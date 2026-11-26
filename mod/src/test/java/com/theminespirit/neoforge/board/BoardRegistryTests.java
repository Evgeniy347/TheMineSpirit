package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BoardRegistryTests {
    @Test
    void deepReport_Progress_UsesSnapshotsWithoutReauditingWorld() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/GraphReporter.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("service.tickets.progress.last(building.id())")
                        && compact.contains("half.service().tickets.progress.last(id)"),
                "Глубокой отчёт обязан брать прогресс из снимков этого прохода: иначе finish повторно аудирует "
                        + "все постройки и возвращает холодный фриз. При смене "
                        + "deepReport_Progress_UsesSnapshotsWithoutReauditingWorld проверь GraphReporter.deep, "
                        + "GraphObserve.builds и graph.src.deep.");
        assertFalse(
                source.contains("FirstStepsRunner.progress(level, service, building.boardCase()"),
                "Диагностический отчёт не должен сам запускать аудит мира: иначе красивая строка лога блокирует "
                        + "серверный тик. При смене deepReport_Progress_UsesSnapshotsWithoutReauditingWorld "
                        + "проверь GraphReporter.readiness и buildingsText.");
    }

    @Test
    void stageBootstrap_Removed_StartupAuditClassIsAbsent() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("com.theminespirit.neoforge.board.StageBootstrap"),
                "Стартового класса полного аудита быть не должно: иначе он снова попадёт в BoardRegistry.get "
                        + "и задержит первый тик на 3–4 секунды. При смене BoardRegistryTests проверь "
                        + "BoardRegistry.get, GraphSource.finish и StartupAuditGuardTests.");
    }
}

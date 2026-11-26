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
    void fenceWatch_UnloadedChunk_ReadsOnlyLoadedChunkState() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/FenceWatch.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("getChunkNow(block.getX()>>4,block.getZ()>>4)")
                        && compact.contains("chunk==null||!isFence(chunk.getBlockState(block))"),
                "Проверка сломанного забора обязана читать состояние только из уже открытого чанка: иначе "
                        + "ServerLevel.getBlockState ждёт частично загруженный чанк и холодная пересборка стоит "
                        + "секунды. При смене fenceWatch_UnloadedChunk_ReadsOnlyLoadedChunkState проверь "
                        + "FenceWatch.liveBreaks и board.src.fence.scan.");
        assertFalse(
                compact.contains("level.getBlockState(block)"),
                "Для старой памяти забора нельзя читать блок через ServerLevel: иначе холодный src.fence "
                        + "снова блокирует серверный тик. При смене fenceWatch_UnloadedChunk_ReadsOnlyLoadedChunkState "
                        + "проверь FenceWatch.liveBreaks.");
    }

    @Test
    void craterWatch_UnloadedChunk_ReadsOnlyLoadedChunkState() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/CraterWatch.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("getChunkNow(block.getX()>>4,block.getZ()>>4)")
                        && compact.contains("chunk==null||chunk.getBlockState(block).isAir()"),
                "Память воронок обязана сохранять незагруженную клетку без чтения блока: иначе getBlockState "
                        + "синхронно грузит чанк и холодная пересборка снова зависает. При смене "
                        + "craterWatch_UnloadedChunk_ReadsOnlyLoadedChunkState проверь CraterWatch.liveCauses "
                        + "и src.crater.");
    }

    @Test
    void finishReport_Diagnostics_DeferredFromCriticalTick() throws IOException {
        String source =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/GraphSource.java"));
        String finish = source.substring(
                source.indexOf("private Set<String> finish"), source.indexOf("public static Set<String> events"));

        assertTrue(
                finish.contains("new GraphReport(half") && source.contains("reporting.advance(reporter, decisionLog)"),
                "Критический finish обязан отдавать билеты сразу, а диагностику ставить в отдельный шаблон: "
                        + "иначе отчёты снова складывают холодные паузы в один тик. При смене "
                        + "finishReport_Diagnostics_DeferredFromCriticalTick проверь GraphSource.advance, "
                        + "GraphReport.advance и graph.src.finish.");
        assertFalse(
                finish.contains("reporter.report(") && finish.contains("reporter.builds("),
                "Отчёты не должны выполняться внутри finish: иначе graph.src.finish снова упирается в аудит "
                        + "мира. При смене finishReport_Diagnostics_DeferredFromCriticalTick проверь "
                        + "GraphReport.advance и graph.src.report.");
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

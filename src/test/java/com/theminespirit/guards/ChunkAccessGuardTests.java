package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ChunkAccessGuardTests {
    private static final Pattern GET_CHUNK_WITH_STATUS =
            Pattern.compile("getChunk\\s*\\([^,]+,\\s*[^,]+,\\s*ChunkStatus\\.");
    private static final Pattern GET_CHUNK_SOURCE_CHUNK_NOW =
            Pattern.compile("getChunkSource\\(\\)\\.getChunkNow\\s*\\(");

    @Test
    void neoforgeSources_GetChunkWithStatus_Forbidden() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(SourceFiles.MAIN).forEach((file, text) -> {
            if (!file.startsWith(SourceFiles.path("neoforge"))) {
                return;
            }
            Matcher badMatcher = GET_CHUNK_WITH_STATUS.matcher(text);
            while (badMatcher.find()) {
                int line = SourceFiles.lineOf(text, badMatcher.start());
                offenders.add(file + ":" + line + " — использует getChunk(..., ChunkStatus.*)");
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "В neoforge запрещено level.getChunk(x, z, ChunkStatus.*, ...) для чтения мира скриптами и сканом. "
                        + "На серверном потоке getChunk для чанка с билетом, ещё не FULL, может ждать генерации (managedBlock). "
                        + "Как надо: level.getChunkSource().getChunkNow(x, z) — вернёт чанк, если он уже загружен (любой статус), иначе null. "
                        + "Скан/скрипты обязаны пропускать не-FULL чанки без догрузки. Нарушители:");
    }

    @Test
    void getChunkNowPattern_ValidUsage_Detected() {
        String valid = "level.getChunkSource().getChunkNow(chunkX, chunkZ);";
        assertTrue(
                GET_CHUNK_SOURCE_CHUNK_NOW.matcher(valid).find(),
                "Сторож ослеп: getChunkSource().getChunkNow должен находиться");
    }

    @Test
    void getChunkWithStatusPattern_InvalidUsage_Detected() {
        String invalid1 = "level.getChunk(x, z, ChunkStatus.FULL, false)";
        String invalid2 = "level.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, true)";
        assertTrue(
                GET_CHUNK_WITH_STATUS.matcher(invalid1).find(),
                "Сторож ослеп: getChunk с ChunkStatus.FULL должен находиться");
        assertTrue(
                GET_CHUNK_WITH_STATUS.matcher(invalid2).find(),
                "Сторож ослеп: getChunk с ChunkStatus.EMPTY должен находиться");
    }

    @Test
    void getChunkWithStatusPattern_NonChunkStatus_Ignored() {
        String other = "level.getChunk(x, z)";
        assertTrue(
                !GET_CHUNK_WITH_STATUS.matcher(other).find(),
                "Сторож ложно срабатывает: getChunk без ChunkStatus не нарушение");
    }
}

package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LevelSafetyViewTests {
    @Test
    void cellAt_UnloadedChunk_DoesNotLoadWorld() throws IOException {
        String safety = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/LevelSafetyView.java"))
                .replaceAll("\\s+", "");
        String network = Files.readString(
                        Path.of("mod/src/main/java/com/theminespirit/neoforge/movement/NetworkLevelView.java"))
                .replaceAll("\\s+", "");

        assertTrue(
                safety.contains("getChunkNow(pos.getX()>>4,pos.getZ()>>4)")
                        && safety.contains("chunk.getBlockState(pos)"),
                "Незагруженный чанк помечается непроходимым до чтения BlockState: иначе HomeStand снова "
                        + "синхронно грузит мир и холодный поиск стоянки занимает секунды. При смене "
                        + "cellAt_UnloadedChunk_DoesNotLoadWorld проверь LevelSafetyView, HomeStand и "
                        + "board.issue.home.");
        assertTrue(
                network.contains("getChunkNow(pos.getX()>>4,pos.getZ()>>4)")
                        && network.contains("chunk.getBlockState(pos)"),
                "Сетевой обход тоже читает только открытый чанк: иначе поиск компонента стоянки повторит "
                        + "блокировку серверного потока. При смене cellAt_UnloadedChunk_DoesNotLoadWorld проверь "
                        + "NetworkLevelView и RetreatPath.walkable.");
        assertFalse(
                safety.contains("level.getBlockState(pos)") || network.contains("level.getBlockState(pos)"),
                "Горячие навигационные виды не должны обращаться к ServerLevel.getBlockState: иначе "
                        + "частично загруженный чанк снова останавливает серверный тик. При смене "
                        + "cellAt_UnloadedChunk_DoesNotLoadWorld проверь NetworkLevelView.tallBelow и "
                        + "LevelSafetyView.cellAt.");
    }
}

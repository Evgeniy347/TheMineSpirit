package com.theminespirit.neoforge;

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
                safety.contains("if(!level.hasChunk(pos.getX()>>4,pos.getZ()>>4)){returnCell.SOLID;}")
                        && safety.indexOf("hasChunk") < safety.indexOf("getBlockState"),
                "Незагруженный чанк помечается непроходимым до getBlockState: иначе HomeStand снова "
                        + "синхронно грузит мир и стартовый stock занимает секунды. При смене "
                        + "cellAt_UnloadedChunk_DoesNotLoadWorld проверь JFR startup4177 и HomeStand.");
        assertTrue(
                network.contains("if(!level.hasChunk(pos.getX()>>4,pos.getZ()>>4)){returnCell.SOLID;}")
                        && network.indexOf("hasChunk") < network.indexOf("getBlockState"),
                "Сетевой обход тоже не грузит чанки: иначе поиск компонента стоянки повторит блокировку "
                        + "серверного потока. При смене cellAt_UnloadedChunk_DoesNotLoadWorld проверь "
                        + "NetworkLevelView и RetreatPath.walkable.");
    }
}

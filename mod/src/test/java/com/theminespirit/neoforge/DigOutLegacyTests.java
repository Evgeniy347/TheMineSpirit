package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DigOutLegacyTests {
    @Test
    void digOut_OwnSteps_AreNotRestoredAsNaturalTerrain() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/DigOutScript.java"));
        String compact = source.replaceAll("\\s+", "");

        assertFalse(
                source.contains("DigOutBackfill"),
                "Заделка пути землёй удалена: иначе dig_out снова оставляет dirt-платформы на складах. "
                        + "При смене digOut_OwnSteps_AreNotRestoredAsNaturalTerrain проверь INC-100 и живой storage_2.");
        assertTrue(
                compact.contains("if(!lease.contains(stuck)){restore.remember(stuck,inside);}"),
                "Свой блок под телом не восстанавливается как природный: иначе ступень останется в мире. "
                        + "При смене digOut_OwnSteps_AreNotRestoredAsNaturalTerrain проверь DigOutFloor и LeaseRun.");
        assertTrue(
                compact.contains("if(!lease.contains(cell)){restore.remember(cell,before);}"),
                "Свой блок на пути не восстанавливается после ломки: иначе аренда оставит земляной след. "
                        + "При смене digOut_OwnSteps_AreNotRestoredAsNaturalTerrain проверь DigOutScript.dig и DigOutRestore.");
    }
}

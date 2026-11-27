package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DigOutLegacyTests {
    @Test
    void digOut_WaterFloor_DoesNotPlaceDirtOverSource() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/DigOutScript.java"));
        String compact = source.replaceAll("\\s+", "");

        assertFalse(
                compact.contains("DigOutSpace.passable(level,floor)||DigOutSpace.water(level,floor)"),
                "Вода не считается пустым полом ступени: иначе rescue снова засыпает источник на пашне "
                        + "dirt и ломает орошение. При смене digOut_WaterFloor_DoesNotPlaceDirtOverSource "
                        + "проверь DigOutScript.tick и живой источник (-127,63,-457).");
        assertTrue(
                compact.contains(
                        "if(DigOutSpace.passable(level,floor)){Optional<String>refusal=stepFloor.place(floor);"),
                "Dirt ставится только под действительно проходимый воздушный пол: иначе ступень "
                        + "строится сквозь жидкость и уничтожает блок мира. При смене "
                        + "digOut_WaterFloor_DoesNotPlaceDirtOverSource проверь DigOutFloor.place.");
    }

    @Test
    void digOut_CompletedStep_RecomputesDirectionTowardTarget() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/DigOutScript.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("steps++;turns=0;direction=null;returnScriptStatus.RUNNING;"),
                "После успешного шага направление снова считается к цели: иначе поворот вокруг препятствия "
                        + "сохраняется и дух кружит вместо движения к дому. При смене "
                        + "digOut_CompletedStep_RecomputesDirectionTowardTarget проверь SpinWatch и живой spirit-6.");
    }

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

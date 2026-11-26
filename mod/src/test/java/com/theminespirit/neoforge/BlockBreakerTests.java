package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.FarmTuning;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class BlockBreakerTests {
    @Test
    void reachableFrom_HorizontalTarget_UsesEuclideanEyeDistance() {
        BlockPos feet = new BlockPos(0, 64, 0);

        assertTrue(
                BreakReach.reachableFrom(feet, new BlockPos(4, 64, 0)),
                "Цель в четырёх блоках по горизонтали остаётся достижимой: иначе очистка будет выбирать "
                        + "лишние стоянки и замедлит стройку. При смене reachableFrom_HorizontalTarget_UsesEuclideanEyeDistance "
                        + "проверь BlockBreaker.REACH и живое поле.");
        assertFalse(
                BreakReach.reachableFrom(feet, new BlockPos(5, 64, 0)),
                "Цель в пяти блоках по горизонтали уже недостижима: иначе планировщик снова попробует сломать "
                        + "её из текущей стоянки и зациклится. При смене reachableFrom_HorizontalTarget_UsesEuclideanEyeDistance "
                        + "проверь SchematicWorkPhase.clearStep.");
    }

    @Test
    void reachableFrom_HighTarget_RejectsBeyondVerticalReach() {
        BlockPos feet = new BlockPos(0, 64, 0);

        assertTrue(
                BreakReach.reachableFrom(feet, new BlockPos(0, 69, 0)),
                "Блок на пять координат выше ног достижим из-за высоты глаз: иначе дух не сможет убрать "
                        + "верхний мусор над схемой. При смене reachableFrom_HighTarget_RejectsBeyondVerticalReach "
                        + "проверь EYE_HEIGHT и живой wheat_field.");
        assertFalse(
                BreakReach.reachableFrom(feet, new BlockPos(0, 70, 0)),
                "Блок на шесть координат выше ног вне фактической руки: иначе очистка повторит отказ "
                        + "«дальше 4.5 от глаз» без смены стоянки. При смене "
                        + "reachableFrom_HighTarget_RejectsBeyondVerticalReach проверь SchematicPlacement.moveTowards.");
    }

    @Test
    void clearStep_ReachMismatch_UsesBreakDistanceForWorkAndStands() throws IOException {
        String work = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicWorkPhase.java"));
        String stands = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/SchematicStands.java"));

        assertTrue(
                work.contains("BreakReach.reachableFrom(host.spirit.blockPosition(), pos)")
                        && work.contains("(stand, cell) -> BreakReach.reachableFrom("),
                "Очистка и выбор стоянки используют одно евклидово расстояние: иначе прямоугольный SpiritReach "
                        + "снова выберет текущую стоянку для недостижимого блока. При смене "
                        + "clearStep_ReachMismatch_UsesBreakDistanceForWorkAndStands проверь SchematicWorkPhase.clearStep.");
        assertTrue(
                stands.contains("BiPredicate<BlockCoord, BlockCoord> reaches")
                        && stands.contains("excluded.add(candidate)"),
                "Стойка, не подходящая по фактическому расстоянию, исключается до следующего выбора: иначе "
                        + "дух бесконечно возвращается к той же точке. При смене "
                        + "clearStep_ReachMismatch_UsesBreakDistanceForWorkAndStands проверь SchematicStands.choose.");
    }

    @Test
    void reach_ComparedWithFarmTuningReach_IsNotSmaller() {
        assertTrue(
                FarmTuning.REACH <= BlockBreaker.REACH,
                "Досягаемость фермы " + FarmTuning.REACH + " обязана быть не больше досягаемости ломки "
                        + BlockBreaker.REACH + ": иначе планировщик поставит дух туда, откуда BlockBreaker "
                        + "отвергнет обработку клетки"
                        + " При смене reach_ComparedWithFarmTuningReach_IsNotSmaller проверь это утверждение.");
    }
}

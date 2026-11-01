package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LevelRepairProgressTests {
    private static final BlockCoord CUT_DONE = new BlockCoord(5, 70, 5);
    private static final BlockCoord CUT_STANDS = new BlockCoord(6, 70, 5);
    private static final BlockCoord FILL_SOLID = new BlockCoord(0, 69, 0);
    private static final BlockCoord FILL_HOLE = new BlockCoord(1, 69, 0);

    @Test
    void progress_LevelingRepair_HasCutAndFillCellsInsteadOfZeroBlocks() {
        PlotLeveling leveling = LevelRepair.leveling(List.of(FILL_SOLID, FILL_HOLE), List.of(CUT_DONE, CUT_STANDS));
        Set<BlockCoord> empty = new HashSet<>(Set.of(CUT_DONE, FILL_HOLE));

        LevelRepair.Progress progress = LevelRepair.progress(leveling, empty::contains);

        assertEquals(
                4,
                progress.total(),
                "Ремонт выравнивания — это 2 лишних блока срезать и 2 дыры засыпать: план из 0 блоков "
                        + "ошибочно закрывал его как «нечего строить»");
        assertEquals(
                2, progress.done(), "Срезан один лишний блок и засыпана одна дыра: готовность считается по факту мира");
        assertTrue(
                StageAudit.nothingToBuild(progress.total()).isEmpty(),
                "У ремонта выравнивания есть работа — «стройка закрыта без работы» больше не срабатывает");
    }

    @Test
    void progress_AllExtrasCut_RepairIsComplete() {
        PlotLeveling leveling = LevelRepair.leveling(List.of(), List.of(CUT_DONE, CUT_STANDS));

        LevelRepair.Progress progress =
                LevelRepair.progress(leveling, at -> at.equals(CUT_DONE) || at.equals(CUT_STANDS));

        assertEquals(2, progress.total(), "Два лишних блока — две клетки ремонта: " + progress);
        assertEquals(progress.total(), progress.done(), "Оба лишних блока срезаны — ремонт завершён");
    }
}

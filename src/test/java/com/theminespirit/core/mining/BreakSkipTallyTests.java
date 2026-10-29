package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreakSkipTallyTests {
    private static final BlockCoord TARGET = new BlockCoord(-163, 70, -500);
    private static final BlockCoord STAND = new BlockCoord(-162, 70, -500);

    @Test
    void record_OutOfReach_IncrementsOutOfReachNotNoStand() {
        BreakSkipTally tally = new BreakSkipTally();

        tally.record(BreakSkipTally.Reason.OUT_OF_REACH, TARGET, STAND);

        assertEquals(
                1,
                tally.count(BreakSkipTally.Reason.OUT_OF_REACH),
                "Отказ «дальше досягаемости» обязан попадать в свой счётчик: иначе дух сдаётся с ложным "
                        + "«без места для духа» на достижимом блоке");
        assertEquals(
                0,
                tally.count(BreakSkipTally.Reason.NO_STAND),
                "Отказ «дальше досягаемости» не означает «нет стоянки»: стоянка есть, духу нужно подойти");
    }

    @Test
    void record_NoSightAndChunkNotLoaded_KeepSeparateCounters() {
        BreakSkipTally tally = new BreakSkipTally();

        tally.record(BreakSkipTally.Reason.NO_SIGHT, TARGET, STAND);
        tally.record(BreakSkipTally.Reason.CHUNK_NOT_LOADED, TARGET, STAND);
        tally.record(BreakSkipTally.Reason.NO_STAND, TARGET, STAND);

        assertEquals(1, tally.count(BreakSkipTally.Reason.NO_SIGHT), "Отказ «не виден» обязан считаться отдельно");
        assertEquals(
                1,
                tally.count(BreakSkipTally.Reason.CHUNK_NOT_LOADED),
                "Отказ «чанк не загружен» обязан считаться отдельно");
        assertEquals(1, tally.count(BreakSkipTally.Reason.NO_STAND), "Отказ «нет стоянки» обязан считаться отдельно");
    }

    @Test
    void text_AllRefusalReasons_ListsEachWithCount() {
        BreakSkipTally tally = new BreakSkipTally();

        tally.record(BreakSkipTally.Reason.OUT_OF_REACH, TARGET, STAND);
        tally.record(BreakSkipTally.Reason.NO_SIGHT, TARGET, STAND);
        tally.record(BreakSkipTally.Reason.CHUNK_NOT_LOADED, TARGET, STAND);
        tally.record(BreakSkipTally.Reason.NO_STAND, TARGET, STAND);

        assertEquals(
                "вне досягаемости: 1, не виден: 1, чанк не загружен: 1, без стоянки: 1",
                tally.text(),
                "Итог обязан называть каждую причину пропуска отдельно: по общему «без стоянки» не видно, "
                        + "что духу нужно подойти, а не искать другое место");
    }

    @Test
    void record_MoreThanThreeSamples_KeepsFirstThreeWithStand() {
        BreakSkipTally tally = new BreakSkipTally();

        for (int i = 0; i < 5; i++) {
            tally.record(BreakSkipTally.Reason.OUT_OF_REACH, new BlockCoord(i, 70, -500), STAND);
        }

        assertEquals(
                List.of(
                        new BreakSkipTally.Sample(
                                new BlockCoord(0, 70, -500), BreakSkipTally.Reason.OUT_OF_REACH, STAND),
                        new BreakSkipTally.Sample(
                                new BlockCoord(1, 70, -500), BreakSkipTally.Reason.OUT_OF_REACH, STAND),
                        new BreakSkipTally.Sample(
                                new BlockCoord(2, 70, -500), BreakSkipTally.Reason.OUT_OF_REACH, STAND)),
                tally.samples(),
                "В лог идут первые три пропущенных блока с позицией и кандидатом стоянки: без них причину "
                        + "отказа не проверить в мире");
    }
}

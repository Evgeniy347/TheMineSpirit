package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearScaffoldStepTests {
    private static final BlockCoord FEET = new BlockCoord(0, 64, 0);

    @Test
    void step_ReachableTopExists_CutsBeforeClimbing() {
        List<BlockCoord> tops = List.of(new BlockCoord(1, 66, 0), new BlockCoord(0, 90, 1));

        assertEquals(
                ClearScaffold.Action.WORK,
                ClearScaffold.step(FEET, tops, 3, false, 2).action(),
                "сначала срезать всё досягаемое с текущей высоты, потом подниматься (схема владельца 12.11: срезать,"
                        + " поставить блок под себя, снова срезать). Сломается: дух лезет к самой верхушке, оставляя низ."
                        + " Проверить при смене: ClearScaffoldWork.decide и ClearOrder.topmost");
    }

    @Test
    void step_AllTopsHigh_ClimbsToLowestOnly() {
        List<BlockCoord> tops = List.of(new BlockCoord(1, 70, 0), new BlockCoord(0, 95, 1));

        ClearScaffold.Decision decision = ClearScaffold.step(FEET, tops, 3, false, 2);
        assertEquals(
                new ClearScaffold.Decision(ClearScaffold.Action.CLIMB, 3),
                decision,
                "подъём ступенью — ровно до досягаемости самой НИЗКОЙ недосягаемой верхушки (70 − 64 − 3 = 3), а не"
                        + " к самой высокой (сервер 12.11: spirit-1 сразу поднялся на y=95 с 487 земли). Сломается:"
                        + " высокие столбы, расход земли, падения. Проверить при смене: MiningTuning.MAX_STAND_RISE");
    }

    @Test
    void step_TopsBelowOnOwnPillar_StepsDown() {
        BlockCoord feet = new BlockCoord(0, 80, 0);
        List<BlockCoord> tops = List.of(new BlockCoord(1, 70, 0));

        assertEquals(
                ClearScaffold.Action.DESCEND,
                ClearScaffold.step(feet, tops, 3, true, 2).action(),
                "наверху срезано, остальное ниже ног — спуск шагом по своему столбу, а не уход с высоты пешком."
                        + " Сломается: дух застревает на столбе или прыгает вниз. Проверить при смене: ScaffoldReach.stepDown");
    }

    @Test
    void step_TopsFar_WalksFirst() {
        List<BlockCoord> tops = List.of(new BlockCoord(40, 90, 0));

        assertEquals(
                ClearScaffold.Action.WORK,
                ClearScaffold.step(FEET, tops, 3, false, 2).action(),
                "верхушки дальше досягаемости колонки — сначала подойти, столб на месте не строится (сервер 12.11:"
                        + " дух поднял 26 блоков земли там, где стоял). Проверить при смене: параметр reach (COLUMN_REACH)");
    }

    @Test
    void step_NoTops_Done() {
        assertEquals(
                ClearScaffold.Action.DONE,
                ClearScaffold.step(FEET, List.of(), 3, false, 2).action(),
                "срезать нечего — расчистка окончена, столб разбирается в finish(). Сломается: вечная сессия");
    }
}

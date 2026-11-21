package com.theminespirit.core.mining;

import static com.theminespirit.core.mining.MineStepStandsTests.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineStepStandsTestsExtraTests {

    @Test
    void of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable() {
        BlockCoord from = new BlockCoord(-190, 71, -516);
        MineColumn entrance = new MineColumn(new BlockCoord(-189, 68, -518), MineColumn.PASSAGE_HEIGHT);
        CellWorld world = Observe7MineWorld.build();

        assertFalse(
                MineReach.canReach(from, entrance.stand(), world),
                "Предусловие observe7: дух на холме над шахтой, вход " + entrance.stand()
                        + " за стеной — стоянка за преградой, проходка встаёт на 75%"
                        + " Иначе тест of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable перестанет ловить эту поломку. При смене of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable проверь это утверждение.");

        List<BlockCoord> opening = MineApproach.opening(entrance, from, world);

        assertFalse(
                opening.isEmpty(),
                "Вход шахты обязан вскрываться: иначе дух не спускается и шахта не растёт"
                        + " При смене of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable проверь это утверждение.");
        for (BlockCoord cell : opening) {
            assertTrue(
                    cell.y() > entrance.floor().y(),
                    "Вскрытие входа ломает только стену над полом ступени, а не сам пол: " + cell
                            + " При смене of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable проверь это утверждение.");
            world.set(cell.x(), cell.y(), cell.z(), Cell.PASSABLE);
        }
        assertTrue(
                MineReach.canReach(from, entrance.stand(), world),
                "После вскрытия стены вход достижим без падения глубже " + MineReach.MAX_FALL + ": " + opening
                        + " Иначе тест of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable перестанет ловить эту поломку. При смене of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable проверь это утверждение.");

        MineStepStands.Choice choice = MineStepStands.of(entrance, null, from, world);

        assertTrue(
                choice.found(),
                "После вскрытия входа стоянка первой ступени обязана найтись, иначе шахта снова стоит: "
                        + choice.reason()
                        + " При смене of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable проверь это утверждение.");
        for (BlockCoord stand : choice.stands()) {
            assertTrue(
                    MineReach.canReach(from, stand, world),
                    "Стоянка " + stand + " обязана быть достижима от духа " + from
                            + " Иначе тест of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable перестанет ловить эту поломку. При смене of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable проверь это утверждение.");
            assertTrue(
                    MineStepStands.visibleFrom(entrance, stand, world),
                    "Стоянка " + stand + " обязана видеть ступень " + entrance.stand()
                            + " Иначе тест of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable перестанет ловить эту поломку. При смене of_LiveObserve7EntranceWall_OpeningMakesFirstStepReachable проверь это утверждение.");
        }
    }
}

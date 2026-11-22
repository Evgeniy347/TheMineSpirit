package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.ClearScaffold;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearScaffoldReachTests {
    private static final BlockCoord TOP = new BlockCoord(-175, 97, -517);

    @Test
    void decide_HighCellFarAway_ApproachFirstNoPillar() {
        ClearScaffold.Decision decision = ClearScaffold.decide(new BlockCoord(-128, 68, -485), TOP, 3, false, 2);

        assertEquals(
                ClearScaffold.Action.WORK,
                decision.action(),
                "Высокая клетка в 47 блоках — сначала подойти к её колонке; столб на месте не строится (сервер 12.11: "
                        + "дух поднял 26 блоков земли там, где стоял)"
                        + " Иначе тест decide_HighCellFarAway_ApproachFirstNoPillar перестанет ловить эту поломку. При смене decide_HighCellFarAway_ApproachFirstNoPillar проверь это утверждение.");
    }

    @Test
    void decide_HighCellBeside_Climbs() {
        ClearScaffold.Decision decision = ClearScaffold.decide(new BlockCoord(-176, 68, -517), TOP, 3, false, 2);

        assertEquals(
                ClearScaffold.Action.CLIMB,
                decision.action(),
                "Колонка рядом — подъём к верхней клетке"
                        + " Иначе тест decide_HighCellBeside_Climbs перестанет ловить эту поломку. При смене decide_HighCellBeside_Climbs проверь это утверждение.");
        assertEquals(
                26,
                decision.height(),
                "Подъём только до досягаемости верхней клетки (рост стоянки 3), а не на её уровень"
                        + " Иначе тест decide_HighCellBeside_Climbs перестанет ловить эту поломку. При смене decide_HighCellBeside_Climbs проверь это утверждение.");
    }

    @Test
    void bases_ThirdBlockAndDiagonal_StaysWithinCircularReach() {
        BlockCoord feet = new BlockCoord(-10, 64, 0);
        BlockCoord target = new BlockCoord(0, 70, 0);
        List<BlockCoord> bases = ClearScaffold.bases(feet, target, 3);
        assertTrue(
                bases.contains(new BlockCoord(-3, 64, 0)),
                "Основание в трёх блоках от цели допускается к проверке пути и видимости: прежний радиус два терял безопасные площадки"
                        + " Иначе тест bases_ThirdBlockAndDiagonal_StaysWithinCircularReach перестанет ловить эту поломку. При смене bases_ThirdBlockAndDiagonal_StaysWithinCircularReach проверь это утверждение.");
        assertFalse(
                bases.contains(new BlockCoord(3, 64, 3)),
                "Диагональ 3×3 вне радиуса три: квадратный поиск давал подмости, с которых дух не дотягивается"
                        + " Иначе тест bases_ThirdBlockAndDiagonal_StaysWithinCircularReach перестанет ловить эту поломку. При смене bases_ThirdBlockAndDiagonal_StaysWithinCircularReach проверь это утверждение.");
        assertTrue(
                bases.stream().noneMatch(cell -> cell.x() == target.x() && cell.z() == target.z()),
                "Столб нельзя строить в колонке цели: иначе потолок перекроет прыжок и дух застрянет"
                        + " При смене bases_ThirdBlockAndDiagonal_StaysWithinCircularReach проверь это утверждение.");
        assertEquals(
                new BlockCoord(-3, 64, 0),
                bases.get(0),
                "Ближайшее основание проверяется первым, а не дальняя сторона цели; порядок влияет на лишние обходы"
                        + " Иначе тест bases_ThirdBlockAndDiagonal_StaysWithinCircularReach перестанет ловить эту поломку. При смене bases_ThirdBlockAndDiagonal_StaysWithinCircularReach проверь это утверждение.");
        assertEquals(
                196,
                bases.size(),
                "Радиус три даёт 28 колонок на семи уровнях: ограничение удерживает стоимость поиска и не расширяется до всего мира"
                        + " Иначе тест bases_ThirdBlockAndDiagonal_StaysWithinCircularReach перестанет ловить эту поломку. При смене bases_ThirdBlockAndDiagonal_StaysWithinCircularReach проверь это утверждение.");
    }
}

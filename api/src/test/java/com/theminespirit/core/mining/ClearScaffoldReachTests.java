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
                        + "дух поднял 26 блоков земли там, где стоял)");
    }

    @Test
    void decide_HighCellBeside_Climbs() {
        ClearScaffold.Decision decision = ClearScaffold.decide(new BlockCoord(-176, 68, -517), TOP, 3, false, 2);

        assertEquals(ClearScaffold.Action.CLIMB, decision.action(), "Колонка рядом — подъём к верхней клетке");
        assertEquals(
                26,
                decision.height(),
                "Подъём только до досягаемости верхней клетки (рост стоянки 3), а не на её уровень");
    }

    @Test
    void bases_ThirdBlockAndDiagonal_StaysWithinCircularReach() {
        BlockCoord feet = new BlockCoord(-10, 64, 0);
        BlockCoord target = new BlockCoord(0, 70, 0);
        List<BlockCoord> bases = ClearScaffold.bases(feet, target, 3);
        assertTrue(
                bases.contains(new BlockCoord(-3, 64, 0)),
                "Основание в трёх блоках от цели допускается к проверке пути и видимости: прежний радиус два терял безопасные площадки");
        assertFalse(
                bases.contains(new BlockCoord(3, 64, 3)),
                "Диагональ 3×3 вне радиуса три: квадратный поиск давал подмости, с которых дух не дотягивается");
        assertTrue(
                bases.stream().noneMatch(cell -> cell.x() == target.x() && cell.z() == target.z()),
                "Столб нельзя строить в колонке цели: иначе потолок перекроет прыжок и дух застрянет");
        assertEquals(
                new BlockCoord(-3, 64, 0),
                bases.get(0),
                "Ближайшее основание проверяется первым, а не дальняя сторона цели; порядок влияет на лишние обходы");
        assertEquals(
                196,
                bases.size(),
                "Радиус три даёт 28 колонок на семи уровнях: ограничение удерживает стоимость поиска и не расширяется до всего мира");
    }

    @Test
    void surfaceBases_HillAboveSpiritLevel_ReachesTargetSurface() {
        BlockCoord feet = new BlockCoord(-10, 64, 0);
        BlockCoord target = new BlockCoord(0, 76, 0);
        BlockCoord hill = new BlockCoord(-2, 70, 0);
        assertTrue(
                ClearScaffold.bases(feet, target, 3).stream().allMatch(cell -> cell.y() <= 67),
                "Прежний поиск держится за уровень ног духа: поверхность у цели выше полосы «ноги ± радиус» в него "
                        + "не попадает. Живой случай: столб -175,92..95,-517 оставался «на 24 выше духа»");
        int[] checks = {0};
        List<BlockCoord> bases = ClearScaffold.surfaceBases(feet, target, 3, cell -> {
            checks[0]++;
            return cell.equals(hill);
        });
        assertEquals(
                List.of(hill),
                bases,
                "Основание подмостей — верхняя стоянка столбца у цели, даже если она выше духа: с неё дух ставит "
                        + "короткий столб и достаёт клетку");
        assertTrue(
                checks[0] <= 28 * (target.y() - feet.y()),
                "Поиск поверхности читает не больше одной стоянки на уровень каждого из 28 столбцов. Чтений: "
                        + checks[0]);
    }
}

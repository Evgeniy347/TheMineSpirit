package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandSpotsReachTests {
    @Test
    void of_ReachDownFour_FindsStandFourBelowTarget() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.PASSABLE)
                .set(1, -1, 0, Cell.PASSABLE)
                .set(1, -2, 0, Cell.PASSABLE)
                .set(1, -3, 0, Cell.PASSABLE)
                .set(1, -4, 0, Cell.PASSABLE)
                .set(1, -5, 0, Cell.SOLID);

        List<BlockCoord> spots =
                StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0), new SpiritReach(3, 15, 4));

        assertTrue(
                spots.contains(new BlockCoord(1, -4, 0)),
                "Глубина стоянки — из досягаемости духа, а не из своего числа: с «вниз 4» верхушка столба "
                        + "достаётся со стоянки на 4 ниже. Стоянки: " + spots);
    }

    @Test
    void of_ReachDownTwo_RejectsStandThreeBelowTarget() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.PASSABLE)
                .set(1, -1, 0, Cell.PASSABLE)
                .set(1, -2, 0, Cell.PASSABLE)
                .set(1, -3, 0, Cell.PASSABLE)
                .set(1, -4, 0, Cell.SOLID);

        List<BlockCoord> spots =
                StandSpots.of(new BlockCoord(0, 0, 0), world, new BlockCoord(0, 0, 0), new SpiritReach(3, 15, 2));

        assertTrue(
                !spots.contains(new BlockCoord(1, -3, 0)),
                "С «вниз 2» стоянка на три ниже — вне досягаемости, её нельзя предлагать: иначе дух выбирает "
                        + "клетку, с которой не достанет блок. Стоянки: " + spots);
    }

    @Test
    void maxOffsetSquared_ReachDownZero_CoversUpSideStands() {
        CellWorld world = new CellWorld().fill(0, -2, -2, 2, 2, Cell.SOLID);
        BlockCoord target = new BlockCoord(0, 0, 0);
        SpiritReach reach = new SpiritReach(3, 15, 0);

        List<BlockCoord> spots = StandSpots.of(target, world, target, reach);
        long maxOffset = spots.stream()
                .mapToLong(stand -> (long) (stand.x() - target.x()) * (stand.x() - target.x())
                        + (long) (stand.y() - target.y()) * (stand.y() - target.y())
                        + (long) (stand.z() - target.z()) * (stand.z() - target.z()))
                .max()
                .orElseThrow();

        assertEquals(
                2,
                StandSpots.maxOffsetSquared(reach),
                "При «вниз 0» предел смещения не ноль и не единица: стоянки сбоку-сверху дают 1² + 1² = 2. "
                        + "Меньший предел заставит планировщик бросить ближнюю цель, до которой дух достаёт");
        assertEquals(
                2,
                maxOffset,
                "Предел смещения обязан покрывать все стоянки, которые выдаёт поиск: иначе ранний выход "
                        + "планировщика теряет достижимую цель. Стоянки: " + spots);
    }

    @Test
    void maxOffsetSquared_ReachDownThreeAndOne_MatchesSquaredBound() {
        assertEquals(
                10,
                StandSpots.maxOffsetSquared(new SpiritReach(3, 15, 3)),
                "Сбоку на глубину досягаемости: 3² + 1² = 10 — по этому числу планировщик решает, может ли "
                        + "ближняя цель со стоянкой побить текущую");
        assertEquals(
                2,
                StandSpots.maxOffsetSquared(new SpiritReach(3, 15, 1)),
                "На один ниже предел равен 1² + 1² = 2: своё число вместо досягаемости снова сузило бы окно");
    }

    @Test
    void of_WithoutExplicitReach_UsesCurrentSpiritReach() {
        CellWorld world = new CellWorld()
                .set(0, 0, 0, Cell.SOLID)
                .set(0, -4, 0, Cell.SOLID)
                .set(1, -4, 0, Cell.SOLID);
        BlockCoord target = new BlockCoord(0, 0, 0);
        BlockCoord from = new BlockCoord(0, 0, 0);

        assertEquals(
                StandSpots.of(target, world, from, SpiritReach.current()),
                StandSpots.of(target, world, from),
                "Поиск стоянки без явной досягаемости обязан брать её из SpiritReach (build.json): своя копия "
                        + "глубины снова разойдётся с данными и оставит верхушку столба «без стоянки»");
    }
}

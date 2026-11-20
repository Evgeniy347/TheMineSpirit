package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FleePlannerTests {
    private static final BlockCoord CORNER = new BlockCoord(0, 0, 0);

    private static AsciiWorld world(String... layers) {
        return AsciiWorld.of(CORNER, Cell.SOLID, layers);
    }

    private static FleePlanner.Plan plan(AsciiWorld world, double minDistance) {
        return FleePlanner.plan(
                world.marker('@'), List.of(new FleePlanner.Threat(world.marker('1'), minDistance)), world);
    }

    private static ReflexAction.Flee flee(int x, int y, int z) {
        return new ReflexAction.Flee(new BlockCoord(x, y, z));
    }

    @Test
    void plan_CorridorThreatEast_FleesWestToRequiredDistance() {
        AsciiWorld world = world("###########", "#...@.1...#", "#.........#");

        FleePlanner.Plan plan = plan(world, 5);

        assertEquals(
                flee(1, 1, 0),
                plan.action(),
                "Угроза в x=6, держать 5: ближайшая клетка с расстоянием 5 — x=1, а не любая западная, "
                        + "иначе план ведёт духа лишними шагами. При смене поиска проверь коридор с угрозой на востоке");
    }

    @Test
    void plan_OpenRoom_StopsAtNearestQualifyingCell() {
        String floor = String.join("/", Collections.nCopies(9, "#########"));
        String head = String.join("/", Collections.nCopies(9, "........."));
        String feet = "........./........./........./........./1...@..../........./........./........./.........";
        AsciiWorld world = world(floor, feet, head);

        FleePlanner.Plan plan = plan(world, 7);

        assertEquals(
                flee(7, 1, 4),
                plan.action(),
                "В открытой комнате ближайшая клетка на 7 от угрозы в x=0 — x=7 на той же линии, три шага от духа, "
                        + "иначе план уводит дальше необходимого. При смене обхода проверь открытую комнату");
    }

    @Test
    void plan_DeadEndWithNoQualifyingCell_FleesToTheFarthestCell() {
        AsciiWorld world = world("###########", "#..@..1...#", "#.........#");

        FleePlanner.Plan plan = plan(world, 7);

        assertEquals(
                flee(1, 1, 0),
                plan.action(),
                "Если нужных 7 блоков нет, дух уходит в самую дальнюю клетку тупика (x=1, расстояние 5), а не стоит "
                        + "на месте, иначе бегство бесполезно. При смене плана проверь тупик без достаточной дистанции");
    }

    @Test
    void plan_ThreatAtTheMouthOfTheDeadEnd_StopsMoving() {
        AsciiWorld world = world("########", "#@.1...#", "#......#");

        FleePlanner.Plan plan = plan(world, 5);

        assertEquals(
                new ReflexAction.StopMoving(),
                plan.action(),
                "Дух в тупике, угроза в двух блоках у выхода: улучшить нечем, а пройти мимо угрозы нельзя, поэтому STOP_MOVING, "
                        + "иначе дух идёт в крипера. При смене плана проверь тупик с угрозой у выхода");
    }

    @Test
    void plan_DropOfTwo_IsUsedToReachTheGoal() {
        AsciiWorld world = world("########", "###....#", "###....#", "#12.....", "#.......");
        BlockCoord start = new BlockCoord(2, 3, 0);
        BlockCoord threat = new BlockCoord(1, 3, 0);

        FleePlanner.Plan plan = FleePlanner.plan(start, List.of(new FleePlanner.Threat(threat, 4)), world);

        assertEquals(
                flee(5, 1, 0),
                plan.action(),
                "Платформа выше земли на 2 блока: спуск с неё разрешён и ведёт к клетке на 4 от угрозы, "
                        + "иначе дух застревает на платформе. При смене шагов проверь спуск на 2");
    }

    @Test
    void plan_DropOfThree_IsNotUsed() {
        AsciiWorld world = world("########", "###....#", "###....#", "###....#", "#12.....", "#.......");
        BlockCoord start = new BlockCoord(2, 4, 0);
        BlockCoord threat = new BlockCoord(1, 4, 0);

        FleePlanner.Plan plan = FleePlanner.plan(start, List.of(new FleePlanner.Threat(threat, 4)), world);

        assertEquals(
                new ReflexAction.StopMoving(),
                plan.action(),
                "Спуск на 3 блока ранит духа: рефлекс не прыгает с высоты, даже убегая, иначе урон от падения "
                        + "заменяет урон от взрыва. При смене шагов проверь спуск на 3");
    }

    @Test
    void plan_StepUpOfOne_IsUsedToReachTheGoal() {
        AsciiWorld world = world("###########", "#.....#####", "#..........", "#..........");
        BlockCoord start = new BlockCoord(3, 1, 0);
        BlockCoord threat = new BlockCoord(1, 1, 0);

        FleePlanner.Plan plan = FleePlanner.plan(start, List.of(new FleePlanner.Threat(threat, 7)), world);

        assertEquals(
                flee(8, 2, 0),
                plan.action(),
                "Ступень в один блок преодолима: цель на 7 от угрозы лежит за ступенью на высоте y=2, "
                        + "иначе план застревает у любой ступени. При смене шагов проверь подъём на 1");
    }

    @Test
    void plan_StepUpOfTwo_StopsBeforeTheWall() {
        AsciiWorld world = world("###########", "#.....#####", "#.....#####", "#..........", "#..........");
        BlockCoord start = new BlockCoord(3, 1, 0);
        BlockCoord threat = new BlockCoord(1, 1, 0);

        FleePlanner.Plan plan = FleePlanner.plan(start, List.of(new FleePlanner.Threat(threat, 7)), world);

        assertEquals(
                flee(5, 1, 0),
                plan.action(),
                "Стена в два блока непреодолима: дух уходит в самую дальнюю клетку перед ней, иначе поиск "
                        + "перепрыгивает стену. При смене шагов проверь подъём на 2");
    }

    @Test
    void plan_LavaOnTheWay_IsNotCrossed() {
        AsciiWorld world = world("###########", "#1..@..L...#", "#..........#");

        FleePlanner.Plan plan = plan(world, 8);

        assertEquals(
                flee(5, 1, 0),
                plan.action(),
                "Лава в x=7 закрывает восток: клетки за ней (x=9, расстояние 8) недосягаемы, а x=6 стоит вплотную к лаве и не годится для стоянки: дух уходит в x=5, "
                        + "иначе план ведёт через лаву. При смене проверки стоянок сверь соседей клетки");
    }

    @Test
    void plan_LimitedRadius_StopsAtTheRadiusEvenWhenGoalIsFarther() {
        AsciiWorld world = world("#####################", "#1@.................#", "#...................#");

        FleePlanner.Plan limited = FleePlanner.plan(
                world.marker('@'), List.of(new FleePlanner.Threat(world.marker('1'), 10)), world, 200, 3);
        FleePlanner.Plan wide = FleePlanner.plan(
                world.marker('@'), List.of(new FleePlanner.Threat(world.marker('1'), 10)), world, 200, 12);

        assertEquals(
                flee(5, 1, 0),
                limited.action(),
                "Радиус 3: дух уходит на край радиуса (x=5), цель на 10 от угрозы недосягаема, иначе поиск выходит "
                        + "за заданный радиус. При смене радиуса проверь обрезку поиска");
        assertEquals(
                flee(11, 1, 0),
                wide.action(),
                "Радиус 12: цель на 10 от угрозы в x=11 достижима, иначе большой радиус не используется. "
                        + "При смене обрезки проверь пару радиусов 3 и 12");
    }

    @Test
    void plan_NoThreats_FleesToTheOriginCell() {
        AsciiWorld world = world("#####", "#.@.#", "#...#");

        FleePlanner.Plan plan = FleePlanner.plan(world.marker('@'), List.of(), world);

        assertEquals(
                flee(2, 1, 0),
                plan.action(),
                "Без угроз требования выполнены на месте: план остаётся в клетке духа, иначе он бежит без причины. "
                        + "При смене плана проверь пустой список угроз");
    }

    @Test
    void plan_SqueezedBetweenTwoThreats_StopsMoving() {
        AsciiWorld world = world("###########", "#1...@...2#", "#.........#");

        FleePlanner.Plan plan = FleePlanner.plan(
                world.marker('@'),
                List.of(new FleePlanner.Threat(world.marker('1'), 6), new FleePlanner.Threat(world.marker('2'), 6)),
                world);

        assertEquals(
                new ReflexAction.StopMoving(),
                plan.action(),
                "Между двумя угрозами любой шаг приближает к одной из них: стоять лучше, чем бежать в угрозу, "
                        + "иначе дух сам сокращает дистанцию. При смене плана проверь сжатие с двух сторон");
    }

    @Test
    void plan_ThreatBehindWall_StillCountsByStraightDistance() {
        String floor = "#############/#############/#############";
        String feet = "#.....@.....#/#############/#.....1.....#";
        String head = "#...........#/#############/#...........#";
        AsciiWorld walled = world(floor, feet, head);

        FleePlanner.Plan plan = plan(walled, 5);

        assertEquals(
                flee(11, 1, 0),
                plan.action(),
                "Угроза за стеной в двух блоках опасна по прямой: дух уходит вдоль стены на 5 по x, восточная клетка проверяется первой, "
                        + "иначе выбор клетки зависит от порядка обхода. При смене проверки видимости сверь взрыв сквозь стену");
    }

    @Test
    void plan_Enclosed_StopsMoving() {
        AsciiWorld closed = world("###/###/###", "###/#@#/#1#", "###/#.#/###");

        FleePlanner.Plan plan = plan(closed, 5);

        assertEquals(
                new ReflexAction.StopMoving(),
                plan.action(),
                "В закрытой клетке идти некуда: STOP_MOVING, а не исключение, иначе рефлекс роняет тик. "
                        + "При смене плана проверь полностью закрытую клетку");
        assertEquals(
                1,
                plan.visited(),
                "Посещена только исходная клетка: иначе поиск тратит бюджет на клетки без выхода. "
                        + "При смене обхода проверь закрытую клетку");
    }
}

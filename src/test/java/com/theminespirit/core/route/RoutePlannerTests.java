package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.GoalBlock;
import com.theminespirit.core.movement.GoalNear;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoutePlannerTests {
    private static final RouteRules RULES = RouteRules.load();
    private static final BlockCoord ORIGIN = new BlockCoord(0, 0, 0);

    @Test
    void table_Defaults_TicksLikeWalkingSpirit() {
        assertEquals(
                new RouteRules(4.633, 9.091, 8.511, 2.0, 20.0, 2.0, 1.5, 3, 12, 40000, 1.0, 5),
                RULES,
                "Цены ходов — в тиках (ходьба 20/4.317, вода 20/2.2, лестница 20/2.35), падение на сушу до 3, бюджет "
                        + "40 000 узлов: числа — movement/route.json, при смене пересчитать живой снимок");
    }

    @Test
    void plan_PitThreeByThreeDepthFiveWithoutBlocks_DigsStairsOut() {
        GridWorld world = GridWorld.flat(0).fill(-1, -5, -1, 1, -1, 1, RouteCell.AIR);
        BlockCoord bottom = new BlockCoord(0, -5, 0);

        Route route = RoutePlanner.plan(world, bottom, new GoalBlock(new BlockCoord(6, 0, 0)), RULES, 0);

        assertFound(world, bottom, route, "яма 3×3 глубиной 5 без блоков: дух выкапывает ступени в стенке");
        assertEquals(0, route.places(), "Без блоков в инвентаре маршрут не может ничего ставить");
        assertTrue(route.count(RouteStep.Action.BREAK) > 0, "Из ямы глубже прыжка без блоков — только ступени");
    }

    @Test
    void plan_PitThreeByThreeDepthFiveWithBlocks_StaysValidAndNotDearer() {
        GridWorld world = GridWorld.flat(0).fill(-1, -5, -1, 1, -1, 1, RouteCell.AIR);
        BlockCoord bottom = new BlockCoord(0, -5, 0);
        GoalBlock goal = new GoalBlock(new BlockCoord(6, 0, 0));

        Route dig = RoutePlanner.plan(world, bottom, goal, RULES, 0);
        Route build = RoutePlanner.plan(world, bottom, goal, RULES, 16);

        assertFound(world, bottom, build, "яма с блоками в инвентаре");
        assertTrue(
                build.cost() <= dig.cost() + 1e-9,
                "С блоками в запасе маршрут не дороже, чем без них: A* выбирает дешевле из большего набора ходов ("
                        + build.cost() + " против " + dig.cost() + ")");
    }

    @Test
    void plan_WallOfDirt_BreaksTwoBlocksThrough() {
        GridWorld world = GridWorld.flat(0).fill(5, 0, -40, 5, 3, 40, GridWorld.DIRT);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(new BlockCoord(10, 0, 0)), RULES, 0);

        assertFound(world, ORIGIN, route, "стена 4 блока высотой поперёк пути");
        assertEquals(
                2, route.count(RouteStep.Action.BREAK), "Проход сквозь стену — ломка ровно двух блоков: тело и голова");
    }

    @Test
    void plan_UnbreakableWallWithGap_WalksAroundWithoutBreaking() {
        GridWorld world =
                GridWorld.flat(0).fill(5, 0, -40, 5, 3, 40, RouteCell.BLOCKED).fill(5, 0, 6, 5, 1, 6, RouteCell.AIR);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(new BlockCoord(10, 0, 0)), RULES, 0);

        assertFound(world, ORIGIN, route, "неразрушимая стена с проёмом");
        assertEquals(0, route.count(RouteStep.Action.BREAK), "Неразрушимое не ломается: путь идёт в проём");
    }

    @Test
    void plan_CliffTenHigh_NeverFallsMoreThanThree() {
        GridWorld world = GridWorld.flat(0).fill(5, -10, -40, 30, -1, 40, RouteCell.AIR);
        BlockCoord below = new BlockCoord(10, -10, 0);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(below), RULES, 0);

        assertFound(world, ORIGIN, route, "обрыв 10 блоков: спуск копанием, без падения больше 3");
    }

    @Test
    void plan_CliffOverWater_JumpsIntoWater() {
        GridWorld world = GridWorld.flat(0)
                .fill(5, -10, -40, 30, -1, 40, RouteCell.AIR)
                .fill(5, -12, -40, 30, -10, 40, RouteCell.WATER);
        BlockCoord pool = new BlockCoord(10, -10, 0);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(pool), RULES, 0);

        assertFound(world, ORIGIN, route, "обрыв над водой");
        assertEquals(
                0,
                route.count(RouteStep.Action.BREAK),
                "В воду с 10 блоков можно прыгать (maxWaterFall): копать незачем");
    }

    @Test
    void plan_VineOneStepAbove_JumpsInWithoutBlocks() {
        GridWorld world = GridWorld.flat(0).fill(0, 1, 0, 0, 1, 0, RouteCell.CLIMB);
        BlockCoord vine = new BlockCoord(0, 1, 0);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(vine), RULES, 0);

        assertFound(world, ORIGIN, route, "лиана в клетке на шаг выше без единого блока в запасе");
        assertEquals(
                0,
                route.places(),
                "Лиана — не повод ставить ступень: клетка climbable открыта, дух прыгает в неё с земли");
        assertEquals(
                RouteStep.Action.WALK,
                route.steps().get(route.steps().size() - 1).action(),
                "Последний шаг к лиане — ходьба с прыжком, а не столб: иначе исполнитель ставит блок под себя");
    }

    @Test
    void plan_RiverWithoutBlocks_SwimsAcross() {
        GridWorld world = GridWorld.flat(0).fill(3, -3, -40, 7, -1, 40, RouteCell.WATER);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(new BlockCoord(10, 0, 0)), RULES, 0);

        assertFound(world, ORIGIN, route, "река шириной 5 без блоков");
    }

    @Test
    void plan_LavaMoat_KeepsOneCellFromLava() {
        GridWorld world = GridWorld.flat(0)
                .fill(3, -1, -40, 7, -1, 40, RouteCell.LAVA)
                .fill(3, -1, 18, 7, -1, 22, GridWorld.DIRT);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(new BlockCoord(10, 0, 0)), RULES, 0);

        assertFound(world, ORIGIN, route, "ров с лавой и перешеек шириной 5 в стороне");
        for (RouteStep step : route.steps()) {
            BlockCoord cell = step.cell();
            int lava = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 0; dy++) {
                        if (Math.abs(dx) + Math.abs(dz) <= 1
                                && RouteCell.kind(world.cell(cell.x() + dx, cell.y() + dy, cell.z() + dz))
                                        == RouteCell.LAVA) {
                            lava++;
                        }
                    }
                }
            }
            assertEquals(
                    0,
                    lava,
                    "Шаг " + step + " в лаве или вплотную к ней: лава — запрет, путь держится не ближе клетки "
                            + "(как обычная навигация духа), обход — по перешейку");
        }
    }

    @Test
    void plan_TinyNodeBudget_StopsWithinBudget() {
        RouteRules tiny = new RouteRules(4.633, 9.091, 8.511, 2.0, 20.0, 2.0, 1.5, 3, 12, 50, 1.0, 5);
        GridWorld world = GridWorld.flat(0).fill(5, 0, -400, 5, 3, 400, RouteCell.BLOCKED);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalBlock(new BlockCoord(10, 0, 0)), tiny, 0);

        assertTrue(
                route.status() != Route.Status.FOUND && route.nodes() <= tiny.maxNodes(),
                "Бюджет узлов — потолок: за " + tiny.maxNodes() + " узлов цель за стеной не находится, создано "
                        + route.nodes());
    }

    @Test
    void plan_SealedShaftOverVoid_DoesNotReturnRouteWithVetoedFloorBreak() {
        GridWorld world = GridWorld.flat(0)
                .fill(-1, 0, -1, 1, 1, 1, RouteCell.BLOCKED)
                .fill(0, 0, 0, 0, 1, 0, RouteCell.AIR)
                .fill(0, -3, 0, 0, -2, 0, RouteCell.AIR);
        BlockCoord start = new BlockCoord(0, 0, 0);

        Route route = RoutePlanner.plan(world, start, new GoalBlock(new BlockCoord(0, -3, 0)), RULES, 0);

        assertEquals(
                Route.Status.NONE,
                route.status(),
                "В шахте 1×1 единственный путь — сломать пол и упасть на 3 блока в пустоту: BreakVeto с MAX_DROP=2 "
                        + "запрещает такую ломку, поэтому FOUND с этим шагом — цикл «маршрут найден → дух упадёт, "
                        + "сначала положите опору»");
        assertEquals(
                0,
                route.count(RouteStep.Action.BREAK),
                "Маршрут не должен нести ломку пола под ногами, которую исполнитель тут же отвергает: иначе дух "
                        + "стоит в яме, а планировщик каждую секунду снова выдаёт невыполнимый FOUND");
    }

    @Test
    void plan_FloorBreakIntoVoidWithLegalDetour_ReplansWithoutBreak() {
        GridWorld world = GridWorld.flat(0)
                .fill(0, -1, 0, 0, -1, 0, RouteCell.solid(1))
                .fill(0, -3, 0, 0, -2, 0, RouteCell.AIR)
                .fill(1, -3, 0, 5, -2, 0, RouteCell.AIR)
                .fill(5, -1, 0, 5, 0, 0, RouteCell.AIR);
        BlockCoord start = new BlockCoord(0, 0, 0);

        Route route = RoutePlanner.plan(world, start, new GoalBlock(new BlockCoord(0, -3, 0)), RULES, 0);

        assertFound(world, start, route, "яма с тонким полом над пустотой и обходом по коридору");
    }

    @Test
    void plan_HundredBlocksOfPlain_ExpandsAboutStraightLine() {
        GridWorld world = GridWorld.flat(0);

        Route route = RoutePlanner.plan(world, ORIGIN, new GoalNear(new BlockCoord(100, 0, 0), 1), RULES, 0);

        assertFound(world, ORIGIN, route, "равнина 100 блоков");
        assertTrue(
                route.nodes() < 2_000 && route.reads() < 20_000,
                "На равнине A* идёт по прямой: узлов " + route.nodes() + ", чтений клеток " + route.reads()
                        + " — рост значит, что эвристика или цены ходов перестали вести к цели");
    }

    static void assertFound(RouteWorld world, BlockCoord start, Route route, String scene) {
        assertEquals(Route.Status.FOUND, route.status(), "Маршрут не найден: " + scene + ", узлов " + route.nodes());
        Optional<String> broken = RouteReplay.violation(world, start, route);
        assertEquals(Optional.empty(), broken, "План нарушает правила движения (" + scene + ")");
        Optional<Route.Veto> veto = RouteBreakAudit.firstVeto(world, start, route);
        assertEquals(
                Optional.empty(),
                veto,
                "План содержит ломку, запрещённую BreakVeto (MAX_DROP=2), — исполнитель её отвергнет, и дух "
                        + "застрянет в цикле FOUND→отказ (" + scene + ")");
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.navigation.NavContext;
import com.theminespirit.core.navigation.NavGoalBlock;
import com.theminespirit.core.navigation.NavRouteSteps;
import com.theminespirit.core.navigation.NavSearchStatus;
import com.theminespirit.core.navigation.NavSegmentedRoute;
import com.theminespirit.core.navigation.NavSegmentedSession;
import com.theminespirit.core.navigation.NavSettings;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.LevelNavWorld;
import com.theminespirit.neoforge.movement.RouteWalk;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;

final class RouteGapScene {
    private record Crossing(SpiritEntity spirit, RouteWalk walk, float health, BlockCoord target) {}

    private final GameTestHelper helper;
    private final List<Crossing> crossings = new ArrayList<>();
    private SpiritEntity voidSpirit;
    private RouteWalk voidWalk;
    private BlockPos voidStart;
    private int ticks;

    RouteGapScene(GameTestHelper helper) {
        this.helper = helper;
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                for (int y = 0; y <= 9; y++) {
                    helper.setBlock(x, y, z, y == 0 || (y <= 5 && x != 5 && z != 5) ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        add(new BlockPos(4, 6, 3), new BlockPos(6, 6, 3));
        add(new BlockPos(6, 6, 7), new BlockPos(4, 6, 7));
        add(new BlockPos(3, 6, 4), new BlockPos(3, 6, 6));
        add(new BlockPos(7, 6, 6), new BlockPos(7, 6, 4));
        addVoid();
        helper.onEachTick(this::tick);
    }

    static RouteGapScene start(GameTestHelper helper, BlockPos base, int floorSize, int clearHeight) {
        if (helper.getBlockEntity(base) instanceof Container container) {
            container.clearContent();
        }
        DemoAuditGameTests.clearArea(helper, floorSize, clearHeight);
        return new RouteGapScene(helper);
    }

    private void add(BlockPos start, BlockPos destination) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), start);
        spirit.setDemo(true);
        BlockCoord from = MovementScene.coord(helper, start);
        BlockCoord target = MovementScene.coord(helper, destination);
        LevelNavWorld world = new LevelNavWorld(helper.getLevel());
        NavSettings settings = new NavSettings(2000, 2000, 200_000, 50, 3, 12, 2, 20, 2, true, false);
        NavSegmentedSession session = new NavSegmentedSession(
                new NavContext(world, settings, 0),
                from,
                new NavGoalBlock(target.x(), target.y(), target.z()),
                32,
                6,
                0.5);
        NavSegmentedRoute planned = session.step(Long.MAX_VALUE);
        helper.assertTrue(
                planned != null && planned.status() == NavSearchStatus.FOUND,
                "Через щель шириной блок существует прыжок: планировщик обязан найти его без постановки опор");
        Route route = NavRouteSteps.toRoute(planned, world, target);
        crossings.add(new Crossing(spirit, new RouteWalk(spirit, route, target, false), spirit.getHealth(), target));
    }

    private void addVoid() {
        BlockPos start = new BlockPos(4, 6, 2);
        voidStart = helper.absolutePos(start);
        voidSpirit = helper.spawn(ModEntities.SPIRIT.get(), start);
        voidSpirit.setDemo(true);
        BlockCoord cell = MovementScene.coord(helper, new BlockPos(5, 5, 2));
        RouteStep step = new RouteStep(RouteStep.Action.WALK, cell);
        Route route = new Route(Route.Status.FOUND, List.of(step), 1, cell, 1, 0, 0, List.of());
        voidWalk = new RouteWalk(voidSpirit, route, cell, false);
    }

    private void tick() {
        if (++ticks <= MovementScene.SETTLE_TICKS) {
            return;
        }
        if (ticks == MovementScene.SETTLE_TICKS + 1) {
            helper.assertTrue(
                    voidWalk.tick() == RouteWalk.State.FAILED,
                    "Шаг спуска в пустоту отклоняется до прыжка: дух не срывается в яму наудачу, итог "
                            + voidWalk.failure());
            helper.assertTrue(
                    voidSpirit.getHealth() == voidSpirit.getMaxHealth() && voidSpirit.getY() >= voidStart.getY() - 0.1,
                    "Дух остался на опоре у края, а не упал в яму: " + voidSpirit.position() + ", здоровье "
                            + voidSpirit.getHealth());
        }
        for (Crossing crossing : crossings) {
            SpiritEntity spirit = crossing.spirit();
            helper.assertTrue(
                    spirit.getHealth() == crossing.health()
                            && spirit.getY() >= crossing.target().y(),
                    "Прыжок через щель над ямой в пять блоков не превращается в шаг и падение: дух в "
                            + spirit.position() + ", цель " + crossing.target() + ", здоровье " + spirit.getHealth());
            helper.assertTrue(
                    crossing.walk().tick() != RouteWalk.State.FAILED,
                    "Маршрут через щель исполняется телом без застревания: "
                            + crossing.walk().failure());
        }
    }

    void assertDone() {
        helper.assertTrue(
                ticks > MovementScene.SETTLE_TICKS, "Проверка начинается после приземления на стартовые опоры");
        for (Crossing crossing : crossings) {
            SpiritEntity spirit = crossing.spirit();
            BlockCoord target = crossing.target();
            helper.assertTrue(
                    spirit.onGround()
                            && spirit.blockPosition().equals(new BlockPos(target.x(), target.y(), target.z())),
                    "Все четыре направления прыжка завершаются на противоположной опоре, а не в воздухе: "
                            + spirit.position() + " к " + target);
        }
        crossings.forEach(crossing -> crossing.spirit().discard());
        voidSpirit.discard();
    }
}

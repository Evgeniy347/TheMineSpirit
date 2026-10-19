package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementPatrolGameTests {
    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final List<BlockPos> CORNERS = List.of(new BlockPos(1, 1, 1), new BlockPos(7, 1, 1),
            new BlockPos(7, 1, 7), new BlockPos(1, 1, 7));
    private static final int WALLED_CORNER = 1;
    private static final int LOOP_LIMIT_TICKS = 350;
    private static final int PATH_BUILDS_PER_POINT = 3;
    private static final int CANCEL_AFTER_TICKS = 15;
    private static final int SETTLE_AFTER_CANCEL_TICKS = 5;
    private static final int OBSERVE_AFTER_CANCEL_TICKS = 15;
    private static final double STANDS_STILL = 0.1;
    private static final int POINTS_BEFORE_RELOAD = 2;

    private MovementPatrolGameTests() {
    }

    @GameTest(template = "arena", batch = "move_patrol_loop", timeoutTicks = 400)
    public static void patrol_LoopOverFourCorners_VisitsAllInOrderAndSucceedsWithFewPathBuilds(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, CENTER);
        SpiritRun run = SpiritRun.launch(helper, spirit, patrolCall(helper, 1), MovementScene.SETTLE_TICKS);
        Visits visits = new Visits(helper, spirit);
        helper.onEachTick(visits::observe);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "четыре достижимых угла за один круг: patrol обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", посещено " + visits.order());
            helper.assertTrue(visits.order().equals(List.of(0, 1, 2, 3)),
                    "углы обязаны посещаться по порядку маршрута 0, 1, 2, 3, фактически " + visits.order());
            helper.assertTrue(run.spiritTicks() <= LOOP_LIMIT_TICKS,
                    "круг по периметру обязан уложиться в " + LOOP_LIMIT_TICKS + " тиков духа, ушло "
                            + run.spiritTicks());
            int budget = CORNERS.size() * PATH_BUILDS_PER_POINT;
            helper.assertTrue(spirit.pathBuilds() <= budget,
                    "путь строится по разу на точку и перестраивается по политике обновления: не больше " + budget
                            + " построений на круг, построено " + spirit.pathBuilds());
            helper.assertTrue(visits.maxBuildsInOneTick() <= 1,
                    "за один тик скрипт строит не больше одного пути, максимум за тик: " + visits.maxBuildsInOneTick());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_patrol_wall", timeoutTicks = 400)
    public static void patrol_OneCornerSealedInWalls_IsSkippedAndCountedWhileOthersAreVisited(GameTestHelper helper) {
        MovementScene.prepare(helper);
        BlockPos sealed = CORNERS.get(WALLED_CORNER);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(sealed.offset(dx, 0, dz), Blocks.STONE);
                    helper.setBlock(sealed.offset(dx, 1, dz), Blocks.STONE);
                }
            }
        }
        SpiritEntity spirit = MovementScene.spirit(helper, CENTER);
        SpiritRun run = SpiritRun.launch(helper, spirit, patrolCall(helper, 1), MovementScene.SETTLE_TICKS);
        Visits visits = new Visits(helper, spirit);
        helper.onEachTick(visits::observe);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "недостижимая точка пропускается, остальные проходятся: patrol обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", посещено " + visits.order());
            helper.assertTrue(visits.order().equals(List.of(0, 2, 3)),
                    "достижимые углы 0, 2, 3 обязаны быть посещены, замурованный 1 — нет, фактически " + visits.order());
            helper.assertTrue(visits.lastSkipped().equals(List.of(WALLED_CORNER)),
                    "пропущенная точка обязана быть посчитана в состоянии скрипта, фактически " + visits.lastSkipped());
            helper.assertTrue(run.spiritTicks() <= LOOP_LIMIT_TICKS,
                    "обход с пропуском обязан уложиться в " + LOOP_LIMIT_TICKS + " тиков духа, ушло "
                            + run.spiritTicks());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_patrol_cancel", timeoutTicks = 200)
    public static void patrol_CancelledMidWay_StopsNavigationAndStandsStill(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, CENTER);
        SpiritRun run = SpiritRun.launch(helper, spirit, patrolCall(helper, 1), MovementScene.SETTLE_TICKS);
        int[] cancelledAt = {-1};
        Vec3[] atSettle = new Vec3[1];
        helper.onEachTick(() -> {
            if (cancelledAt[0] < 0 && spirit.tickCount - MovementScene.SETTLE_TICKS >= CANCEL_AFTER_TICKS) {
                helper.assertTrue(!spirit.getNavigation().isDone(), "к моменту отмены дух обязан идти по пути");
                spirit.cancelTask();
                cancelledAt[0] = spirit.tickCount;
            }
            if (cancelledAt[0] >= 0 && spirit.tickCount == cancelledAt[0] + SETTLE_AFTER_CANCEL_TICKS) {
                atSettle[0] = spirit.position();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(cancelledAt[0] >= 0
                            && spirit.tickCount >= cancelledAt[0] + SETTLE_AFTER_CANCEL_TICKS + OBSERVE_AFTER_CANCEL_TICKS,
                    "отмена и наблюдение за остановкой ещё не закончились");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "отменённый патруль обязан иметь статус CANCELLED, фактически "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(spirit.getNavigation().isDone(), "после отмены навигация обязана быть остановлена");
            double drift = spirit.position().distanceTo(atSettle[0]);
            helper.assertTrue(drift < STANDS_STILL,
                    "после отмены дух стоит: за " + OBSERVE_AFTER_CANCEL_TICKS + " тиков он сдвинулся на " + drift);
            int stone = 0;
            for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
                for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                    stone += helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.STONE) ? 1 : 0;
                }
            }
            helper.assertTrue(stone == GameTestFixtures.FLOOR_SIZE * GameTestFixtures.FLOOR_SIZE,
                    "патруль ничего не ломает: пол обязан остаться целым, камня " + stone);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_patrol_reload", timeoutTicks = 400)
    public static void patrol_SpiritReloadedAfterTwoOfFourPoints_KeepsProgressAndFinishes(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity first = MovementScene.spirit(helper, CENTER);
        SpiritRun run = SpiritRun.launch(helper, first, patrolCall(helper, 1), MovementScene.SETTLE_TICKS);
        SpiritEntity[] current = {first};
        boolean[] reloaded = {false};
        int[] savedVisited = {-1};
        int[] savedIndex = {-1};
        int[] visitedAfterReload = {-1};
        int[] indexAfterReload = {-1};
        helper.onEachTick(() -> {
            Map<String, Object> state = scriptState(current[0]);
            if (!reloaded[0] && intOf(state.get("visited")) >= POINTS_BEFORE_RELOAD) {
                savedVisited[0] = intOf(state.get("visited"));
                savedIndex[0] = intOf(state.get("index"));
                CompoundTag tag = new CompoundTag();
                current[0].saveWithoutId(tag);
                current[0].discard();
                SpiritEntity second = ModEntities.SPIRIT.get().create(helper.getLevel());
                second.load(tag);
                helper.getLevel().addFreshEntity(second);
                current[0] = second;
                reloaded[0] = true;
                return;
            }
            if (reloaded[0] && visitedAfterReload[0] < 0 && !state.isEmpty()) {
                visitedAfterReload[0] = intOf(state.get("visited"));
                indexAfterReload[0] = intOf(state.get("index"));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(reloaded[0], "дух ещё не прошёл " + POINTS_BEFORE_RELOAD + " точки, перезагрузки не было");
            SpiritEntity spirit = current[0];
            helper.assertTrue(visitedAfterReload[0] >= savedVisited[0],
                    "после загрузки счётчик пройденных точек не обнуляется: сохранено " + savedVisited[0]
                            + ", прочитано " + visitedAfterReload[0]);
            helper.assertTrue(indexAfterReload[0] >= savedIndex[0],
                    "после загрузки индекс маршрута не откатывается к нулю: сохранён " + savedIndex[0]
                            + ", прочитан " + indexAfterReload[0]);
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "загруженный дух обязан дойти до конца маршрута, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            MovementScene.finish(helper);
        });
    }

    private static TaskNode patrolCall(GameTestHelper helper, int loops) {
        List<String> points = new ArrayList<>();
        for (BlockPos corner : CORNERS) {
            BlockCoord at = MovementScene.coord(helper, corner);
            points.add(at.x() + "," + at.y() + "," + at.z());
        }
        return MovementScene.call("patrol", Map.of("points", points, "mode", CatalogLimits.NAV_PATROL_MODE_DEFAULT,
                "loops", loops));
    }

    private static Map<String, Object> scriptState(SpiritEntity spirit) {
        Optional<String> json = GameTestFixtures.taskSnapshot(spirit);
        if (json.isEmpty()) {
            return Map.of();
        }
        TaskProgress progress = TaskSnapshotCodec.decode(json.get()).progress();
        return progress instanceof TaskProgress.ScriptAt at ? at.state() : Map.of();
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : -1;
    }

    private static final class Visits {
        private final GameTestHelper helper;
        private final SpiritEntity spirit;
        private final List<Integer> order = new ArrayList<>();
        private List<?> lastSkipped = List.of();
        private int lastBuilds;
        private int maxBuildsInOneTick;

        Visits(GameTestHelper helper, SpiritEntity spirit) {
            this.helper = helper;
            this.spirit = spirit;
        }

        void observe() {
            if (spirit.tickCount < MovementScene.SETTLE_TICKS) {
                return;
            }
            for (int i = 0; i < CORNERS.size(); i++) {
                if (!order.contains(i) && MovementScene.distanceTo(helper, spirit, CORNERS.get(i)) <= MovementScene.ARRIVAL) {
                    order.add(i);
                }
            }
            maxBuildsInOneTick = Math.max(maxBuildsInOneTick, spirit.pathBuilds() - lastBuilds);
            lastBuilds = spirit.pathBuilds();
            if (scriptState(spirit).get("skipped") instanceof List<?> skipped && !skipped.isEmpty()) {
                lastSkipped = skipped;
            }
        }

        List<Integer> order() {
            return order;
        }

        List<?> lastSkipped() {
            return lastSkipped;
        }

        int maxBuildsInOneTick() {
            return maxBuildsInOneTick;
        }
    }
}

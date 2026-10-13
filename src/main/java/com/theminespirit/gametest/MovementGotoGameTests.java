package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.GotoBlockSession;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementGotoGameTests {
    private static final BlockPos CORNER = new BlockPos(0, 1, 0);
    private static final BlockPos NEAR_HAY = new BlockPos(5, 1, 0);
    private static final BlockPos FAR_HAY_EAST = new BlockPos(8, 1, 4);
    private static final BlockPos FAR_HAY_SOUTH = new BlockPos(4, 1, 8);
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final int NEAREST_LIMIT_TICKS = 100;
    private static final double NEAREST_MAX_WALK = 2.5;
    private static final int NEAREST_MAX_PATH_BUILDS = 3;
    private static final int SEARCH_RADIUS_NO_HAY = 4;
    private static final int SECTIONS_IN_SEARCH_CUBE = 8;
    private static final int SCALE_RADIUS = 16;
    private static final int MAX_SLICES_LITERAL = 4;
    private static final double STANDS_STILL = 0.1;

    private MovementGotoGameTests() {
    }

    @GameTest(template = "arena", batch = "move_goto_block_nearest", timeoutTicks = 300)
    public static void gotoBlock_ThreeHayBlocks_StopsInReachOfNearestWithoutWalkingToFarOnes(GameTestHelper helper) {
        MovementScene.prepare(helper);
        helper.setBlock(NEAR_HAY, Blocks.HAY_BLOCK);
        helper.setBlock(FAR_HAY_EAST, Blocks.HAY_BLOCK);
        helper.setBlock(FAR_HAY_SOUTH, Blocks.HAY_BLOCK);
        SpiritEntity spirit = MovementScene.spirit(helper, CORNER);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("goto_block", Map.of("block", "minecraft:hay_block", "radius", 16)),
                MovementScene.SETTLE_TICKS);
        Vec3[] startAt = new Vec3[1];
        helper.onEachTick(() -> {
            if (startAt[0] == null && spirit.tickCount >= MovementScene.SETTLE_TICKS) {
                startAt[0] = spirit.position();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "сено в радиусе есть: goto_block обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double reach = MovementScene.eyeDistanceTo(helper, spirit, NEAR_HAY);
            helper.assertTrue(reach <= MovementScene.REACH,
                    "дух обязан встать в досягаемости " + MovementScene.REACH + " от ближнего сена, а до него " + reach);
            double walked = spirit.position().distanceTo(startAt[0]);
            helper.assertTrue(walked <= NEAREST_MAX_WALK,
                    "ближняя цель требует пройти меньше " + NEAREST_MAX_WALK + " блока, а дух прошёл " + walked
                            + ": так идут к дальнему сену, ближняя цель обязана идти первой");
            helper.assertTrue(spirit.pathBuilds() <= NEAREST_MAX_PATH_BUILDS,
                    "путь строится по разу на цель и перестраивается по политике обновления: не больше "
                            + NEAREST_MAX_PATH_BUILDS + " построений, построено " + spirit.pathBuilds());
            helper.assertTrue(run.spiritTicks() <= NEAREST_LIMIT_TICKS,
                    "подход к ближнему сену обязан уложиться в " + NEAREST_LIMIT_TICKS + " тиков духа, ушло "
                            + run.spiritTicks());
            helper.assertTrue(helper.getBlockState(NEAR_HAY).is(Blocks.HAY_BLOCK),
                    "goto_block только подходит: сено обязано остаться на месте");
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_goto_block_none", timeoutTicks = 100)
    public static void gotoBlock_NoSuchBlockInRadius_FailsFastAndStands(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("goto_block",
                        Map.of("block", "minecraft:hay_block", "radius", SEARCH_RADIUS_NO_HAY)),
                MovementScene.SETTLE_TICKS);
        Vec3[] startAt = new Vec3[1];
        helper.onEachTick(() -> {
            if (startAt[0] == null && spirit.tickCount >= MovementScene.SETTLE_TICKS) {
                startAt[0] = spirit.position();
            }
        });
        int limit = (SECTIONS_IN_SEARCH_CUBE + GotoBlockSession.MAX_SLICES_PER_TICK - 1)
                / GotoBlockSession.MAX_SLICES_PER_TICK + 1;

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "сена в радиусе нет: goto_block обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("блоков вида minecraft:hay_block в радиусе " + SEARCH_RADIUS_NO_HAY + " нет"),
                    "причина обязана называть блок и радиус, фактически: " + reason);
            helper.assertTrue(run.spiritTicks() <= limit,
                    "поиск куба радиуса " + SEARCH_RADIUS_NO_HAY + " (не больше " + SECTIONS_IN_SEARCH_CUBE
                            + " секций по " + GotoBlockSession.MAX_SLICES_PER_TICK + " за тик) обязан закончиться за "
                            + limit + " тиков духа, ушло " + run.spiritTicks());
            helper.assertTrue(spirit.position().distanceTo(startAt[0]) < STANDS_STILL,
                    "без цели дух стоит, а он сдвинулся на " + spirit.position().distanceTo(startAt[0]));
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_goto_block_scale", timeoutTicks = 200)
    public static void gotoBlockSession_RadiusSixteenSearch_TakesAtMostFourSlicesPerTickAndStillFindsHay(
            GameTestHelper helper) {
        MovementScene.prepare(helper);
        helper.setBlock(NEAR_HAY, Blocks.HAY_BLOCK);
        SpiritEntity spirit = MovementScene.spirit(helper, CORNER);
        AtomicReference<GotoBlockSession> session = new AtomicReference<>();
        AtomicReference<GotoBlockSession.Outcome> outcome = new AtomicReference<>(GotoBlockSession.Outcome.RUNNING);
        helper.onEachTick(() -> {
            if (session.get() == null) {
                if (spirit.tickCount >= MovementScene.SETTLE_TICKS) {
                    session.set(new GotoBlockSession(spirit, "minecraft:hay_block", SCALE_RADIUS, 1));
                }
                return;
            }
            if (outcome.get() == GotoBlockSession.Outcome.RUNNING) {
                outcome.set(session.get().tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(session.get() != null && outcome.get() != GotoBlockSession.Outcome.RUNNING,
                    "поиск и подход ещё идут");
            helper.assertTrue(MAX_SLICES_LITERAL == GotoBlockSession.MAX_SLICES_PER_TICK,
                    "предел срезов поиска за тик — 4 (чеклист 24), в коде " + GotoBlockSession.MAX_SLICES_PER_TICK);
            helper.assertTrue(outcome.get() == GotoBlockSession.Outcome.SUCCEEDED,
                    "сено в радиусе есть: сессия обязана закончиться успехом, итог " + outcome.get() + ", причина "
                            + session.get().failureReason());
            helper.assertTrue(session.get().maxSlicesPerTick() <= MAX_SLICES_LITERAL,
                    "за один тик поиск берёт не больше " + MAX_SLICES_LITERAL + " срезов, максимум за тик: "
                            + session.get().maxSlicesPerTick());
            helper.assertTrue(session.get().slicesUsed() > MAX_SLICES_LITERAL,
                    "куб радиуса " + SCALE_RADIUS + " не помещается в один тик поиска, иначе тест ничего не доказывает: "
                            + "срезов просмотрено " + session.get().slicesUsed());
            MovementScene.finish(helper);
        });
    }
}

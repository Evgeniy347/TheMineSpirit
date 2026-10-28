package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.CAVE_SHIFT_INSIDE_ARENA;
import static com.theminespirit.gametest.MineSceneSupport.LAVA_TOTAL;
import static com.theminespirit.gametest.MineSceneSupport.RADIUS;
import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_START;
import static com.theminespirit.gametest.MineSceneSupport.buildCave;
import static com.theminespirit.gametest.MineSceneSupport.countBlock;
import static com.theminespirit.gametest.MineSceneSupport.mineCall;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.neoforge.HarvestSession;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class MineVeinGameTests {
    private MineVeinGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_FiveIronOreWithIronPickaxe_SucceedsWithoutTunnelsAndWithinReadBudget(
            GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int z = 0; z <= 4; z++) {
            helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        int stoneBefore = countBlock(helper, Blocks.STONE, CAVE_SHIFT_INSIDE_ARENA);
        AtomicReference<HarvestSession> session = new AtomicReference<>();
        AtomicInteger startTick = new AtomicInteger(-1);
        helper.onEachTick(() -> {
            if (session.get() == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(HarvestSession.mineVein(spirit, OreKind.IRON, 5, RADIUS));
                    startTick.set(spirit.tickCount);
                }
                return;
            }
            if (session.get().outcome() == HarvestSession.Outcome.RUNNING) {
                session.get().tick();
            }
        });

        helper.succeedWhen(() -> {
            HarvestSession run = session.get();
            helper.assertTrue(
                    run != null && run.outcome() != HarvestSession.Outcome.RUNNING,
                    "сессия обязана завершиться, а не висеть в RUNNING: "
                            + (run == null
                                    ? "сессия не стартовала"
                                    : run.outcome().name()));
            helper.assertTrue(
                    run.outcome() == HarvestSession.Outcome.SUCCEEDED,
                    "пять железной руды рядом обязаны добыться, а сессия сказала " + run.outcome() + ": "
                            + run.failureReason());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 5,
                    "в инвентаре обязано оказаться 5 сырого железа, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            helper.assertTrue(
                    countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA) == 0,
                    "в мире не должно остаться железной руды, осталось "
                            + countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    countBlock(helper, Blocks.STONE, CAVE_SHIFT_INSIDE_ARENA) == stoneBefore,
                    "добыча руды не копает туннелей: число камня обязано остаться " + stoneBefore + ", стало "
                            + countBlock(helper, Blocks.STONE, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    spirit.tickCount - startTick.get() <= 200,
                    "пять руды обязаны добыться за 200 тиков духа, заняло " + (spirit.tickCount - startTick.get()));
            helper.assertTrue(
                    run.maxSlicesPerTick() <= 4,
                    "за тик поиск берёт не больше 4 срезов, максимум за тик: " + run.maxSlicesPerTick());
            int queryBudget =
                    (spirit.tickCount - startTick.get()) / PickupTuning.REFRESH_TICKS + 2 * run.collectRuns() + 2;
            helper.assertTrue(
                    run.entityQueries() <= queryBudget,
                    "запросов сущностей обязано быть не больше " + queryBudget + " (раз в "
                            + PickupTuning.REFRESH_TICKS + " тиков и по одному на старте и финале сбора), сделано "
                            + run.entityQueries());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_NoOreInRadius_ScansAtMostFourSlicesPerTick(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        AtomicReference<HarvestSession> session = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (session.get() == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(HarvestSession.mineVein(spirit, OreKind.IRON, 1, RADIUS));
                }
                return;
            }
            if (session.get().outcome() == HarvestSession.Outcome.RUNNING) {
                session.get().tick();
            }
        });

        helper.succeedWhen(() -> {
            HarvestSession run = session.get();
            helper.assertTrue(run != null, "сессия обязана стартовать");
            helper.assertTrue(
                    run.outcome() == HarvestSession.Outcome.FAILED,
                    "без руды в радиусе сессия обязана завершиться отказом, а не " + run.outcome());
            helper.assertTrue(
                    run.maxSlicesPerTick() <= 4,
                    "за тик поиск обязан просматривать не больше 4 срезов, максимум за тик: " + run.maxSlicesPerTick()
                            + " из " + run.slicesUsed() + " срезов всего");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void mineVein_WoodenPickaxeOnIronOre_FailsFastWithTierReason(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.WOODEN_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "деревянная кирка не берёт железную руду: обязана быть ошибка, а статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("каменная кирка"),
                    "причина обязана называть каменную кирку, фактически: " + reason);
            helper.assertTrue(
                    run.spiritTicks() <= 5,
                    "отказ обязан случиться до первого удара, за 5 тиков духа, заняло " + run.spiritTicks());
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "руда обязана остаться на месте: до начала работы её не трогают");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_OreWithLavaBehindFace_SkipsUnsafeAndLeavesLava(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int z = 0; z <= 4; z++) {
            helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        }
        helper.setBlock(-2 + CAVE_SHIFT_INSIDE_ARENA, 2, 4 + CAVE_SHIFT_INSIDE_ARENA, Blocks.LAVA);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 5), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "одна из пяти руды небезопасна: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("добыто 4 из 5; пропущено небезопасных: 1"),
                    "причина обязана назвать четыре добытых и одну небезопасную, фактически: " + reason);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 4,
                    "должно быть добыто 4 железа, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 4 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "пятая руда у лавы обязана остаться целой");
            helper.assertTrue(
                    countBlock(helper, Blocks.LAVA, CAVE_SHIFT_INSIDE_ARENA) == LAVA_TOTAL,
                    "лавовый блок обязан остаться один, стало "
                            + countBlock(helper, Blocks.LAVA, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    !helper.getLevel().getBlockState(spirit.blockPosition()).is(Blocks.LAVA),
                    "дух не должен оказаться в лаве");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_OreWithWaterAbove_IsNotTouched(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.WATER);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "руда под водой небезопасна: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("не осталось"),
                    "причина обязана сказать, что подходящих блоков не осталось, фактически: " + reason);
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "руда под водой обязана остаться нетронутой");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.WATER),
                    "блок воды обязан остаться на месте");
            GameTestFixtures.finish(helper);
        });
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.BlockSets;
import com.theminespirit.neoforge.HarvestSession;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineGameTests {
    private static final int SHELL_MIN = -2;
    private static final int SHELL_MAX = 6;
    private static final int SHELL_TOP = 4;
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos SPIRIT_CORNER = new BlockPos(0, 1, 0);
    private static final BlockPos ARENA_START = new BlockPos(4, 1, 4);
    private static final int TOOL_SLOT = 0;
    private static final int SETTLE_TICKS = 5;
    private static final int RADIUS = 16;
    private static final int DIRT_HEAP_TIMEOUT_TICKS = 1000;
    private static final int REDSTONE_TIMEOUT_TICKS = 400;
    private static final int UNPICKABLE_DELAY = 200;
    private static final int UNPICKABLE_TICKS = 80;
    private static final int MAX_DROP_LINES = 12;
    private static final int LAVA_TOTAL = 1;
    private static final int CAVE_SHIFT_INSIDE_ARENA = -SHELL_MIN;

    private MineGameTests() {}

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

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void mineVein_SealedOreWithinReach_MinedFromPlaceNeighboursStay(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "замурованная руда в досягаемости (упрощение 44.3: жила с места, без прямой видимости) обязана "
                            + "добыться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .isAir(),
                    "руда обязана быть сломана");
            for (BlockPos neighbour : List.of(
                    new BlockPos(1, 2, 2),
                    new BlockPos(3, 2, 2),
                    new BlockPos(2, 1, 2),
                    new BlockPos(2, 3, 2),
                    new BlockPos(2, 2, 1),
                    new BlockPos(2, 2, 3))) {
                helper.assertTrue(
                        helper.getBlockState(neighbour.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA))
                                .is(Blocks.STONE),
                        "камень вокруг руды не трогается: ломается только цель, а не проход к ней (" + neighbour + ")");
            }
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 1,
                    "сырое железо обязано лечь в инвентарь: дроп целевого блока сразу в инвентарь");
            helper.assertTrue(
                    spirit.pathBuilds() == 0,
                    "руда в досягаемости ломается без подхода, путь строился " + spirit.pathBuilds() + " раз");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void mineVein_OreAboveThreeDeepPit_MinedFromPlaceSpiritKeepsHeight(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        for (int y = 0; y >= -2; y--) {
            helper.setBlock(0 + CAVE_SHIFT_INSIDE_ARENA, y, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.AIR);
        }
        helper.setBlock(0 + CAVE_SHIFT_INSIDE_ARENA, -3, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);
        double startY = spirit.getY();

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "руда у ямы в досягаемости добывается с места (упрощение 44.3), статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .isAir(),
                    "руда обязана быть сломана");
            helper.assertTrue(
                    Math.abs(spirit.getY() - startY) < 0.1,
                    "дух не должен спускаться в яму глубиной 3: стоянка без пола запрещена, а руда берётся с места. "
                            + "y был " + startY + ", стал " + spirit.getY());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_OreUnderCeilingGravel_MinesFromSideAndIsNotBuried(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 4, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.GRAVEL);
        SpiritEntity spirit = spawnWith(
                helper, new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    !helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "руду на потолке под гравием обязано быть добыта, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            BlockPos targetAbs =
                    helper.absolutePos(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA));
            BlockPos spiritCell = spirit.blockPosition();
            helper.assertTrue(
                    spiritCell.getX() != targetAbs.getX() || spiritCell.getZ() != targetAbs.getZ(),
                    "стоянку в столбце руды под гравием запрещено выбирать: дух добыл руду, стоя в " + spiritCell
                            + ", столбец руды " + targetAbs);
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] замер 584 тика: 18 земли, подбор дропа зависит от случайного разлёта предметов;
    // потолок 1000 ≈ 1.7× замера
    @SceneStep(template = "arena", timeoutTicks = 1000)
    public static void gather_TerracottaHeapOfEighteen_CollectsAllAndKeepsFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x + CAVE_SHIFT_INSIDE_ARENA, y, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.TERRACOTTA);
                }
            }
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("terracotta", 18, 4), SETTLE_TICKS);
        HarvestProbe probe = HarvestProbe.attach(
                helper,
                spirit,
                run::started,
                () -> GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA),
                () -> GameTestFixtures.countInWorld(helper, Items.TERRACOTTA),
                () -> dropSummary(helper, Items.TERRACOTTA),
                () -> countBlock(helper, Blocks.TERRACOTTA, CAVE_SHIFT_INSIDE_ARENA));
        helper.onEachTick(() -> {
            if (run.started()
                    && spirit.taskStatus().orElse(null) != RunStatus.SUCCEEDED
                    && helper.getTick() >= DIRT_HEAP_TIMEOUT_TICKS) {
                helper.fail(probe.report("тайм-аут " + DIRT_HEAP_TIMEOUT_TICKS + " тиков игры"));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "куча из 18 блоков терракоты обязана собраться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "\n" + probe.report("нет успеха"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA) == 18,
                    "в инвентаре обязано быть 18 терракоты, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA) + "\n"
                            + probe.report("недобрал"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 0, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.STONE),
                    "пол под кучей обязан остаться: блок под ногами не ломается" + "\n" + probe.report("пол сломан"));
            helper.assertTrue(
                    run.spiritTicks() <= 720,
                    "куча из 18 блоков терракоты обязана собраться за 720 тиков (замерено 565–612, порог ≈1.2×), "
                            + "заняло " + run.spiritTicks() + " тиков духа, рабочих " + probe.workingTicks()
                            + ", пауз " + probe.pausedTicks() + "\n" + probe.report("не уложился в 720"));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void gather_TerracottaRingAroundSealedPocket_MinedFromOutsideWithinReach(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        int[][] dirt = {{4, 1, 3}, {5, 1, 4}, {4, 1, 5}, {3, 1, 4}, {4, 2, 3}, {5, 2, 4}, {4, 2, 5}, {3, 2, 4}};
        for (int[] cell : dirt) {
            helper.setBlock(cell[0], cell[1], cell[2], Blocks.TERRACOTTA);
        }
        int[][] stone = {
            {3, 1, 3}, {5, 1, 3}, {3, 1, 5}, {5, 1, 5}, {6, 1, 4}, {2, 1, 4}, {4, 1, 6}, {4, 1, 2}, {3, 2, 3},
            {5, 2, 3}, {3, 2, 5}, {5, 2, 5}, {6, 2, 4}, {2, 2, 4}, {4, 2, 6}, {4, 2, 2}
        };
        for (int[] cell : stone) {
            helper.setBlock(cell[0], cell[1], cell[2], Blocks.STONE);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(4 + dx, 3, 4 + dz, Blocks.STONE);
            }
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("terracotta", 8, 4), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "терракота вокруг запечатанной стоянки добывается снаружи, со стоянок в досягаемости (упрощение 44.3), "
                            + "статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    countBlock(helper, Blocks.TERRACOTTA, CAVE_SHIFT_INSIDE_ARENA) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.TERRACOTTA) == 8,
                    "все 8 блоков терракоты обязаны оказаться в инвентаре, в мире осталось "
                            + countBlock(helper, Blocks.TERRACOTTA, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 3, 4)).is(Blocks.STONE),
                    "каменный колпак над стоянкой не трогается: ломается только цель");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] 1200 тиков HARVEST_NO_PROGRESS_TICKS: отказ обязан прийти не позже предела простоя
    @SceneStep(template = "arena", timeoutTicks = 1400)
    public static void gather_StoneBehindGlassWall_FailsWithNoPathReasonWithinTimeout(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.DIRT);
        for (int x = -2; x <= 10; x++) {
            for (int z = -2; z <= 10; z++) {
                for (int y = -3; y <= -1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
            }
        }
        for (int x = 3; x <= 9; x++) {
            for (int z = 3; z <= 9; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(x, y, z, Blocks.GLASS);
                }
            }
        }
        for (int x = 4; x <= 8; x++) {
            for (int z = 4; z <= 8; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        helper.setBlock(6, 1, 6, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(2, 1, 2), Items.IRON_PICKAXE);
        AtomicReference<HarvestSession> session = new AtomicReference<>();
        AtomicReference<Integer> startTick = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (session.get() == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(HarvestSession.gather(spirit, "stone", 1, 4));
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
            helper.assertTrue(run != null, "сессия обязана стартовать после оседания духа");
            helper.assertTrue(
                    run.outcome() != HarvestSession.Outcome.RUNNING,
                    "камень за преградой обязан завершиться отказом, а не висеть RUNNING: " + run.outcome() + ", тиков "
                            + (spirit.tickCount - startTick.get()));
            helper.assertTrue(
                    run.outcome() == HarvestSession.Outcome.FAILED,
                    "камень за преградой обязан завершиться отказом, а не успехом: " + run.outcome());
            helper.assertTrue(
                    spirit.tickCount - startTick.get() <= CatalogLimits.HARVEST_NO_PROGRESS_TICKS,
                    "отказ обязан прийти не позже предела простоя " + CatalogLimits.HARVEST_NO_PROGRESS_TICKS
                            + " тиков, заняло " + (spirit.tickCount - startTick.get()));
            BlockPos target = helper.absolutePos(new BlockPos(6, 1, 6));
            String expected = "нет пути к блоку (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")";
            String reason = run.failureReason();
            helper.assertTrue(
                    reason != null && reason.contains(expected),
                    "причина отказа обязана назвать блок и «нет пути» (" + expected + "): " + reason);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void gather_FewTargetsLeft_KeepsRefillingSearchEachTick(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int x = 1; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(x + CAVE_SHIFT_INSIDE_ARENA, 1, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.COBBLESTONE);
            }
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        AtomicReference<HarvestSession> session = new AtomicReference<>();
        AtomicInteger slicesAtFirstFind = new AtomicInteger(-1);
        AtomicBoolean refilledWhileSearching = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            HarvestSession run = session.get();
            if (run == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(HarvestSession.gather(spirit, "cobblestone", 6, RADIUS));
                }
                return;
            }
            if (run.outcome() == HarvestSession.Outcome.RUNNING) {
                run.tick();
                if (run.mined() == 0) {
                    if (slicesAtFirstFind.get() < 0) {
                        slicesAtFirstFind.set(run.slicesUsed());
                    } else if (run.slicesUsed() > slicesAtFirstFind.get()) {
                        refilledWhileSearching.set(true);
                    }
                }
            }
        });

        helper.succeedWhen(() -> {
            HarvestSession run = session.get();
            helper.assertTrue(run != null, "сессия обязана стартовать");
            helper.assertTrue(
                    run.outcome() != HarvestSession.Outcome.RUNNING, "сессия обязана завершиться, а висит в RUNNING");
            helper.assertTrue(
                    refilledWhileSearching.get(),
                    "при остатке меньше 8 целей и незаконченном поиске сессия обязана за тик дочитать новую порцию: "
                            + "иначе правило «осталось меньше восьми — искать ещё» мёртвое; срезов у первого поиска "
                            + slicesAtFirstFind.get() + ", всего " + run.slicesUsed());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void gather_WildcardLogFilter_CollectsAllMatchingLogs(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(1 + CAVE_SHIFT_INSIDE_ARENA, 1, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.OAK_LOG);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.OAK_LOG);
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 1, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.OAK_LOG);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_AXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_log", 3), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "три бревна по шаблону *_log обязаны собраться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG) == 3,
                    "в инвентаре обязано быть 3 бревна, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void gather_ForbiddenContainerAndBedrock_RefuseAtOnceAndKeepChest(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(
                        helper.absolutePos(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA)));
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 1, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.BEDROCK);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);

        HarvestSession chestSession = HarvestSession.gather(spirit, "minecraft:chest", 1, RADIUS);
        helper.assertTrue(
                chestSession.tick() == HarvestSession.Outcome.FAILED,
                "сундук запрещён для gather: отказ обязан наступить сразу");
        helper.assertTrue(
                chestSession.failureReason().contains("запрещён"),
                "причина обязана объяснять запрет, фактически: " + chestSession.failureReason());

        HarvestSession bedrockSession = HarvestSession.gather(spirit, "minecraft:bedrock", 1, RADIUS);
        helper.assertTrue(
                bedrockSession.tick() == HarvestSession.Outcome.FAILED,
                "бедрок запрещён для gather: отказ обязан наступить сразу");
        helper.assertTrue(
                helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA))
                        .is(Blocks.CHEST),
                "запрещённый сундук обязан остаться на месте");
        ChestBlockEntity kept = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(
                        helper.absolutePos(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA)));
        helper.assertTrue(
                kept.getItem(0).is(Items.DIAMOND) && kept.getItem(0).getCount() == 3,
                "содержимое сундука обязано уцелеть, в слоте " + kept.getItem(0));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_CancelledMidWork_LeavesCaveUnchanged(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int z = 0; z <= 4; z++) {
            helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 5), SETTLE_TICKS);
        AtomicInteger cancelTick = new AtomicInteger(-1);
        AtomicReference<String> snapshot = new AtomicReference<>();

        helper.onEachTick(() -> {
            if (cancelTick.get() >= 0 || spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            if (GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) >= 1) {
                spirit.cancelTask();
                snapshot.set(caveSnapshot(helper, CAVE_SHIFT_INSIDE_ARENA));
                cancelTick.set(spirit.tickCount);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    cancelTick.get() >= 0 && spirit.tickCount >= cancelTick.get() + 40,
                    "нужно дождаться отмены и переждать 40 тиков: тик отмены " + cancelTick.get() + ", тик духа "
                            + spirit.tickCount);
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "отменённая задача обязана быть CANCELLED, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    caveSnapshot(helper, CAVE_SHIFT_INSIDE_ARENA).equals(snapshot.get()),
                    "через 40 тиков после отмены мир обязан остаться прежним");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_PickaxeWithOneDurability_FailsBeforeMiningAsNeedTool(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        ItemStack pickaxe = spirit.getInventory().getItem(TOOL_SLOT);
        int lastUseDamage = pickaxe.getMaxDamage() - 1;
        pickaxe.setDamageValue(lastUseDamage);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "изношенная кирка обязана провалить задачу до первой руды, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("нужен инструмент") && reason.contains("кирка"),
                    "причина обязана назвать, что нужен инструмент (кирка), фактически: " + reason);
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "первая руда обязана остаться: последний удар изношенной кирки не тратится");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "вторая руда обязана остаться: её нечем добыть");
            helper.assertTrue(
                    spirit.getInventory().getItem(TOOL_SLOT).getDamageValue() == lastUseDamage,
                    "изношенная кирка не должна изнашиваться дальше, фактический износ "
                            + spirit.getInventory().getItem(TOOL_SLOT).getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void toolReplace_SpentPickaxeInHandAndSpareInInventory_MinesWithSpareAndKeepsWorn(
            GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        int wornDamage = worn.getMaxDamage() - 1;
        worn.setDamageValue(wornDamage);
        spirit.setItemSlot(EquipmentSlot.MAINHAND, worn);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "с запасной киркой задача обязана дойти до конца, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ": "
                            + spirit.taskFailureReason().orElse(""));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 2,
                    "обе руды обязаны быть добыты запасной киркой, фактически сырого железа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            helper.assertTrue(
                    countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA) == 0,
                    "в мире не должно остаться железной руды, осталось "
                            + countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.IRON_PICKAXE)
                            && spirit.getMainHandItem().getDamageValue() >= 1,
                    "в руке обязана оказаться запасная кирка и износиться на добыче, а в руке "
                            + spirit.getMainHandItem());
            helper.assertTrue(
                    spirit.getInventory().getItem(TOOL_SLOT).is(Items.IRON_PICKAXE)
                            && spirit.getInventory().getItem(TOOL_SLOT).getDamageValue() == wornDamage,
                    "изношенная кирка обязана вернуться в слот инвентаря нетронутой, а в слоте "
                            + spirit.getInventory().getItem(TOOL_SLOT));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void toolReplace_SpentPickaxeInHandAndNoSpare_FailsNeedTool(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        SpiritEntity spirit = helper.spawn(
                ModEntities.SPIRIT.get(), SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        int wornDamage = worn.getMaxDamage() - 1;
        worn.setDamageValue(wornDamage);
        spirit.setItemSlot(EquipmentSlot.MAINHAND, worn);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "без запасной кирки задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("нужен инструмент") && reason.contains("кирка"),
                    "причина обязана назвать, что нужен инструмент (кирка), фактически: " + reason);
            helper.assertTrue(
                    countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA) == 2,
                    "обе руды обязаны остаться: последний удар изношенной кирки не тратится, осталось "
                            + countBlock(helper, Blocks.IRON_ORE, CAVE_SHIFT_INSIDE_ARENA));
            helper.assertTrue(
                    spirit.getMainHandItem().getDamageValue() == wornDamage,
                    "изношенная кирка не должна изнашиваться дальше, фактический износ "
                            + spirit.getMainHandItem().getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_AnyOreWithStonePickaxe_MinesCoalAndSkipsDiamond(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.COAL_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.DIAMOND_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("any", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("без подходящего инструмента: 1"),
                    "причина обязана посчитать пропущенный алмаз, фактически: " + reason);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.DIAMOND_ORE),
                    "алмазная руда обязана остаться целой");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void gather_WildcardOreWithStonePickaxe_MinesCoalAndSkipsDiamond(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.COAL_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.DIAMOND_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_ore", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: gather обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    !reason.contains("сломался"),
                    "каменная кирка цела: причина не смеет говорить о поломке, фактически: " + reason);
            helper.assertTrue(
                    reason.contains("без подходящего инструмента: 1"),
                    "причина обязана посчитать пропущенный алмаз, фактически: " + reason);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.DIAMOND_ORE),
                    "алмазная руда обязана остаться целой");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void gather_WildcardOreDiamondCloserThanCoal_SkipsDiamond(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.DIAMOND_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.COAL_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_ore", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: gather обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("без подходящего инструмента: 1"),
                    "первым выбран ближний алмаз без подходящего инструмента: его обязано пропустить и посчитать, "
                            + "а не отказываться до начала, фактически: " + reason);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "дальний уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.DIAMOND_ORE),
                    "ближний алмаз обязан остаться целой");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_TwoRedstoneOre_ConservesAllDrops(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.REDSTONE_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.REDSTONE_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("redstone", 2), SETTLE_TICKS);
        HarvestProbe probe = HarvestProbe.attach(
                helper,
                spirit,
                run::started,
                () -> GameTestFixtures.countInInventory(spirit.getInventory(), Items.REDSTONE),
                () -> GameTestFixtures.countInWorld(helper, Items.REDSTONE),
                () -> dropSummary(helper, Items.REDSTONE),
                () -> countBlock(helper, Blocks.REDSTONE_ORE, CAVE_SHIFT_INSIDE_ARENA));
        helper.onEachTick(() -> {
            if (run.started()
                    && spirit.taskStatus().orElse(null) != RunStatus.SUCCEEDED
                    && helper.getTick() >= REDSTONE_TIMEOUT_TICKS) {
                helper.fail(probe.report("тайм-аут " + REDSTONE_TIMEOUT_TICKS + " тиков игры"));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "две редстоуновые руды обязаны добыться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "\n" + probe.report("нет успеха"));
            int inInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.REDSTONE);
            int inWorld = GameTestFixtures.countInWorld(helper, Items.REDSTONE);
            int total = inInventory + inWorld;
            helper.assertTrue(
                    total >= 8 && total <= 10,
                    "две руды дают 8..10 редстоуна вместе в инвентаре и мире, в инвентаре " + inInventory
                            + ", в мире " + inWorld + ", фактически " + total + "\n"
                            + probe.report("дроп не сошёлся"));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "empty", timeoutTicks = 100)
    public static void gatherPolicy_ExactIds_ExistInRegistries(GameTestHelper helper) {
        for (String id : GatherPolicy.forbiddenExactIds()) {
            helper.assertTrue(
                    BlockSets.resolve(IdFilter.of(List.of("minecraft:" + id))).size() == 1,
                    "точный запрет gather '" + id + "' обязан существовать в реестре блоков: опечатка в списке "
                            + "ослепляет политику");
        }
        helper.succeed();
    }

    // [флак] 18 камня деревянной киркой (твёрдость 1.5) и разлёт дропа: запас 2× к ~600 тикам
    @SceneStep(template = "arena", timeoutTicks = 1400)
    public static void gather_StoneHeapWithWoodenPickaxe_MinesAll(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x + CAVE_SHIFT_INSIDE_ARENA, y, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
                }
            }
        }
        SpiritEntity spirit = spawnWith(
                helper,
                SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA),
                Items.WOODEN_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("stone", 18), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "куча камня под деревянной киркой обязана добыться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 18,
                    "в инвентаре обязано быть 18 булыжника, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] 2400 тиков общего подбора при задержке подъёма дропа и разлёте: запас 2× к ожиданию
    @SceneStep(template = "arena", timeoutTicks = 600)
    public static void gather_StoneDropTemporarilyUnpickable_CollectsAfterDelay(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 1, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        SpiritEntity spirit = spawnWith(
                helper,
                SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA),
                Items.WOODEN_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("stone", 2), SETTLE_TICKS);

        int[] dropTick = {-1};
        helper.onEachTick(() -> {
            List<ItemEntity> drops = GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE);
            if (drops.isEmpty()) {
                return;
            }
            if (dropTick[0] < 0) {
                dropTick[0] = spirit.tickCount;
            }
            for (ItemEntity drop : drops) {
                if (spirit.tickCount < dropTick[0] + UNPICKABLE_TICKS) {
                    drop.setPickUpDelay(UNPICKABLE_DELAY);
                } else {
                    drop.setNoPickUpDelay();
                }
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "временно не поднимаемый дроп не валит добычу, пока есть недобытые блоки: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "оба булыжника обязаны попасть в инвентарь после снятия задержки, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 280)
    public static void gatherCobblestone_SavedMidway_ResumesWithoutExtraMining(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        int heapBefore = countBlock(helper, Blocks.COBBLESTONE);
        SpiritEntity spirit = spawnWith(helper, ARENA_START, Items.IRON_PICKAXE);
        AtomicReference<SpiritEntity> live = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        SpiritRun.launch(helper, spirit, gatherCall("cobblestone", 6), SETTLE_TICKS);
        helper.onEachTick(() -> {
            if (saved.get()) {
                return;
            }
            SpiritEntity current = live.get();
            if (GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE) == 3) {
                saved.set(true);
                CompoundTag tag = new CompoundTag();
                current.saveWithoutId(tag);
                live.set(reload(helper, current, tag));
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = live.get();
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный gather обязан досчитать до 6 блоков: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет") + ", тиков духа " + current.tickCount);
            int inInventory = GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    inInventory == 6,
                    "после перезагрузки на середине в инвентаре обязано быть ровно 6 булыжника, а не пересчёт "
                            + "с нуля: " + inInventory);
            helper.assertTrue(
                    countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6,
                    "в куче обязано остаться " + (heapBefore - 6) + " блоков (исходно " + heapBefore
                            + " минус 6), а не " + countBlock(helper, Blocks.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 260)
    public static void gatherCobblestone_SavedAfterLastBlock_RestoresAsSucceeded(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 4; z <= 5; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        int heapBefore = countBlock(helper, Blocks.COBBLESTONE);
        SpiritEntity spirit = spawnWith(helper, ARENA_START, Items.IRON_PICKAXE);
        AtomicReference<SpiritEntity> live = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        SpiritRun.launch(helper, spirit, gatherCall("cobblestone", 6), SETTLE_TICKS);
        helper.onEachTick(() -> {
            if (saved.get()) {
                return;
            }
            SpiritEntity current = live.get();
            if (current.taskStatus().orElse(null) == RunStatus.RUNNING
                    && countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6) {
                saved.set(true);
                CompoundTag tag = new CompoundTag();
                current.saveWithoutId(tag);
                live.set(reload(helper, current, tag));
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = live.get();
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "снимок в момент сбора последнего блока обязан восстановиться успехом, а не искать "
                            + "седьмой блок или падать «добыто 6 из 6»: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            helper.assertTrue(saved.get(), "снимок обязан быть сделан после шестого блока");
            helper.assertTrue(
                    countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6,
                    "после восстановления нельзя добывать ещё: в куче обязано остаться " + (heapBefore - 6) + ", стало "
                            + countBlock(helper, Blocks.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void blockLocator_TwoStepsInOneTick_CountsSlicesPerTick(GameTestHelper helper) {
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        BlockPos center = SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA);
        BlockLocator.Search search = BlockLocator.search(
                helper.getLevel(),
                helper.absolutePos(center),
                RADIUS,
                BlockSets.resolve(IdFilter.of(List.of("minecraft:stone"))));

        search.step(1);
        int afterFirst = search.maxSlicesPerTick();
        search.step(2);
        int afterSecond = search.maxSlicesPerTick();

        helper.assertTrue(afterFirst == 1, "первый шаг в тик обязан посчитать один срез, а посчитал " + afterFirst);
        helper.assertTrue(
                afterSecond == 3,
                "второй шаг в том же тике обязан прибавить свои срезы к тику (1 + 2 = 3), а максимум стал "
                        + afterSecond);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        boolean added = helper.getLevel().addFreshEntity(reloaded);
        if (!added) {
            throw new IllegalStateException("перезагруженный дух не добавлен в мир: uuid " + reloaded.getUUID());
        }
        return reloaded;
    }

    private static String dropSummary(GameTestHelper helper, Item item) {
        List<ItemEntity> drops = GameTestFixtures.dropsInArea(helper, item);
        StringBuilder text = new StringBuilder("капель: " + drops.size());
        int shown = 0;
        for (ItemEntity drop : drops) {
            if (shown >= MAX_DROP_LINES) {
                text.append(" ...обрезано");
                break;
            }
            text.append(" [")
                    .append(drop.getItem().getCount())
                    .append("× на ")
                    .append(drop.blockPosition().toShortString())
                    .append("]");
            shown++;
        }
        return text.toString();
    }

    private static ScriptCall mineCall(String ore, int count) {
        return new ScriptCall("mine_vein", Map.of("ore", ore, "count", count, "radius", RADIUS));
    }

    private static ScriptCall gatherCall(String block, int count) {
        return gatherCall(block, count, RADIUS);
    }

    private static ScriptCall gatherCall(String block, int count, int radius) {
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", radius));
    }

    // [флак] полный инвентарь, путь до сундука и добыча руды: под нагрузкой машины запас двойной.
    @SceneStep(template = "arena", timeoutTicks = 600)
    public static void mineVein_FullInventoryNearChest_DepositsAndContinues(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        for (int slot = 1; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.CHEST);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.IRON_ORE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "при полном инвентаре дух обязан сдать лишнее на склад и продолжить добычу, статус "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 1,
                    "после сдачи руда обязана оказаться в инвентаре: добыча продолжилась, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 1, 7)));
            helper.assertTrue(
                    chest instanceof Container container
                            && GameTestFixtures.countInInventory(container, Items.DIRT) > 0,
                    "лишняя земля обязана уехать в сундук: иначе дух не освободил место и бросил задачу");
            GameTestFixtures.finish(helper);
        });
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, Item tool) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(tool));
        return spirit;
    }

    private static void buildCave(GameTestHelper helper, int shift) {
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        for (int x = shift; x <= 4 + shift; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = shift; z <= 4 + shift; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                helper.setBlock(x, -1, z, Blocks.DIRT);
            }
        }
    }

    private static int countBlock(GameTestHelper helper, Block block) {
        return countBlock(helper, block, 0);
    }

    private static int countBlock(GameTestHelper helper, Block block, int shift) {
        int total = 0;
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    private static String caveSnapshot(GameTestHelper helper, int shift) {
        StringBuilder snapshot = new StringBuilder();
        for (int x = SHELL_MIN + shift; x <= SHELL_MAX + shift; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN + shift; z <= SHELL_MAX + shift; z++) {
                    snapshot.append(x)
                            .append(',')
                            .append(y)
                            .append(',')
                            .append(z)
                            .append('=')
                            .append(helper.getBlockState(new BlockPos(x, y, z)))
                            .append(';');
                }
            }
        }
        return snapshot.toString();
    }
}

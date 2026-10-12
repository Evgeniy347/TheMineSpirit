package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.BlockSets;
import com.theminespirit.neoforge.HarvestSession;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
    private static final int TOOL_SLOT = 0;
    private static final int SETTLE_TICKS = 5;
    private static final int RADIUS = 16;
    private static final int LAVA_TOTAL = 1;

    private MineGameTests() {
    }

    @GameTest(template = "empty", batch = "mine_happy", timeoutTicks = 400)
    public static void mineVein_FiveIronOreWithIronPickaxe_SucceedsWithoutTunnelsAndWithinReadBudget(GameTestHelper helper) {
        buildCave(helper);
        for (int z = 0; z <= 4; z++) {
            helper.setBlock(-1, 2, z, Blocks.IRON_ORE);
        }
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        int stoneBefore = countBlock(helper, Blocks.STONE);
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
            helper.assertTrue(run != null && run.outcome() != HarvestSession.Outcome.RUNNING,
                    "сессия обязана завершиться, а не висеть в RUNNING: "
                            + (run == null ? "сессия не стартовала" : run.outcome().name()));
            helper.assertTrue(run.outcome() == HarvestSession.Outcome.SUCCEEDED,
                    "пять железной руды рядом обязаны добыться, а сессия сказала " + run.outcome() + ": "
                            + run.failureReason());
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 5,
                    "в инвентаре обязано оказаться 5 сырого железа, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            helper.assertTrue(countBlock(helper, Blocks.IRON_ORE) == 0,
                    "в мире не должно остаться железной руды, осталось " + countBlock(helper, Blocks.IRON_ORE));
            helper.assertTrue(countBlock(helper, Blocks.STONE) == stoneBefore,
                    "добыча руды не копает туннелей: число камня обязано остаться " + stoneBefore + ", стало "
                            + countBlock(helper, Blocks.STONE));
            helper.assertTrue(spirit.tickCount - startTick.get() <= 200,
                    "пять руды обязаны добыться за 200 тиков духа, заняло " + (spirit.tickCount - startTick.get()));
            int budget = MiningPlanner.MAX_VIEW_READS_PER_TARGET * 5 * run.planCalls();
            helper.assertTrue(run.safetyReads() <= budget,
                    "чтений безопасности обязано быть не больше " + budget + " (K на цель на вызов планировщика), "
                            + "прочитано " + run.safetyReads() + " за " + run.planCalls() + " вызовов");
            helper.assertTrue(run.maxSlicesPerTick() <= 4,
                    "за тик поиск берёт не больше 4 срезов, максимум за тик: " + run.maxSlicesPerTick());
            helper.assertTrue(run.maxBreakerHitsPerTick() <= 1,
                    "ломка идёт не больше одного удара за тик, максимум за тик: " + run.maxBreakerHitsPerTick());
            int queryBudget = run.totalTicks() / PickupTuning.REFRESH_TICKS + 2 * run.collectRuns() + 2;
            helper.assertTrue(run.entityQueries() <= queryBudget,
                    "запросов сущностей обязано быть не больше " + queryBudget + " (раз в "
                            + PickupTuning.REFRESH_TICKS + " тиков и по одному на старте и финале сбора), сделано "
                            + run.entityQueries());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_scale", timeoutTicks = 400)
    public static void mineVein_NoOreInRadius_ScansAtMostFourSlicesPerTick(GameTestHelper helper) {
        buildCave(helper);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
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
            helper.assertTrue(run.outcome() == HarvestSession.Outcome.FAILED,
                    "без руды в радиусе сессия обязана завершиться отказом, а не " + run.outcome());
            helper.assertTrue(run.maxSlicesPerTick() <= 4,
                    "за тик поиск обязан просматривать не больше 4 срезов, максимум за тик: "
                            + run.maxSlicesPerTick() + " из " + run.slicesUsed() + " срезов всего");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_no_tool", timeoutTicks = 100)
    public static void mineVein_WoodenPickaxeOnIronOre_FailsFastWithTierReason(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 2, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.WOODEN_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "деревянная кирка не берёт железную руду: обязана быть ошибка, а статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("каменная кирка"),
                    "причина обязана называть каменную кирку, фактически: " + reason);
            helper.assertTrue(run.spiritTicks() <= 5,
                    "отказ обязан случиться до первого удара, за 5 тиков духа, заняло " + run.spiritTicks());
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 2)).is(Blocks.IRON_ORE),
                    "руда обязана остаться на месте: до начала работы её не трогают");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_lava_neighbor", timeoutTicks = 400)
    public static void mineVein_OreWithLavaBehindFace_SkipsUnsafeAndLeavesLava(GameTestHelper helper) {
        buildCave(helper);
        for (int z = 0; z <= 4; z++) {
            helper.setBlock(-1, 2, z, Blocks.IRON_ORE);
        }
        helper.setBlock(-2, 2, 4, Blocks.LAVA);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 5), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "одна из пяти руды небезопасна: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("добыто 4 из 5; пропущено небезопасных: 1"),
                    "причина обязана назвать четыре добытых и одну небезопасную, фактически: " + reason);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 4,
                    "должно быть добыто 4 железа, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 4)).is(Blocks.IRON_ORE),
                    "пятая руда у лавы обязана остаться целой");
            helper.assertTrue(countBlock(helper, Blocks.LAVA) == LAVA_TOTAL,
                    "лавовый блок обязан остаться один, стало " + countBlock(helper, Blocks.LAVA));
            helper.assertTrue(!helper.getLevel().getBlockState(spirit.blockPosition()).is(Blocks.LAVA),
                    "дух не должен оказаться в лаве");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_water_above", timeoutTicks = 400)
    public static void mineVein_OreWithWaterAbove_IsNotTouched(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 2, Blocks.IRON_ORE);
        helper.setBlock(-1, 3, 2, Blocks.WATER);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "руда под водой небезопасна: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("не осталось"),
                    "причина обязана сказать, что подходящих блоков не осталось, фактически: " + reason);
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 2)).is(Blocks.IRON_ORE),
                    "руда под водой обязана остаться нетронутой");
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 3, 2)).is(Blocks.WATER),
                    "блок воды обязан остаться на месте");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_sealed", timeoutTicks = 200)
    public static void mineVein_SealedOre_FailsWithoutStand(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(2, 2, 2, Blocks.IRON_ORE);
        helper.setBlock(1, 2, 2, Blocks.STONE);
        helper.setBlock(3, 2, 2, Blocks.STONE);
        helper.setBlock(2, 1, 2, Blocks.STONE);
        helper.setBlock(2, 3, 2, Blocks.STONE);
        helper.setBlock(2, 2, 1, Blocks.STONE);
        helper.setBlock(2, 2, 3, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "замурованная руда недобываема: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("подходящих блоков") && reason.contains("не осталось"),
                    "причина обязана сказать, что подходящих блоков не осталось, фактически: " + reason);
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).is(Blocks.IRON_ORE),
                    "замурованную руду нельзя пробивать через соседей: она обязана остаться");
            helper.assertTrue(run.spiritTicks() <= 40,
                    "отказ по замурованной руде обязан наступить за 40 тиков духа, заняло " + run.spiritTicks());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_pit", timeoutTicks = 200)
    public static void mineVein_OreAboveThreeDeepPit_StaysAndSpiritKeepsHeight(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 2, Blocks.IRON_ORE);
        for (int y = 0; y >= -2; y--) {
            helper.setBlock(0, y, 2, Blocks.AIR);
        }
        helper.setBlock(0, -3, 2, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);
        double startY = spirit.getY();

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "единственная стоянка без пола: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 2)).is(Blocks.IRON_ORE),
                    "руду у ямы без пола трогать нельзя");
            helper.assertTrue(Math.abs(spirit.getY() - startY) < 0.1,
                    "дух не должен спускаться в яму глубиной 3 (навигация согласна прыгнуть): без проверки пола "
                            + "стоянки он шагнёт за край. y был " + startY + ", стал " + spirit.getY());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_gravel_ceiling", timeoutTicks = 400)
    public static void mineVein_OreUnderCeilingGravel_MinesFromSideAndIsNotBuried(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(2, 3, 2, Blocks.IRON_ORE);
        helper.setBlock(2, 4, 2, Blocks.GRAVEL);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(2, 1, 2), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(!helper.getBlockState(new BlockPos(2, 3, 2)).is(Blocks.IRON_ORE),
                    "руду на потолке под гравием обязано быть добыта, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            BlockPos targetAbs = helper.absolutePos(new BlockPos(2, 3, 2));
            BlockPos spiritCell = spirit.blockPosition();
            helper.assertTrue(spiritCell.getX() != targetAbs.getX() || spiritCell.getZ() != targetAbs.getZ(),
                    "стоянку в столбце руды под гравием запрещено выбирать: дух добыл руду, стоя в "
                            + spiritCell + ", столбец руды " + targetAbs);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "gather_cobble", timeoutTicks = 1000)
    public static void gather_CobblestoneHeapOfEighteen_CollectsAllAndKeepsFloor(GameTestHelper helper) {
        buildCave(helper);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x, y, z, Blocks.COBBLESTONE);
                }
            }
        }
        SpiritEntity spirit = spawnWith(helper, SPIRIT_CORNER, Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("cobblestone", 18), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "куча из 18 булыжника обязана собраться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 18,
                    "в инвентаре обязано быть 18 булыжника, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 0, 2)).is(Blocks.STONE),
                    "пол под кучей обязан остаться: блок под ногами не ломается");
            helper.assertTrue(run.spiritTicks() <= 720,
                    "куча из 18 булыжника обязана собраться за 720 тиков (замерено 486, порог ≈1.5×), "
                            + "заняло " + run.spiritTicks());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "gather_refill", timeoutTicks = 400)
    public static void gather_FewTargetsLeft_KeepsRefillingSearchEachTick(GameTestHelper helper) {
        buildCave(helper);
        for (int x = 1; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        SpiritEntity spirit = spawnWith(helper, SPIRIT_CORNER, Items.IRON_PICKAXE);
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
            helper.assertTrue(run.outcome() != HarvestSession.Outcome.RUNNING,
                    "сессия обязана завершиться, а висит в RUNNING");
            helper.assertTrue(refilledWhileSearching.get(),
                    "при остатке меньше 8 целей и незаконченном поиске сессия обязана за тик дочитать новую порцию: "
                            + "иначе правило «осталось меньше восьми — искать ещё» мёртвое; срезов у первого поиска "
                            + slicesAtFirstFind.get() + ", всего " + run.slicesUsed());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "gather_logs", timeoutTicks = 600)
    public static void gather_WildcardLogFilter_CollectsAllMatchingLogs(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(1, 1, 1, Blocks.OAK_LOG);
        helper.setBlock(2, 1, 1, Blocks.OAK_LOG);
        helper.setBlock(3, 1, 1, Blocks.OAK_LOG);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_CORNER, Items.IRON_AXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_log", 3), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "три бревна по шаблону *_log обязаны собраться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG) == 3,
                    "в инвентаре обязано быть 3 бревна, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "gather_forbidden", timeoutTicks = 100)
    public static void gather_ForbiddenContainerAndBedrock_RefuseAtOnceAndKeepChest(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(2, 1, 2, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 2)));
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        helper.setBlock(3, 1, 3, Blocks.BEDROCK);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_CORNER, Items.IRON_PICKAXE);

        HarvestSession chestSession = HarvestSession.gather(spirit, "minecraft:chest", 1, RADIUS);
        helper.assertTrue(chestSession.tick() == HarvestSession.Outcome.FAILED,
                "сундук запрещён для gather: отказ обязан наступить сразу");
        helper.assertTrue(chestSession.failureReason().contains("запрещён"),
                "причина обязана объяснять запрет, фактически: " + chestSession.failureReason());

        HarvestSession bedrockSession = HarvestSession.gather(spirit, "minecraft:bedrock", 1, RADIUS);
        helper.assertTrue(bedrockSession.tick() == HarvestSession.Outcome.FAILED,
                "бедрок запрещён для gather: отказ обязан наступить сразу");
        helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.CHEST),
                "запрещённый сундук обязан остаться на месте");
        ChestBlockEntity kept = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 2)));
        helper.assertTrue(kept.getItem(0).is(Items.DIAMOND) && kept.getItem(0).getCount() == 3,
                "содержимое сундука обязано уцелеть, в слоте " + kept.getItem(0));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mine_cancel", timeoutTicks = 400)
    public static void mineVein_CancelledMidWork_LeavesCaveUnchanged(GameTestHelper helper) {
        buildCave(helper);
        for (int z = 0; z <= 4; z++) {
            helper.setBlock(-1, 2, z, Blocks.IRON_ORE);
        }
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 5), SETTLE_TICKS);
        AtomicInteger cancelTick = new AtomicInteger(-1);
        AtomicReference<String> snapshot = new AtomicReference<>();

        helper.onEachTick(() -> {
            if (cancelTick.get() >= 0 || spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            if (GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) >= 1) {
                spirit.cancelTask();
                snapshot.set(caveSnapshot(helper));
                cancelTick.set(spirit.tickCount);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(cancelTick.get() >= 0 && spirit.tickCount >= cancelTick.get() + 40,
                    "нужно дождаться отмены и переждать 40 тиков: тик отмены " + cancelTick.get() + ", тик духа "
                            + spirit.tickCount);
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "отменённая задача обязана быть CANCELLED, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(caveSnapshot(helper).equals(snapshot.get()),
                    "через 40 тиков после отмены мир обязан остаться прежним");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_tool_break", timeoutTicks = 400)
    public static void mineVein_PickaxeWithOneDurabilityAndTwoOre_FailsMidway(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 2, Blocks.IRON_ORE);
        helper.setBlock(-1, 2, 3, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        ItemStack pickaxe = spirit.getInventory().getItem(TOOL_SLOT);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "сломанная кирка обязана провалить задачу, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("инструмент сломался: добыто 1 из 2"),
                    "причина обязана сказать, что инструмент сломался после первой руды, фактически: " + reason);
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 3)).is(Blocks.IRON_ORE),
                    "вторая руда обязана остаться: её нечем добыть");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_any", timeoutTicks = 400)
    public static void mineVein_AnyOreWithStonePickaxe_MinesCoalAndSkipsDiamond(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 1, Blocks.COAL_ORE);
        helper.setBlock(-1, 2, 3, Blocks.DIAMOND_ORE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("any", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("без подходящего инструмента: 1"),
                    "причина обязана посчитать пропущенный алмаз, фактически: " + reason);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 3)).is(Blocks.DIAMOND_ORE),
                    "алмазная руда обязана остаться целой");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "gather_any_ore", timeoutTicks = 400)
    public static void gather_WildcardOreWithStonePickaxe_MinesCoalAndSkipsDiamond(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 1, Blocks.COAL_ORE);
        helper.setBlock(-1, 2, 3, Blocks.DIAMOND_ORE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_ore", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: gather обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(!reason.contains("сломался"),
                    "каменная кирка цела: причина не смеет говорить о поломке, фактически: " + reason);
            helper.assertTrue(reason.contains("без подходящего инструмента: 1"),
                    "причина обязана посчитать пропущенный алмаз, фактически: " + reason);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 3)).is(Blocks.DIAMOND_ORE),
                    "алмазная руда обязана остаться целой");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "gather_any_ore_close", timeoutTicks = 400)
    public static void gather_WildcardOreDiamondCloserThanCoal_SkipsDiamond(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 1, Blocks.DIAMOND_ORE);
        helper.setBlock(-1, 2, 3, Blocks.COAL_ORE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_ore", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: gather обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("без подходящего инструмента: 1"),
                    "первым выбран ближний алмаз без подходящего инструмента: его обязано пропустить и посчитать, "
                            + "а не отказываться до начала, фактически: " + reason);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "дальний уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(helper.getBlockState(new BlockPos(-1, 2, 1)).is(Blocks.DIAMOND_ORE),
                    "ближний алмаз обязан остаться целой");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_preserve", timeoutTicks = 400)
    public static void mineVein_TwoRedstoneOre_ConservesAllDrops(GameTestHelper helper) {
        buildCave(helper);
        helper.setBlock(-1, 2, 2, Blocks.REDSTONE_ORE);
        helper.setBlock(-1, 2, 3, Blocks.REDSTONE_ORE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("redstone", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "две редстоуновые руды обязаны добыться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int total = GameTestFixtures.countInInventory(spirit.getInventory(), Items.REDSTONE)
                    + GameTestFixtures.countInWorld(helper, Items.REDSTONE);
            helper.assertTrue(total >= 8 && total <= 10,
                    "две руды дают 8..10 редстоуна вместе в инвентаре и мире, фактически " + total);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "gather_policy", timeoutTicks = 100)
    public static void gatherPolicy_ExactIds_ExistInRegistries(GameTestHelper helper) {
        for (String id : GatherPolicy.forbiddenExactIds()) {
            helper.assertTrue(BlockSets.resolve(IdFilter.of(List.of("minecraft:" + id))).size() == 1,
                    "точный запрет gather '" + id + "' обязан существовать в реестре блоков: опечатка в списке "
                            + "ослепляет политику");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mine_registry", timeoutTicks = 100)
    public static void scripts_RegistryForSpirit_ContainsMineVeinAndGather(GameTestHelper helper) {
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        var ids = SpiritScripts.registryFor(spirit).ids();
        helper.assertTrue(ids.contains("mine_vein") && ids.contains("gather"),
                "реестр скриптов духа обязан содержать mine_vein и gather, а содержит " + ids);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mine_persist", timeoutTicks = 280)
    public static void gatherCobblestone_SavedMidway_ResumesWithoutExtraMining(GameTestHelper helper) {
        buildCave(helper);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        int heapBefore = countBlock(helper, Blocks.COBBLESTONE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_CORNER, Items.IRON_PICKAXE);
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
            helper.assertTrue(current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный gather обязан досчитать до 6 блоков: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет") + ", тиков духа " + current.tickCount);
            int inInventory = GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(inInventory == 6,
                    "после перезагрузки на середине в инвентаре обязано быть ровно 6 булыжника, а не пересчёт "
                            + "с нуля: " + inInventory);
            helper.assertTrue(countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6,
                    "в куче обязано остаться " + (heapBefore - 6) + " блоков (исходно " + heapBefore
                            + " минус 6), а не " + countBlock(helper, Blocks.COBBLESTONE));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_persist_last", timeoutTicks = 260)
    public static void gatherCobblestone_SavedAfterLastBlock_RestoresAsSucceeded(GameTestHelper helper) {
        buildCave(helper);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        int heapBefore = countBlock(helper, Blocks.COBBLESTONE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_CORNER, Items.IRON_PICKAXE);
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
            helper.assertTrue(current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "снимок в момент сбора последнего блока обязан восстановиться успехом, а не искать "
                            + "седьмой блок или падать «добыто 6 из 6»: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            helper.assertTrue(saved.get(), "снимок обязан быть сделан после шестого блока");
            helper.assertTrue(countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6,
                    "после восстановления нельзя добывать ещё: в куче обязано остаться " + (heapBefore - 6)
                            + ", стало " + countBlock(helper, Blocks.COBBLESTONE));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "mine_scale_search", timeoutTicks = 100)
    public static void blockLocator_TwoStepsInOneTick_CountsSlicesPerTick(GameTestHelper helper) {
        buildCave(helper);
        BlockLocator.Search search = BlockLocator.search(helper.getLevel(), helper.absolutePos(SPIRIT_START), RADIUS,
                BlockSets.resolve(IdFilter.of(List.of("minecraft:stone"))));

        search.step(1);
        int afterFirst = search.maxSlicesPerTick();
        search.step(2);
        int afterSecond = search.maxSlicesPerTick();

        helper.assertTrue(afterFirst == 1,
                "первый шаг в тик обязан посчитать один срез, а посчитал " + afterFirst);
        helper.assertTrue(afterSecond == 3,
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

    private static ScriptCall mineCall(String ore, int count) {
        return new ScriptCall("mine_vein", Map.of("ore", ore, "count", count, "radius", RADIUS));
    }

    private static ScriptCall gatherCall(String block, int count) {
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", RADIUS));
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, Item tool) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(tool));
        return spirit;
    }

    private static void buildCave(GameTestHelper helper) {
        for (int x = SHELL_MIN; x <= SHELL_MAX; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN; z <= SHELL_MAX; z++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 0; z <= 4; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        for (int x = SHELL_MIN; x <= SHELL_MAX; x++) {
            for (int z = SHELL_MIN; z <= SHELL_MAX; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    private static int countBlock(GameTestHelper helper, Block block) {
        int total = 0;
        for (int x = SHELL_MIN; x <= SHELL_MAX; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN; z <= SHELL_MAX; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    private static String caveSnapshot(GameTestHelper helper) {
        StringBuilder snapshot = new StringBuilder();
        for (int x = SHELL_MIN; x <= SHELL_MAX; x++) {
            for (int y = 0; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN; z <= SHELL_MAX; z++) {
                    snapshot.append(x).append(',').append(y).append(',').append(z).append('=')
                            .append(helper.getBlockState(new BlockPos(x, y, z))).append(';');
                }
            }
        }
        return snapshot.toString();
    }
}
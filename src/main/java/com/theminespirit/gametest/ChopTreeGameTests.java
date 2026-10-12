package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.neoforge.ChopTreeScript;
import com.theminespirit.neoforge.LevelTreeView;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChopTreeGameTests {
    private static final String FIELD = "arena";
    private static final int PLATFORM = 11;
    private static final int PIT_DEPTH = 4;
    private static final int FIELD_HEIGHT = 24;
    private static final int SEARCH_RADIUS = 8;
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final int SETTLE_TICKS = 1;
    private static final int TOOL_SLOT = 0;

    private ChopTreeGameTests() {
    }

    @GameTest(template = "empty", batch = "chop_registry", timeoutTicks = 20)
    public static void treeSpecies_AllIds_ExistInRegistries(GameTestHelper helper) {
        for (TreeSpecies species : TreeSpecies.ALL) {
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(species.logId())),
                    "порода '" + species.id() + "': блок брёвен '" + species.logId() + "' отсутствует в реестре "
                            + "блоков — опечатка в id молча дала бы 'деревьев нет'");
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(species.leavesId())),
                    "порода '" + species.id() + "': блок листьев '" + species.leavesId() + "' отсутствует в реестре "
                            + "блоков — дерево не распозналось бы как настоящее");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(species.saplingId())),
                    "порода '" + species.id() + "': саженец '" + species.saplingId() + "' отсутствует в реестре "
                            + "предметов — посадка после рубки не смогла бы его поставить");
        }
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = "chop_tree_basic", timeoutTicks = 300)
    public static void chopTree_SingleOakFiveLogs_SucceedsWithLogsInInventory(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(run.spiritTicks() <= 200,
                    "рубка 5 брёвен с железным топором обязана уложиться в 200 тиков, заняло " + run.spiritTicks());

            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logsInInventory == 5,
                    "в инвентаре должно быть 5 дубовых брёвен, фактически " + logsInInventory);

            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(logsInWorld == 0,
                    "в мире не должно остаться дубовых брёвен, осталось " + logsInWorld);

            int leavesInWorld = countBlocksInWorld(helper, Blocks.OAK_LEAVES);
            helper.assertTrue(leavesInWorld > 0,
                    "листва должна остаться целой, осталось " + leavesInWorld + " блоков листьев");

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_axe_durability", timeoutTicks = 300)
    public static void chopTree_AxeDurability_DecreasesByLogCount(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        spirit.getInventory().setItem(TOOL_SLOT, axe);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree обязан завершиться успехом, причина: " + spirit.taskFailureReason().orElse("нет"));

            int damage = axe.getDamageValue();
            helper.assertTrue(damage == 5,
                    "прочность топора должна уменьшиться ровно на 5 (по 1 за бревно), фактически " + damage);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_two_trees", timeoutTicks = 400)
    public static void chopTree_TwoTreesAnySpecies_BothChopped(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        buildBirchTree(helper, 7, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("any", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree с count=2 и species=any обязан срубить оба дерева, причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            int oakLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            int birchLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BIRCH_LOG);
            helper.assertTrue(oakLogs == 5 && birchLogs == 5,
                    "должно быть 5 дубовых и 5 берёзовых брёвен, фактически дуб=" + oakLogs + ", берёза=" + birchLogs);

            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG)
                    + countBlocksInWorld(helper, Blocks.BIRCH_LOG);
            helper.assertTrue(logsInWorld == 0,
                    "в мире не должно остаться брёвен, осталось " + logsInWorld);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_species_filter", timeoutTicks = 400)
    public static void chopTree_BirchAndOakSpeciesBirch_OnlyBirchChopped(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        buildBirchTree(helper, 7, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("birch", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree с species=birch обязан срубить только берёзу, причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            int birchLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BIRCH_LOG);
            int oakLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(birchLogs == 5 && oakLogs == 0,
                    "должно быть 5 берёзовых и 0 дубовых брёвен, фактически берёза=" + birchLogs + ", дуб=" + oakLogs);

            int birchInWorld = countBlocksInWorld(helper, Blocks.BIRCH_LOG);
            int oakInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(birchInWorld == 0 && oakInWorld == 5,
                    "берёза должна быть срублена (0 в мире), дуб должен остаться (5 в мире), "
                            + "фактически берёза=" + birchInWorld + ", дуб=" + oakInWorld);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_hut_and_pillar", timeoutTicks = 300)
    public static void chopTree_HutAndPillarRejected_NotATreeReturnsFail(GameTestHelper helper) {
        prepareField(helper);
        buildLogHut(helper, 3, 3, 3);
        buildLogPillar(helper, 8, 6, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("any", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "хижина и столб не деревья: chop_tree обязан вернуть FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("деревья") && reason.contains("закончились")
                            && reason.contains("отвергнуто не-деревьев"),
                    "причина FAILED обязана упоминать, что деревья закончились и не-деревья отвергнуты: " + reason);

            int totalLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(totalLogs == 27 + 5,
                    "хижина (27) + столб (5) = 32 бревна должны остаться нетронутыми, осталось " + totalLogs);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_eight_logs", timeoutTicks = 400)
    public static void chopTree_EightLogTree_SixChoppedTwoStanding(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 8);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дерево из 8 брёвен: срубить 6 достижимых, 2 выше +5 — SUCCEEDED, причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logsInInventory == 6,
                    "должно быть 6 брёвен в инвентаре (достижимых), фактически " + logsInInventory);

            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(logsInWorld == 2,
                    "2 бревна выше досягаемости должны остаться в мире, осталось " + logsInWorld);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_count_exceeds", timeoutTicks = 300)
    public static void chopTree_CountThreeWithOneTree_FailsWithCountMessage(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 3), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "при count=3 и одном дереве должен быть FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.startsWith("срублено 1 из 3: деревья породы oak в радиусе " + SEARCH_RADIUS
                            + " закончились"),
                    "причина FAILED: 'срублено 1 из 3: деревья породы oak в радиусе " + SEARCH_RADIUS
                            + " закончились': " + reason);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_unreachable_island", timeoutTicks = 300)
    public static void chopTree_TreeOnIslandWithPit_RejectedUnreachable(GameTestHelper helper) {
        prepareField(helper);
        digPitAround(helper, 6, 6, 5);
        buildOakTree(helper, 6, 6, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "дерево на острове за ямой глубже безопасного падения недоступно: должен быть FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("недоступных: 1"),
                    "причина FAILED обязана содержать 'недоступных: 1': " + reason);
            helper.assertTrue(run.spiritTicks() <= 150,
                    "недоступное дерево обязано определяться за ≤150 тиков, заняло " + run.spiritTicks());

            LOGGER.info("chop_tree, остров за ямой: дерево признано недоступным за {} тиков духа", run.spiritTicks());
            BlockPos spiritCell = helper.relativePos(spirit.blockPosition());
            helper.assertTrue(spiritCell.getY() >= 1 && (spiritCell.getX() == 0 || spiritCell.getZ() == 0),
                    "дух не должен оказаться в яме: он обязан остаться на кромке (x=0 или z=0, y>=1), "
                            + "а стоит в " + spiritCell);
            helper.assertTrue(countBlocksInWorld(helper, Blocks.OAK_LOG) == 5,
                    "дерево на острове недоступно и обязано остаться целым");

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_cancel", timeoutTicks = 300)
    public static void chopTree_CancelMidChop_WorldUnchangedAfter40Ticks(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        int[] cancelledAt = { -1 };
        List<String> frozenWorld = new ArrayList<>();
        BlockPos[] frozenSpirit = new BlockPos[1];

        helper.succeedWhen(() -> {
            run.assertStarted();
            if (cancelledAt[0] < 0) {
                boolean midChop = countBlocksInWorld(helper, Blocks.OAK_LOG) < 5
                        && spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
                helper.assertTrue(midChop, "ожидание середины рубки: хотя бы одно бревно срублено, задача идёт");
                cancelledAt[0] = spirit.tickCount;
                spirit.cancelTask();
                frozenWorld.addAll(snapshotField(helper));
                frozenSpirit[0] = spirit.blockPosition();
            }
            helper.assertTrue(spirit.tickCount >= cancelledAt[0] + 40,
                    "ожидание 40 тиков после отмены: тик отмены " + cancelledAt[0] + ", тик духа " + spirit.tickCount);
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "статус задачи после отмены обязан быть CANCELLED: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(frozenWorld.equals(snapshotField(helper)),
                    "через 40 тиков после отмены мир не должен меняться: ни ломки, ни трещин, ни подбора");
            helper.assertTrue(frozenSpirit[0].equals(spirit.blockPosition()),
                    "после отмены дух обязан стоять: был в " + frozenSpirit[0] + ", стал в " + spirit.blockPosition());

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_by_hand", timeoutTicks = 600)
    public static void chopTree_WithoutAxeTwoLogs_SucceedsByHand(GameTestHelper helper) {
        prepareField(helper);
        // Крона на высоте 3, а не 2: листья вплотную к земле закрывают духу ростом 1.4 вход в клетку ствола,
        // и дроп оказывается физически недосягаем.
        buildLogPillar(helper, 4, 4, 2);
        addLeavesCrown(helper, 4, 4, 3, Blocks.OAK_LEAVES);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "без инструмента дух рубит рукой — это не ошибка, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(run.spiritTicks() >= 2 * 60,
                    "рука бьёт бревно 60 тиков (ванильная твёрдость 2.0 без инструмента), два бревна не быстрее "
                            + "120 тиков, а заняло " + run.spiritTicks());
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logsInInventory == 2,
                    "в инвентаре должно быть 2 бревна, фактически " + logsInInventory);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_slope_oak", timeoutTicks = 400)
    public static void chopTree_OakOnSlopeWithBranchOverGrass_ChoppedWithBranch(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        helper.setBlock(5, 1, 4, Blocks.GRASS_BLOCK);
        helper.setBlock(5, 2, 4, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "настоящий дуб на склоне, ветка которого висит над травой выше основания, обязан рубиться: "
                            + "почва под веткой не признак постройки. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logsInInventory == 6,
                    "ствол из 5 брёвен и ветка над склоном — 6 брёвен в инвентаре, фактически " + logsInInventory);
            helper.assertTrue(countBlocksInWorld(helper, Blocks.OAK_LOG) == 0,
                    "в мире не должно остаться брёвен дерева на склоне");

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_built_posts", timeoutTicks = 500)
    public static void chopTree_LogPostsWithPlankWallsUnderCrown_UntouchedAndRealOakChopped(GameTestHelper helper) {
        prepareField(helper);
        buildPlankHutOnLedge(helper, 3, 3);
        buildOakTree(helper, 8, 8, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "хижина с досками обязана остаться, а настоящий дуб за ней срубиться. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int postLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(postLogs == HUT_POST_LOGS,
                    "все " + HUT_POST_LOGS + " брёвна угловых столбов хижины обязаны остаться: столб с листьями "
                            + "неотличим от ствола, кроме досок стены вплотную. Осталось " + postLogs);
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 3)).is(BlockTags.DOORS)
                            && helper.getBlockState(new BlockPos(4, 3, 5)).is(Blocks.GLASS)
                            && helper.getBlockState(new BlockPos(4, 2, 4)).is(Blocks.TORCH),
                    "дверь, стекло и факел хижины обязаны остаться на месте");
            int oakInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(oakInInventory == 5,
                    "настоящий дуб срублен целиком: 5 брёвен в инвентаре, фактически " + oakInInventory);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_big_building", timeoutTicks = 500)
    public static void chopTree_LogBuildingOfManyLogs_TreeBehindItIsChopped(GameTestHelper helper) {
        prepareField(helper);
        for (int x = 3; x <= 9; x++) {
            for (int y = 1; y <= 7; y++) {
                for (int z = 3; z <= 9; z++) {
                    helper.setBlock(x, y, z, Blocks.OAK_LOG);
                }
            }
        }
        buildOakTree(helper, 1, 8, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "постройка из " + BIG_BUILDING_LOGS + " брёвен всех отвергнута, а дерево за ней обязано срубиться: "
                            + "лимит кандидатов не считает отвергнутых, иначе поиск не идёт дальше и скрипт висит. "
                            + "Статус: " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                            + ", причина: " + spirit.taskFailureReason().orElse("нет"));
            int oakInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(oakInInventory == 5,
                    "дерево за постройкой срублено: 5 брёвен в инвентаре, фактически " + oakInInventory);
            int buildingLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(buildingLogs == BIG_BUILDING_LOGS,
                    "ни одно из " + BIG_BUILDING_LOGS + " брёвен постройки не тронуто, осталось " + buildingLogs);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_scale_reads", timeoutTicks = 600)
    public static void chopTree_SixtyLogTree_ParsesWithinTwentySevenReadsPerLog(GameTestHelper helper) {
        prepareField(helper);
        buildTree(helper, 4, 4, TALL_TREE_LOGS, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        SpiritEntity spirit = spawnChopper(helper);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(status[0] == ScriptStatus.SUCCEEDED,
                    "дерево из " + TALL_TREE_LOGS + " брёвен: достижимые срублены, скрипт успешен, статус "
                            + status[0] + ", причина: " + script.failureReason());
            long reads = script.treeBlockReads();
            helper.assertTrue(reads >= TALL_TREE_LOGS,
                    "разбор обязан прочитать хотя бы каждое бревно, а прочитал " + reads
                            + ": нулевой счётчик означал бы, что скрипт читает мир мимо LevelTreeView");
            helper.assertTrue(reads <= 27L * TALL_TREE_LOGS,
                    "разбор одного дерева из " + TALL_TREE_LOGS + " брёвен читает не больше 27 клеток на бревно ("
                            + 27L * TALL_TREE_LOGS + "), прочитано " + reads
                            + ": повторное чтение соседей или новый вид на каждый разбор завешивают тик");
            long distinctCells = 9L * (TALL_TREE_LOGS + 2);
            helper.assertTrue(reads <= distinctCells + 1,
                    "ствол в одну клетку окружён " + distinctCells + " различными клетками (3x3 на "
                            + (TALL_TREE_LOGS + 2) + " слоёв): каждая читается из мира не больше одного раза, плюс "
                            + "одно повторное чтение почвы под основанием после обхода, а прочитано " + reads
                            + ". Раздельные вопросы isLog/isNaturalLeaves/isArtificial к одной клетке обязаны "
                            + "делить одно чтение");

            helper.assertTrue(script.summary().contains("оставлено брёвен выше досягаемости: " + (TALL_TREE_LOGS - 6)),
                    "итог обязан назвать число оставленных брёвен выше досягаемости: " + (TALL_TREE_LOGS - 6)
                            + ", а он: " + script.summary());
            LOGGER.info("chop_tree, дерево из {} брёвен: разбор прочитал {} клеток мира, предел {}",
                    TALL_TREE_LOGS, reads, 27L * TALL_TREE_LOGS);

            clearField(helper, TALL_TREE_LOGS + 10);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_scale_search", timeoutTicks = 400)
    public static void chopTree_Search_ReadsOnlySectionsWithLogsAndBoundedPerTick(GameTestHelper helper) {
        prepareField(helper);
        // Акация: ни один другой тест не строит акации. Палитра секции не сжимается после удаления блока,
        // а поиск пропускает секцию по палитре, поэтому следы дубов из соседних тестов в тех же чанках
        // арены дали бы лишние прочитанные секции.
        buildTree(helper, 4, 4, 5, Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES);
        SpiritEntity spirit = spawnChopper(helper);
        BlockPos center = spirit.blockPosition();
        int expected = positionsInSectionsWithLogs(helper.getLevel(), center, SEARCH_RADIUS, Blocks.ACACIA_LOG);
        int cubeVolume = (2 * SEARCH_RADIUS + 1) * (2 * SEARCH_RADIUS + 1) * (2 * SEARCH_RADIUS + 1);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("acacia", 1));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(status[0] == ScriptStatus.SUCCEEDED,
                    "рубка одной акации успешна, статус " + status[0] + ", причина: " + script.failureReason());
            int examined = script.searchExaminedPositions();
            helper.assertTrue(expected > 0 && expected < cubeVolume,
                    "в кубе поиска есть секции без брёвен (ожидание " + expected + " < куба " + cubeVolume
                            + "), иначе проверка «читает только секции с брёвнами» ничего не доказывает");
            helper.assertTrue(examined == expected,
                    "поиск читает позиции куба только в секциях, где есть брёвна породы: ожидалось " + expected
                            + ", прочитано " + examined + ". Больше — читаются секции без брёвен, меньше — "
                            + "секция с брёвнами пропущена и дерево может не найтись. Расчёт «27 клеток на бревно» "
                            + "к поиску не относится: поиск читает срез секции целиком, а не соседей брёвен");
            helper.assertTrue(script.searchMaxExaminedPositionsPerTick()
                            <= ScanTuning.SLICES_PER_TICK * SECTION_POSITIONS,
                    "за тик читается не больше " + ScanTuning.SLICES_PER_TICK + " срезов секций ("
                            + ScanTuning.SLICES_PER_TICK * SECTION_POSITIONS + " позиций), а прочитано "
                            + script.searchMaxExaminedPositionsPerTick());

            LOGGER.info("chop_tree, поиск радиуса {}: прочитано {} позиций, ожидалось {} (куб {}), "
                            + "максимум за тик {}",
                    SEARCH_RADIUS, examined, expected, cubeVolume, script.searchMaxExaminedPositionsPerTick());

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_persist", timeoutTicks = 900)
    public static void chopTree_ReloadedAfterFirstTree_ChopsExactlyThreeTrees(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 3, 3, 5);
        buildOakTree(helper, 6, 3, 5);
        buildOakTree(helper, 9, 3, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 3), SETTLE_TICKS);

        SpiritEntity[] current = { spirit };
        boolean[] reloaded = { false };
        helper.onEachTick(() -> {
            if (reloaded[0] || current[0].taskSnapshot().isEmpty()) {
                return;
            }
            TaskProgress progress = TaskSnapshotCodec.decode(current[0].taskSnapshot().get()).progress();
            if (progress instanceof TaskProgress.ScriptAt at && at.state().get("chopped") instanceof Number chopped
                    && chopped.intValue() == 1) {
                reloaded[0] = true;
                CompoundTag tag = new CompoundTag();
                current[0].saveWithoutId(tag);
                current[0].discard();
                SpiritEntity fresh = ModEntities.SPIRIT.get().create(helper.getLevel());
                fresh.load(tag);
                helper.getLevel().addFreshEntity(fresh);
                current[0] = fresh;
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(reloaded[0],
                    "после первого срубленного дерева снимок задачи обязан показать chopped=1: иначе прогресс "
                            + "рубки не сохраняется и перезапуск начинает счёт заново");
            SpiritEntity now = current[0];
            helper.assertTrue(now.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "после перезагрузки рубка продолжается с chopped=1 и срубает ровно два оставшихся дерева: "
                            + "SUCCEEDED, а не FAILED «срублено 2 из 3». Статус: "
                            + now.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + now.taskFailureReason().orElse("нет"));
            int logs = GameTestFixtures.countInInventory(now.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logs == 15,
                    "три дерева по 5 брёвен: 15 в инвентаре, фактически " + logs);
            helper.assertTrue(countBlocksInWorld(helper, Blocks.OAK_LOG) == 0,
                    "в мире не должно остаться брёвен трёх деревьев");

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_replant_oak", timeoutTicks = 400)
    public static void chopTree_ReplantWithOneSapling_PlantsItAtTrunkBase(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.OAK_SAPLING));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1, true), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "рубка с replant и одним саженцем успешна, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.OAK_SAPLING),
                    "на месте ствола обязан стоять саженец дуба, а стоит "
                            + helper.getBlockState(new BlockPos(4, 1, 4)).getBlock());
            int saplings = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_SAPLING);
            helper.assertTrue(saplings == 0,
                    "посаженный саженец списывается из инвентаря, осталось " + saplings);
            int logs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logs == 5, "5 брёвен в инвентаре, фактически " + logs);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_replant_none", timeoutTicks = 400)
    public static void chopTree_ReplantWithoutSaplings_SucceedsAndReportsNoSaplings(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1, true));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(status[0] == ScriptStatus.SUCCEEDED,
                    "нехватка саженцев не ошибка: рубка успешна, статус " + status[0] + ", причина: "
                            + script.failureReason());
            helper.assertTrue(script.summary().contains("replant: нет саженцев"),
                    "итоговое сообщение обязано сказать «replant: нет саженцев», а оно: " + script.summary());
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)).isAir(),
                    "без саженцев на месте ствола ничего не появляется");

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_replant_dark_oak", timeoutTicks = 900)
    public static void chopTree_ReplantDarkOakWithFourSaplings_PlantsAllFourCells(GameTestHelper helper) {
        prepareField(helper);
        buildDarkOakTree(helper, 4, 4, 4);
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.DARK_OAK_SAPLING, 4));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("dark_oak", 1, true), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "тёмный дуб 2x2 срублен с посадкой, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            for (BlockPos cell : List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(4, 1, 5),
                    new BlockPos(5, 1, 5))) {
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.DARK_OAK_SAPLING),
                        "в клетке ствола " + cell + " обязан стоять саженец тёмного дуба, а стоит "
                                + helper.getBlockState(cell).getBlock());
            }
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.DARK_OAK_SAPLING) == 0,
                    "четыре саженца потрачены на четыре клетки");

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_replant_short", timeoutTicks = 900)
    public static void chopTree_ReplantDarkOakWithThreeSaplings_PlantsNothing(GameTestHelper helper) {
        prepareField(helper);
        buildDarkOakTree(helper, 4, 4, 4);
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.DARK_OAK_SAPLING, 3));
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("dark_oak", 1, true));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(status[0] == ScriptStatus.SUCCEEDED,
                    "рубка успешна и при нехватке саженцев, статус " + status[0] + ", причина: "
                            + script.failureReason());
            helper.assertTrue(script.summary().contains("replant: нет саженцев"),
                    "три саженца на четыре клетки: нехватка названа в итоге, а он: " + script.summary());
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.DARK_OAK_SAPLING) == 3,
                    "саженцы сажаются сразу на все клетки или никак: три остаются в инвентаре");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4)).isAir(),
                    "ни одной клетки не засажено частично");

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_vanished", timeoutTicks = 500)
    public static void chopTree_SecondOakVanishesDuringFirst_FailsWithTreesRanOut(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 3, 3, 5);
        buildOakTree(helper, 7, 3, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 2), SETTLE_TICKS);

        boolean[] removed = { false };
        helper.onEachTick(() -> {
            if (!removed[0] && countBlocksInWorld(helper, Blocks.OAK_LOG) < 10) {
                removed[0] = true;
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(7, y, 3, Blocks.AIR);
                }
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(removed[0], "второй дуб убирается, как только первый начал рубиться");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "второго дуба больше нет: FAILED «деревья закончились», статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.startsWith("срублено 1 из 2") && reason.contains("закончились"),
                    "исчезнувшее бревно кандидата не должно ронять скрипт («скрипт упал»), а причина должна быть "
                            + "«срублено 1 из 2 … закончились»: " + reason);

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_left_standing", timeoutTicks = 700)
    public static void chopTree_BranchOverShallowPit_LeftLogsAreCountedInSummary(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        for (int x = 5; x <= 9; x++) {
            helper.setBlock(x, 6, 4, Blocks.OAK_LOG);
        }
        // Стена за дальними брёвнами ветки, шире платформы: они недосягаемы, подход встаёт,
        // брёвна остаются стоять. Ближние брёвна ветки перед стеной: дроп с них достижим.
        for (int z = -2; z <= 12; z++) {
            for (int y = 1; y <= 5; y++) {
                helper.setBlock(8, y, z, Blocks.STONE);
            }
        }
        SpiritEntity spirit = spawnChopper(helper);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(status[0] == ScriptStatus.SUCCEEDED,
                    "дерево срублено, недосягаемое бревно ветки осталось, статус " + status[0] + ", причина: "
                            + script.failureReason());
            int left = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(left == 1,
                    "бревно ветки над ямой недосягаемо (подход встаёт) и остаётся стоять, а осталось "
                            + left + " брёвен");
            helper.assertTrue(script.summary().contains("оставлено брёвен выше досягаемости: " + left),
                    "итог обязан называть все оставленные брёвна, в том числе пропущенные после повторного "
                            + "приближения: в мире " + left + ", а итог: " + script.summary());

            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_tree_view_table", timeoutTicks = 40)
    public static void treeView_BlockTable_ClassifiesBuiltAndNaturalBlocks(GameTestHelper helper) {
        prepareField(helper);
        Map<BlockState, Boolean> table = new LinkedHashMap<>();
        for (Block built : List.of(Blocks.OAK_PLANKS, Blocks.OAK_DOOR, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TORCH,
                Blocks.WALL_TORCH, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE,
                Blocks.OAK_TRAPDOOR, Blocks.LADDER, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_WOOD, Blocks.OAK_SIGN,
                Blocks.WHITE_BED, Blocks.CHEST, Blocks.CRAFTING_TABLE, Blocks.COBBLESTONE_WALL, Blocks.WHITE_WOOL,
                Blocks.LANTERN, Blocks.BOOKSHELF)) {
            table.put(built.defaultBlockState(), true);
        }
        table.put(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), true);
        for (Block natural : List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.SAND, Blocks.GRAVEL,
                Blocks.WATER, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.VINE, Blocks.DANDELION, Blocks.SNOW,
                Blocks.BROWN_MUSHROOM, Blocks.BEE_NEST, Blocks.BIRCH_LOG, Blocks.OAK_LOG, Blocks.MANGROVE_ROOTS,
                Blocks.COCOA)) {
            table.put(natural.defaultBlockState(), false);
        }
        table.put(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), false);

        LevelTreeView view = new LevelTreeView(helper.getLevel(), Set.of(Blocks.OAK_LOG));
        int index = 0;
        for (Map.Entry<BlockState, Boolean> entry : table.entrySet()) {
            BlockPos cell = new BlockPos(index % PLATFORM, 1, index / PLATFORM);
            helper.getLevel().setBlock(helper.absolutePos(cell), entry.getKey(), Block.UPDATE_CLIENTS);
            BlockPos absolute = helper.absolutePos(cell);
            BlockCoord coord = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
            helper.assertTrue(view.isArtificial(coord) == entry.getValue(),
                    "блок " + entry.getKey() + (entry.getValue() ? " — постройка" : " — естественный")
                            + ": LevelTreeView.isArtificial вернул " + view.isArtificial(coord)
                            + ". Таблица решает, что дух не тронет; ошибка в сторону «естественный» ломает стены игрока, "
                            + "в сторону «постройка» оставляет настоящие деревья стоять");
            int answers = (view.isLog(coord) ? 1 : 0) + (view.isNaturalLeaves(coord) ? 1 : 0)
                    + (view.isSoil(coord) ? 1 : 0) + (view.isArtificial(coord) ? 1 : 0);
            helper.assertTrue(answers <= 1,
                    "блок " + entry.getKey() + " не может быть двумя видами сразу, ответов " + answers);
            index++;
        }
        helper.assertTrue(view.isLog(coordOf(helper, table, Blocks.OAK_LOG)),
                "дубовое бревно при запрошенной породе oak — бревно");
        helper.assertTrue(!view.isLog(coordOf(helper, table, Blocks.BIRCH_LOG)),
                "берёзовое бревно при запрошенной породе oak не бревно рубки, но и не постройка");
        clearField(helper);
        helper.succeed();
    }

    private static BlockCoord coordOf(GameTestHelper helper, Map<BlockState, Boolean> table, Block block) {
        int index = 0;
        for (BlockState state : table.keySet()) {
            if (state.is(block)) {
                BlockPos absolute = helper.absolutePos(new BlockPos(index % PLATFORM, 1, index / PLATFORM));
                return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
            }
            index++;
        }
        throw new IllegalStateException("блока " + block + " нет в таблице теста");
    }

    private static ChopTreeParams chopParams(String species, int count) {
        return chopParams(species, count, false);
    }

    private static ChopTreeParams chopParams(String species, int count, boolean replant) {
        return ChopTreeParams.from(
                Map.of("species", species, "count", count, "radius", SEARCH_RADIUS, "replant", replant));
    }

    private static ScriptStatus[] driveScript(GameTestHelper helper, SpiritEntity spirit, ChopTreeScript script) {
        ScriptStatus[] status = { ScriptStatus.RUNNING };
        helper.onEachTick(() -> {
            if (status[0] == ScriptStatus.RUNNING && spirit.tickCount >= SETTLE_TICKS) {
                status[0] = script.tick();
            }
        });
        return status;
    }

    private static int positionsInSectionsWithLogs(Level level, BlockPos center, int radius, Block log) {
        int minY = Math.max(center.getY() - radius, level.getMinBuildHeight());
        int maxY = Math.min(center.getY() + radius, level.getMaxBuildHeight() - 1);
        Map<String, Boolean> sectionHasLog = new HashMap<>();
        int count = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    int sectionY = y >> 4;
                    if (!(level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk)) {
                        continue;
                    }
                    String key = chunkX + "," + sectionY + "," + chunkZ;
                    if (sectionHasLog.computeIfAbsent(key, k -> sectionContains(level, chunkX, sectionY, chunkZ, log))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean sectionContains(Level level, int chunkX, int sectionY, int chunkZ, Block block) {
        for (int dx = 0; dx < 16; dx++) {
            for (int dy = 0; dy < 16; dy++) {
                for (int dz = 0; dz < 16; dz++) {
                    BlockPos pos = new BlockPos(chunkX * 16 + dx, sectionY * 16 + dy, chunkZ * 16 + dz);
                    if (level.getBlockState(pos).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static SpiritEntity spawnChopper(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));
        return spirit;
    }

    private static ScriptCall chopCall(String species, int count) {
        return chopCall(species, count, false);
    }

    private static ScriptCall chopCall(String species, int count, boolean replant) {
        return new ScriptCall("chop_tree",
                Map.of("species", species, "count", count, "radius", SEARCH_RADIUS, "replant", replant));
    }

    private static void prepareField(GameTestHelper helper) {
        clearField(helper);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
    }

    private static void clearField(GameTestHelper helper) {
        clearField(helper, FIELD_HEIGHT);
    }

    private static void clearField(GameTestHelper helper, int height) {
        GameTestFixtures.reset(helper, height);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                for (int y = 1 - PIT_DEPTH; y < 0; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
            }
        }
    }

    private static void digPitAround(GameTestHelper helper, int centerX, int centerZ, int width) {
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                int distance = Math.max(Math.abs(x - centerX), Math.abs(z - centerZ));
                if (distance > 0 && distance <= width) {
                    for (int y = 1 - PIT_DEPTH; y <= 0; y++) {
                        helper.setBlock(x, y, z, Blocks.AIR);
                    }
                }
            }
        }
    }

    private static List<String> snapshotField(GameTestHelper helper) {
        List<String> cells = new ArrayList<>();
        for (int x = 0; x < PLATFORM; x++) {
            for (int y = 1; y < FIELD_HEIGHT; y++) {
                for (int z = 0; z < PLATFORM; z++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir()) {
                        cells.add(x + "," + y + "," + z + "=" + state);
                    }
                }
            }
        }
        return cells;
    }

    private static void buildOakTree(GameTestHelper helper, int x, int z, int height) {
        buildTree(helper, x, z, height, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
    }

    private static void buildDarkOakTree(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    helper.setBlock(x + dx, y, z + dz, Blocks.DARK_OAK_LOG);
                }
            }
        }
        var leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                helper.setBlock(x + dx, height + 1, z + dz, leaves);
            }
        }
    }

    private static void buildBirchTree(GameTestHelper helper, int x, int z, int height) {
        buildTree(helper, x, z, height, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
    }

    private static void buildTree(GameTestHelper helper, int x, int z, int height, Block log, Block leaves) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, log);
        }
        addLeavesCrown(helper, x, z, height, leaves);
    }

    private static void addLeavesCrown(GameTestHelper helper, int x, int z, int trunkHeight, Block leavesBlock) {
        int top = trunkHeight;
        var leavesState = leavesBlock.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        helper.setBlock(x, top + 1, z, leavesState);
        helper.setBlock(x + 1, top, z, leavesState);
        helper.setBlock(x - 1, top, z, leavesState);
        helper.setBlock(x, top, z + 1, leavesState);
        helper.setBlock(x, top, z - 1, leavesState);
        helper.setBlock(x + 1, top + 1, z, leavesState);
        helper.setBlock(x - 1, top + 1, z, leavesState);
        helper.setBlock(x, top + 1, z + 1, leavesState);
        helper.setBlock(x, top + 1, z - 1, leavesState);
        helper.setBlock(x + 1, top + 1, z + 1, leavesState);
        helper.setBlock(x - 1, top + 1, z - 1, leavesState);
    }

    private static final int HUT_POST_LOGS = 12;
    private static final int TALL_TREE_LOGS = 60;
    private static final int SECTION_POSITIONS = 16 * 16 * 16;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BIG_BUILDING_LOGS = 7 * 7 * 7;

    private static void buildPlankHutOnLedge(GameTestHelper helper, int x, int z) {
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.setBlock(x + dx, 1, z + dz, Blocks.DIRT);
            }
        }
        for (int y = 2; y <= 4; y++) {
            for (int dx = 0; dx <= 2; dx += 2) {
                for (int dz = 0; dz <= 2; dz += 2) {
                    helper.setBlock(x + dx, y, z + dz, Blocks.OAK_LOG);
                }
            }
        }
        for (int y = 2; y <= 3; y++) {
            helper.setBlock(x + 1, y, z, Blocks.OAK_PLANKS);
            helper.setBlock(x, y, z + 1, Blocks.OAK_PLANKS);
            helper.setBlock(x + 2, y, z + 1, Blocks.OAK_PLANKS);
        }
        helper.setBlock(x + 1, 2, z + 2, Blocks.OAK_PLANKS);
        helper.setBlock(x + 1, 3, z + 2, Blocks.GLASS);
        helper.setBlock(x + 1, 2, z, Blocks.OAK_DOOR);
        helper.setBlock(x + 1, 3, z, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(x + 1, 2, z + 1, Blocks.TORCH);
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                helper.setBlock(x + dx, 5, z + dz, Blocks.OAK_PLANKS);
            }
        }
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                boolean ring = dx == -1 || dx == 3 || dz == -1 || dz == 3;
                if (ring) {
                    helper.setBlock(x + dx, 4, z + dz, leaves);
                }
            }
        }
    }

    private static void buildLogHut(GameTestHelper helper, int x, int z, int size) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                for (int y = 1; y <= size; y++) {
                    helper.setBlock(x + dx, y, z + dz, Blocks.OAK_LOG);
                }
            }
        }
    }

    private static void buildLogPillar(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, Blocks.OAK_LOG);
        }
    }

    private static int countBlocksInWorld(GameTestHelper helper, Block block) {
        int count = 0;
        for (int x = -2; x < PLATFORM + 2; x++) {
            for (int y = 0; y < FIELD_HEIGHT; y++) {
                for (int z = -2; z < PLATFORM + 2; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}

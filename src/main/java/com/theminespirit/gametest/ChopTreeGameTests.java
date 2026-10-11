package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChopTreeGameTests {
    private static final int PLATFORM = 12;
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

    @GameTest(template = "empty", batch = "chop_tree_basic", timeoutTicks = 300)
    public static void chopTree_SingleOakFiveLogs_SucceedsWithLogsInInventory(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

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

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_axe_durability", timeoutTicks = 300)
    public static void chopTree_AxeDurability_DecreasesByLogCount(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        spirit.getInventory().setItem(TOOL_SLOT, axe);

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree обязан завершиться успехом, причина: " + spirit.taskFailureReason().orElse("нет"));

            int damage = axe.getDamageValue();
            helper.assertTrue(damage == 5,
                    "прочность топора должна уменьшиться ровно на 5 (по 1 за бревно), фактически " + damage);

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_two_trees", timeoutTicks = 400)
    public static void chopTree_TwoTreesAnySpecies_BothChopped(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 5);
        buildBirchTree(helper, 8, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "any", "count", 2, "radius", 16, "replant", false)),
                SETTLE_TICKS);

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

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_species_filter", timeoutTicks = 400)
    public static void chopTree_BirchAndOakSpeciesBirch_OnlyBirchChopped(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildBirchTree(helper, 3, 3, 5);
        buildOakTree(helper, 8, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "birch", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

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

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_hut_and_pillar", timeoutTicks = 300)
    public static void chopTree_HutAndPillarRejected_NotATreeReturnsFail(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildLogHut(helper, 3, 3, 3);
        buildLogPillar(helper, 8, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "any", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

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

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_eight_logs", timeoutTicks = 400)
    public static void chopTree_EightLogTree_SixesChoppedTwoStanding(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 8);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

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

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_count_exceeds", timeoutTicks = 300)
    public static void chopTree_CountThreeWithOneTree_FailsWithCountMessage(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 3, "radius", 16, "replant", false)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "при count=3 и одном дереве должен быть FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("срублено 1 из 3") && reason.contains("деревья") && reason.contains("закончились"),
                    "причина FAILED: 'срублено 1 из 3: деревья породы oak в радиусе 16 закончились': " + reason);

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_unreachable_island", timeoutTicks = 300)
    public static void chopTree_TreeOnIslandWithMoat_RejectedUnreachable(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildMoat(helper, 6, 6, 5);
        buildOakTree(helper, 6, 6, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "дерево на острове недоступно: должен быть FAILED, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("недоступных: 1"),
                    "причина FAILED обязана содержать 'недоступных: 1': " + reason);
            helper.assertTrue(run.spiritTicks() <= 150,
                    "недоступное дерево обязано определяться за ≤150 тиков, заняло " + run.spiritTicks());

            double spiritX = spirit.getX();
            double spiritZ = spirit.getZ();
            helper.assertTrue(spiritX <= 1 || spiritX >= 11 || spiritZ <= 1 || spiritZ >= 11,
                    "дух не должен попасть в яму (x,z в [2..10]), позиция духа: " + spiritX + ", " + spiritZ);

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_cancel", timeoutTicks = 300)
    public static void chopTree_CancelMidChop_WorldUnchangedAfter40Ticks(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

        int[] cancelAt = { -1 };
        int[] marker = { -1 };

        helper.succeedWhen(() -> {
            if (cancelAt[0] == -1 && spirit.taskStatus().orElse(null) == RunStatus.RUNNING) {
                cancelAt[0] = spirit.tickCount;
                spirit.cancelTask();
                marker[0] = spirit.tickCount;
            }
            if (marker[0] != -1 && spirit.tickCount >= marker[0] + 40) {
                int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
                int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
                int leavesInWorld = countBlocksInWorld(helper, Blocks.OAK_LEAVES);

                helper.assertTrue(logsInWorld == 5,
                        "через 40 тиков после отмены мир не должен меняться: должно быть 5 брёвен, осталось " + logsInWorld);
                helper.assertTrue(logsInInventory == 0,
                        "инвентарь должен быть пуст (рубка отменена до подбора): " + logsInInventory);
                helper.assertTrue(leavesInWorld > 0,
                        "листва должна остаться нетронутой: " + leavesInWorld);
                helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                        "статус задачи после отмены обязан быть CANCELLED: "
                                + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));

                GameTestFixtures.reset(helper);
            }
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_no_axe", timeoutTicks = 500)
    public static void chopTree_NoAxeTwoLogs_VanillaTimeCommented(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 2);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 16, "replant", false)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "рубка без топора (рукой) должна сработать, причина: " + spirit.taskFailureReason().orElse("нет"));
            // Ванильное время: 60 тиков на бревно, 2 бревна = 120 тиков + подход + подбор
            // Тест выполняется дольше, таймаут 400 тиков в批次 показывает это
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logsInInventory == 2,
                    "должно быть 2 дубовых бревна в инвентаре, фактически " + logsInInventory);

            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "chop_tree_replant", timeoutTicks = 400)
    public static void chopTree_ReplantWithSapling_SaplingPlacedOnBase(GameTestHelper helper) {
        resetAndBuildPlatform(helper);
        buildOakTree(helper, 3, 3, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.OAK_SAPLING));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 16, "replant", true)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree с replant=true обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            BlockPos base = helper.absolutePos(new BlockPos(3, 1, 3));
            Block blockBelow = helper.getLevel().getBlockState(base.below()).getBlock();
            helper.assertTrue(blockBelow == Blocks.DIRT,
                    "под основанием должна быть почва: " + blockBelow);

            Block blockAtBase = helper.getLevel().getBlockState(base).getBlock();
            helper.assertTrue(blockAtBase == Blocks.OAK_SAPLING,
                    "на месте ствола (базового бревна) должен стоять саженец дуба, а там " + blockAtBase);

            int saplingsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_SAPLING);
            helper.assertTrue(saplingsInInventory == 0,
                    "саженец должен быть израсходован (0 в инвентаре), фактически " + saplingsInInventory);

            GameTestFixtures.reset(helper);
        });
    }

    private static void resetAndBuildPlatform(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
    }

    private static void buildOakTree(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, Blocks.OAK_LOG);
        }
        addLeavesCrown(helper, x, z, height, Blocks.OAK_LEAVES);
    }

    private static void buildBirchTree(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, Blocks.BIRCH_LOG);
        }
        addLeavesCrown(helper, x, z, height, Blocks.BIRCH_LEAVES);
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

    private static void buildMoat(GameTestHelper helper, int centerX, int centerZ, int width) {
        int innerX = centerX - width;
        int innerZ = centerZ - width;
        int outerX = centerX + width;
        int outerZ = centerZ + width;
        for (int x = innerX; x <= outerX; x++) {
            for (int z = innerZ; z <= outerZ; z++) {
                if (x < innerX + 1 || x > outerX - 1 || z < innerZ + 1 || z > outerZ - 1) {
                    helper.setBlock(x, 0, z, Blocks.AIR);
                    helper.setBlock(x, 1, z, Blocks.AIR);
                }
            }
        }
    }

    private static int countBlocksInWorld(GameTestHelper helper, Block block) {
        int count = 0;
        for (int x = -2; x < PLATFORM + 2; x++) {
            for (int y = 0; y < 20; y++) {
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
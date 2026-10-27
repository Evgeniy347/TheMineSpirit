package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.addLeavesCrown;
import static com.theminespirit.gametest.ChopSceneSupport.buildBirchTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildLogPillar;
import static com.theminespirit.gametest.ChopSceneSupport.buildOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopSceneSupport.prepareField;
import static com.theminespirit.gametest.ChopTestSupport.FIELD;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.SPIRIT_START;
import static com.theminespirit.gametest.ChopTestSupport.TOOL_SLOT;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopBasicsGameTests {
    private ChopBasicsGameTests() {}

    @SceneStep(template = "empty", timeoutTicks = 20)
    public static void treeSpecies_AllIds_ExistInRegistries(GameTestHelper helper) {
        for (TreeSpecies species : TreeSpecies.ALL) {
            helper.assertTrue(
                    BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(species.logId())),
                    "порода '" + species.id() + "': блок брёвен '" + species.logId() + "' отсутствует в реестре "
                            + "блоков — опечатка в id молча дала бы 'деревьев нет'");
            helper.assertTrue(
                    BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(species.leavesId())),
                    "порода '" + species.id() + "': блок листьев '" + species.leavesId() + "' отсутствует в реестре "
                            + "блоков — дерево не распозналось бы как настоящее");
            helper.assertTrue(
                    BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(species.saplingId())),
                    "порода '" + species.id() + "': саженец '" + species.saplingId() + "' отсутствует в реестре "
                            + "предметов — посадка после рубки не смогла бы его поставить");
        }
        helper.succeed();
    }

    @SceneStep(template = FIELD, timeoutTicks = 300)
    public static void chopTree_SingleOakFiveLogs_SucceedsWithLogsInInventory(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);
        ItemStack axe = spirit.getInventory().getItem(TOOL_SLOT);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() <= 200,
                    "рубка 5 брёвен с железным топором обязана уложиться в 200 тиков, заняло " + run.spiritTicks());

            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    logsInInventory == 5, "в инвентаре должно быть 5 дубовых брёвен, фактически " + logsInInventory);

            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(logsInWorld == 0, "в мире не должно остаться дубовых брёвен, осталось " + logsInWorld);

            int droppedLogs = GameTestFixtures.countInWorld(helper, Items.OAK_LOG);
            helper.assertTrue(
                    droppedLogs == 0, "сборщик обязан подобрать все выпавшие брёвна, осталось валяться " + droppedLogs);

            int leavesInWorld = countBlocksInWorld(helper, Blocks.OAK_LEAVES);
            helper.assertTrue(
                    leavesInWorld > 0, "листва должна остаться целой, осталось " + leavesInWorld + " блоков листьев");
            helper.assertTrue(
                    axe.getDamageValue() == 5,
                    "прочность топора должна уменьшиться ровно на 5 (по 1 за бревно), фактически "
                            + axe.getDamageValue());

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_TwoTreesAnySpecies_BothChopped(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        buildBirchTree(helper, 7, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("any", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree с count=2 и species=any обязан срубить оба дерева, причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            int oakLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            int birchLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BIRCH_LOG);
            helper.assertTrue(
                    oakLogs == 5 && birchLogs == 5,
                    "должно быть 5 дубовых и 5 берёзовых брёвен, фактически дуб=" + oakLogs + ", берёза=" + birchLogs);

            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG) + countBlocksInWorld(helper, Blocks.BIRCH_LOG);
            helper.assertTrue(logsInWorld == 0, "в мире не должно остаться брёвен, осталось " + logsInWorld);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_BirchAndOakSpeciesBirch_OnlyBirchChopped(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        buildBirchTree(helper, 7, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("birch", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree с species=birch обязан срубить только берёзу, причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            int birchLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BIRCH_LOG);
            int oakLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    birchLogs == 5 && oakLogs == 0,
                    "должно быть 5 берёзовых и 0 дубовых брёвен, фактически берёза=" + birchLogs + ", дуб=" + oakLogs);

            int birchInWorld = countBlocksInWorld(helper, Blocks.BIRCH_LOG);
            int oakInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    birchInWorld == 0 && oakInWorld == 5,
                    "берёза должна быть срублена (0 в мире), дуб должен остаться (5 в мире), " + "фактически берёза="
                            + birchInWorld + ", дуб=" + oakInWorld);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_TwoAdjacentOaks_OnlySelectedChopped(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        buildOakTree(helper, 6, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree с count=1 обязан срубить ровно одно дерево из двух вплотную, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    logsInInventory == 5,
                    "в инвентарь обязано попасть одно дерево (5 брёвен), фактически " + logsInInventory);
            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    logsInWorld == 5,
                    "второе дерево вплотную обязано уцелеть целиком (5 брёвен), осталось " + logsInWorld);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 300)
    public static void chopTree_WithoutAxeTwoLogs_SucceedsByHand(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        buildLogPillar(helper, 4, 4, 2);
        addLeavesCrown(helper, 4, 4, 3, Blocks.OAK_LEAVES);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "без инструмента дух рубит рукой — это не ошибка, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() >= 2 * 60,
                    "рука бьёт бревно 60 тиков (ванильная твёрдость 2.0 без инструмента), два бревна не быстрее "
                            + "120 тиков, а заняло " + run.spiritTicks());
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logsInInventory == 2, "в инвентаре должно быть 2 бревна, фактически " + logsInInventory);

            clearField(helper);
        });
    }
}

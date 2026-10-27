package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.buildOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildTree;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopSceneSupport.prepareField;
import static com.theminespirit.gametest.ChopTestSupport.FIELD;
import static com.theminespirit.gametest.ChopTestSupport.LOGGER;
import static com.theminespirit.gametest.ChopTestSupport.MAX_TREE_READS;
import static com.theminespirit.gametest.ChopTestSupport.MEASURED_TREE_READS;
import static com.theminespirit.gametest.ChopTestSupport.SEARCH_RADIUS;
import static com.theminespirit.gametest.ChopTestSupport.SECTION_POSITIONS;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.TALL_TREE_LOGS;
import static com.theminespirit.gametest.ChopTestSupport.TOOL_SLOT;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.chopParams;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.driveScript;
import static com.theminespirit.gametest.ChopTestSupport.positionsInSectionsWithLogs;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.neoforge.ChopTreeScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopBudgetGameTests {
    private ChopBudgetGameTests() {}

    @SceneStep(template = FIELD, timeoutTicks = 200)
    public static void chopTree_SixtyLogTree_ParsesWithinTwentySevenReadsPerLog(GameTestHelper helper) {
        prepareField(helper);
        buildTree(helper, 4, 4, TALL_TREE_LOGS, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        SpiritEntity spirit = spawnChopper(helper);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, TALL_TREE_LOGS, 4)).isAir(),
                    "рубка обязана начаться с самого дальнего (верхнего) бревна: разбор дерева завершён, "
                            + "счётчик чтений замер, статус " + status[0] + ", причина: " + script.failureReason());
            long reads = script.treeBlockReads();
            helper.assertTrue(
                    reads >= TALL_TREE_LOGS,
                    "нижняя граница разбора: прочитано каждое из " + TALL_TREE_LOGS
                            + " брёвен дерева — без чтения клетки ствола разбор не соберёт группу, поэтому "
                            + TALL_TREE_LOGS + " — минимум, а прочитано " + reads
                            + ": нулевой счётчик означал бы, что скрипт читает мир мимо LevelTreeView");
            helper.assertTrue(
                    reads <= 27L * TALL_TREE_LOGS,
                    "разбор одного дерева из " + TALL_TREE_LOGS + " брёвен читает не больше 27 клеток на бревно ("
                            + 27L * TALL_TREE_LOGS + "), прочитано " + reads
                            + ": повторное чтение соседей или новый вид на каждый разбор завешивают тик");
            helper.assertTrue(
                    reads <= MAX_TREE_READS,
                    "верхняя граница разбора: не больше " + MAX_TREE_READS + " клеток (замер " + MEASURED_TREE_READS
                            + " × 1.5), прочитано " + reads
                            + ": чтение вдвое больше замера (" + 2 * MEASURED_TREE_READS
                            + ") или отключённый кеш клетки обязаны краснеть здесь, не дотягивая до бюджета "
                            + 27L * TALL_TREE_LOGS + ". Прежняя граница была посажена на замер ровно и красила "
                            + "тест на одном лишнем чтении; если обход читает мир шире осознанно — перепроверь "
                            + "кеш LevelTreeView и подними границу вместе с MEASURED_TREE_READS");
            LOGGER.info(
                    "chop_tree, дерево из {} брёвен: разбор прочитал {} клеток мира, граница {}, бюджет {}",
                    TALL_TREE_LOGS,
                    reads,
                    MAX_TREE_READS,
                    27L * TALL_TREE_LOGS);

            clearField(helper, TALL_TREE_LOGS + 10);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_Search_ReadsOnlySectionsWithLogsAndBoundedPerTick(GameTestHelper helper) {
        prepareField(helper);
        buildTree(helper, 4, 4, 5, Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES);
        SpiritEntity spirit = spawnChopper(helper);
        BlockPos center = spirit.blockPosition();
        int expected = positionsInSectionsWithLogs(helper.getLevel(), center, SEARCH_RADIUS, Blocks.ACACIA_LOG);
        int cubeVolume = (2 * SEARCH_RADIUS + 1) * (2 * SEARCH_RADIUS + 1) * (2 * SEARCH_RADIUS + 1);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("acacia", 1));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    status[0] == ScriptStatus.SUCCEEDED,
                    "рубка одной акации успешна, статус " + status[0] + ", причина: " + script.failureReason());
            int examined = script.searchExaminedPositions();
            helper.assertTrue(
                    expected > 0 && expected < cubeVolume,
                    "в кубе поиска есть секции без брёвен (ожидание " + expected + " < куба " + cubeVolume
                            + "), иначе проверка «читает только секции с брёвнами» ничего не доказывает");
            helper.assertTrue(
                    examined == expected,
                    "поиск читает позиции куба только в секциях, где есть брёвна породы: ожидалось " + expected
                            + ", прочитано " + examined + ". Больше — читаются секции без брёвен, меньше — "
                            + "секция с брёвнами пропущена и дерево может не найтись. Расчёт «27 клеток на бревно» "
                            + "к поиску не относится: поиск читает срез секции целиком, а не соседей брёвен");
            helper.assertTrue(
                    script.searchMaxExaminedPositionsPerTick() <= ScanTuning.SLICES_PER_TICK * SECTION_POSITIONS,
                    "за тик читается не больше " + ScanTuning.SLICES_PER_TICK + " срезов секций ("
                            + ScanTuning.SLICES_PER_TICK * SECTION_POSITIONS + " позиций), а прочитано "
                            + script.searchMaxExaminedPositionsPerTick());

            LOGGER.info(
                    "chop_tree, поиск радиуса {}: прочитано {} позиций, ожидалось {} (куб {}), " + "максимум за тик {}",
                    SEARCH_RADIUS,
                    examined,
                    expected,
                    cubeVolume,
                    script.searchMaxExaminedPositionsPerTick());

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_ReloadedAfterFirstTree_ChopsExactlyThreeTrees(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        buildOakTree(helper, 3, 3, 5);
        buildOakTree(helper, 6, 3, 5);
        buildOakTree(helper, 9, 3, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 3), SETTLE_TICKS);

        SpiritEntity[] current = {spirit};
        boolean[] reloaded = {false};
        helper.onEachTick(() -> {
            Optional<String> snapshot = GameTestFixtures.taskSnapshot(current[0]);
            if (reloaded[0] || snapshot.isEmpty()) {
                return;
            }
            TaskProgress progress = TaskSnapshotCodec.decode(snapshot.get()).progress();
            if (progress instanceof TaskProgress.ScriptAt at
                    && at.state().get("chopped") instanceof Number chopped
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
            helper.assertTrue(
                    reloaded[0],
                    "после первого срубленного дерева снимок задачи обязан показать chopped=1: иначе прогресс "
                            + "рубки не сохраняется и перезапуск начинает счёт заново");
            SpiritEntity now = current[0];
            helper.assertTrue(
                    now.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "после перезагрузки рубка продолжается с chopped=1 и срубает ровно два оставшихся дерева: "
                            + "SUCCEEDED, а не FAILED «срублено 2 из 3». Статус: "
                            + now.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + now.taskFailureReason().orElse("нет"));
            int logs = GameTestFixtures.countInInventory(now.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logs == 15, "три дерева по 5 брёвен: 15 в инвентаре, фактически " + logs);
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.OAK_LOG) == 0, "в мире не должно остаться брёвен трёх деревьев");

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 170)
    public static void chopTree_SecondOakVanishesDuringFirst_FailsWithTreesRanOut(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 3, 3, 5);
        buildOakTree(helper, 7, 3, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 2), SETTLE_TICKS);

        boolean[] removed = {false};
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
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "второго дуба больше нет: FAILED «деревья закончились», статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.startsWith("срублено деревьев 1 из 2") && reason.contains("закончились"),
                    "исчезнувшее бревно кандидата не должно ронять скрипт («скрипт упал»), а причина должна быть "
                            + "«срублено деревьев 1 из 2 … закончились»: " + reason);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_TwoSpiritsOneTree_LoserFailsWithTreesRanOutNotAir(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity first = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, 1, 3));
        SpiritEntity second = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, 1, 5));
        first.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));
        second.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun firstRun = SpiritRun.launch(helper, first, chopCall("oak", 1), SETTLE_TICKS);
        SpiritRun secondRun = SpiritRun.launch(helper, second, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            firstRun.assertStarted();
            secondRun.assertStarted();
            RunStatus firstStatus = first.taskStatus().orElse(null);
            RunStatus secondStatus = second.taskStatus().orElse(null);
            helper.assertTrue(
                    firstStatus != RunStatus.RUNNING && secondStatus != RunStatus.RUNNING,
                    "одно дерево на двоих обязано завершить обе задачи, а не висеть: статусы " + firstStatus + " и "
                            + secondStatus);
            helper.assertTrue(
                    firstStatus == RunStatus.SUCCEEDED || secondStatus == RunStatus.SUCCEEDED,
                    "срубить единственное дерево обязан ровно один дух: статусы " + firstStatus + " и " + secondStatus);
            SpiritEntity loser = firstStatus == RunStatus.SUCCEEDED ? second : first;
            RunStatus loserStatus = loser.taskStatus().orElse(null);
            String reason = loser.taskFailureReason().orElse("");
            helper.assertTrue(
                    loserStatus == RunStatus.FAILED && reason.contains("закончились"),
                    "проигравший гонку за бронь обязан отказаться понятной причиной «деревья … закончились», "
                            + "а не падать на воздухе: статус " + loserStatus + ", причина: " + reason);
            helper.assertTrue(
                    !reason.contains("Основание дерева") && !reason.contains("скрипт упал"),
                    "уже срубленное дерево пропускается пересканированием без исключения: причина " + reason);
            clearField(helper);
        });
    }
}

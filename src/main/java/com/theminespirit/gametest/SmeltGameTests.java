package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.FIELD;
import static com.theminespirit.gametest.CraftTestSupport.TIMEOUT;
import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.assertReason;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.launch;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.reload;
import static com.theminespirit.gametest.CraftTestSupport.speedUpCampfires;
import static com.theminespirit.gametest.CraftTestSupport.speedUpFurnaces;
import static com.theminespirit.gametest.CraftTestSupport.stack;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmeltGameTests {
    private static final BlockPos FURNACE_CELL = new BlockPos(3, 1, 1);

    private SmeltGameTests() {}

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_ThreeRawIronTwoCoal_ThreeIngotsOneCoalLeftFurnaceEmpty(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 3), stack(Items.COAL, 2));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 3);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 3, "слитки железа");
            assertCount(helper, spirit, Items.RAW_IRON, 0, "сырое железо после плавки");
            assertCount(
                    helper,
                    spirit,
                    Items.COAL,
                    1,
                    "уголь после плавки: на 3 предмета хватает одного, " + "лишний обязан остаться");
            assertFurnaceEmpty(helper);
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_ThreeRawIronTwoOakPlanks_ThreeIngotsBothPlanksBurned(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 3), stack(Items.OAK_PLANKS, 2));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 3);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 3, "слитки железа при плавке досками");
            assertCount(helper, spirit, Items.RAW_IRON, 0, "сырое железо после плавки досками");
            assertCount(
                    helper,
                    spirit,
                    Items.OAK_PLANKS,
                    0,
                    "три плавки по 200 тиков требуют 600 тиков горения, "
                            + "доска горит 300 — израсходованы обе, остаток досок");
            assertFurnaceEmpty(helper);
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_FurnaceOnlyInInventory_PlacedAndUsed(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.FURNACE, 1), stack(Items.RAW_IRON, 1), stack(Items.COAL, 1));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 1, "слиток железа из поставленной печи");
            assertCount(helper, spirit, Items.FURNACE, 0, "печь в инвентаре после установки");
            helper.assertTrue(
                    furnaceCount(helper) == 1,
                    "дух обязан поставить ровно одну печь из инвентаря, на поле их " + furnaceCount(helper));
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_NoFurnaceAnywhere_FailsBeforeSpending(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 3), stack(Items.COAL, 2));
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 3);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "нужна печь");
            assertCount(helper, spirit, Items.RAW_IRON, 3, "сырое железо при отказе без печи");
            assertCount(helper, spirit, Items.COAL, 2, "уголь при отказе без печи");
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_NoFuel_FailsLeavingInputInInventory(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 3));
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 3);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "нет топлива");
            assertCount(helper, spirit, Items.RAW_IRON, 3, "сырое железо при отказе без топлива");
            assertFurnaceEmpty(helper);
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_OneStickForThreeItems_FailsFuelRanOutAndReturnsInput(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 3), stack(Items.STICK, 1));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 3);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "топливо кончилось: переплавлено 0 из 3");
            assertCount(helper, spirit, Items.RAW_IRON, 3, "сырое железо после провала по топливу");
            assertCount(helper, spirit, Items.IRON_INGOT, 0, "слитки при провале до первого результата");
            assertFurnaceEmpty(helper);
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_CancelledInTheMiddle_ReturnsEverythingToSpirit(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 8), stack(Items.COAL, 3));
        speedUpFurnaces(helper);
        launch(helper, spirit, "smelt", "iron_ingot", 8);
        boolean[] cancelled = {false};
        helper.onEachTick(() -> {
            if (!cancelled[0] && firstIngotReady(helper)) {
                spirit.cancelTask();
                cancelled[0] = true;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(cancelled[0], "отмена ещё не выдана");
            int raw = GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON);
            int ingots = GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT);
            helper.assertTrue(
                    ingots > 0 && ingots < 8,
                    "к моменту отмены часть слитков уже готова, но не все: слитков " + ingots);
            helper.assertTrue(
                    raw + ingots == 8,
                    "после отмены вход и продукты обязаны вернуться духу без потерь: сырого " + raw + " + слитков "
                            + ingots + " должно быть 8");
            assertFurnaceEmpty(helper);
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_SpiritReloadedMidway_AdoptsItsFurnaceAndFinishesExactly(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 8), stack(Items.COAL, 3));
        speedUpFurnaces(helper);
        launch(helper, spirit, "smelt", "iron_ingot", 8);
        SpiritEntity[] current = {spirit};
        boolean[] reloaded = {false};
        helper.onEachTick(() -> {
            if (!reloaded[0] && firstIngotReady(helper)) {
                current[0] = reload(helper, spirit);
                reloaded[0] = true;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(reloaded[0], "перезагрузка духа ещё не выполнена");
            SpiritEntity now = current[0];
            helper.assertTrue(
                    now.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленная плавка обязана завершиться успехом, статус: "
                            + now.taskStatus().orElse(null) + ", причина: "
                            + now.taskFailureReason().orElse("нет"));
            assertCount(
                    helper,
                    now,
                    Items.IRON_INGOT,
                    8,
                    "слитки после перезапуска: готовое из печи забрано, " + "новый вход не положен поверх");
            assertCount(helper, now, Items.RAW_IRON, 0, "сырое железо после перезапуска");
            assertFurnaceEmpty(helper);
            cleanup(helper, now);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_IronOreInsteadOfRawIron_IsAccepted(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.IRON_ORE, 2), stack(Items.COAL, 1));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 2);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 2, "слитки из железной руды");
            assertCount(helper, spirit, Items.IRON_ORE, 0, "руда после плавки");
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_BeefToCookedBeef_ProductNamedNotInput(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.BEEF, 2), stack(Items.COAL, 1));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "cooked_beef", 2);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.COOKED_BEEF, 2, "стейки");
            assertCount(helper, spirit, Items.BEEF, 0, "сырая говядина после плавки");
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_BeefOnCampfire_CookedWithoutFuel(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.CAMPFIRE);
        SpiritEntity spirit = prepare(helper, stack(Items.BEEF, 2));
        speedUpCampfires(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "cooked_beef", 2);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.COOKED_BEEF, 2, "стейки с костра без топлива");
            assertCount(helper, spirit, Items.BEEF, 0, "сырая говядина после готовки на костре");
            assertCampfireEmpty(helper);
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_NoCampfireWithMaterials_BuildsItAndCooks(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(
                helper, stack(Items.BEEF, 2), stack(Items.STICK, 3), stack(Items.COAL, 1), stack(Items.OAK_LOG, 3));
        speedUpCampfires(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "cooked_beef", 2);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.COOKED_BEEF, 2, "стейки на поставленном костре");
            assertCount(helper, spirit, Items.BEEF, 0, "сырая говядина после готовки на поставленном костре");
            assertCount(helper, spirit, Items.STICK, 0, "палки, ушедшие на костёр");
            assertCount(helper, spirit, Items.COAL, 0, "уголь, ушедший на костёр");
            assertCount(helper, spirit, Items.OAK_LOG, 0, "брёвна, ушедшие на костёр");
            helper.assertTrue(
                    campfireCount(helper) == 1,
                    "дух обязан поставить ровно один костёр из материалов, на поле их " + campfireCount(helper));
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_BeefInSmoker_CookedBeefInChest(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.SMOKER);
        SpiritEntity spirit = prepare(helper, stack(Items.BEEF, 2), stack(Items.COAL, 1));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "cooked_beef", 2);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.COOKED_BEEF, 2, "стейки из коптильни");
            assertCount(helper, spirit, Items.BEEF, 0, "сырая говядина после копчения");
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_LogsInFurnace_Charcoal(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_LOG, 2), stack(Items.COAL, 1));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "charcoal", 2);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.CHARCOAL, 2, "древесный уголь из брёвен");
            assertCount(helper, spirit, Items.OAK_LOG, 0, "брёвна после плавки");
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_FurnaceDestroyedInTheMiddle_FailsAndNothingIsLost(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 8), stack(Items.COAL, 3));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 8);
        boolean[] destroyed = {false};
        helper.onEachTick(() -> {
            if (!destroyed[0] && firstIngotReady(helper)) {
                helper.destroyBlock(FURNACE_CELL);
                destroyed[0] = true;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(destroyed[0], "печь ещё не разрушена");
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "исчезла");
            int total = totalOf(helper, spirit, Items.RAW_IRON) + totalOf(helper, spirit, Items.IRON_INGOT);
            helper.assertTrue(
                    total == 8,
                    "после разрушения печи вход и продукты не должны пропасть (в инвентаре и выпавшие в мир): "
                            + "сырого и слитков вместе " + total + " из 8");
            cleanup(helper, spirit);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = TIMEOUT)
    public static void smelt_FourRawIronWithPlayerOnline_AwardsExperienceToPlayerWithoutOrbs(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(7, 1, 7), "xp_smelt_player");
        player.totalExperience = 0;
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 4), stack(Items.COAL, 1));
        speedUpFurnaces(helper);
        SpiritRun run = launch(helper, spirit, "smelt", "iron_ingot", 4);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 4, "слитки железа");
            assertFurnaceEmpty(helper);
            assertRecipesCleared(helper);
            helper.assertTrue(
                    player.totalExperience > 0,
                    "опыт, накопленный печью за плавку, обязан достаться реальному игроку, а его опыт "
                            + player.totalExperience);
            int orbs = orbsNear(helper).size();
            helper.assertTrue(
                    orbs == 0, "опыт плавки уходит игроку напрямую, а не сферами на землю: на земле сфер " + orbs);
            cleanup(helper, spirit);
        });
    }

    private static List<ExperienceOrb> orbsNear(GameTestHelper helper) {
        Vec3 first = helper.absoluteVec(new Vec3(-2, 0, -2));
        Vec3 second = helper.absoluteVec(new Vec3(GameTestFixtures.FLOOR_SIZE + 2, 8, GameTestFixtures.FLOOR_SIZE + 2));
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(first, second));
    }

    private static void assertRecipesCleared(GameTestHelper helper) {
        BlockEntity entity = helper.getBlockEntity(FURNACE_CELL);
        helper.assertTrue(
                entity instanceof AbstractFurnaceBlockEntity furnace && furnace.recipesUsed.isEmpty(),
                "после выноса результата счётчик рецептов печи обязан обнулиться, как в ванили: иначе следующий "
                        + "забор результата выдаст тот же опыт повторно");
    }

    private static boolean firstIngotReady(GameTestHelper helper) {
        BlockEntity entity = helper.getBlockEntity(FURNACE_CELL);
        return entity instanceof AbstractFurnaceBlockEntity furnace
                && !furnace.getItem(2).isEmpty();
    }

    private static int totalOf(GameTestHelper helper, SpiritEntity spirit, Item item) {
        return GameTestFixtures.countInInventory(spirit.getInventory(), item)
                + GameTestFixtures.countInWorld(helper, item);
    }

    private static int furnaceCount(GameTestHelper helper) {
        int furnaces = 0;
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.FURNACE)) {
                    furnaces++;
                }
            }
        }
        return furnaces;
    }

    private static int campfireCount(GameTestHelper helper) {
        int campfires = 0;
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.CAMPFIRE)) {
                    campfires++;
                }
            }
        }
        return campfires;
    }

    private static void assertCampfireEmpty(GameTestHelper helper) {
        BlockEntity entity = helper.getBlockEntity(FURNACE_CELL);
        helper.assertTrue(entity instanceof CampfireBlockEntity, "в клетке костра нет костра");
        CampfireBlockEntity campfire = (CampfireBlockEntity) entity;
        for (int slot = 0; slot < campfire.getItems().size(); slot++) {
            ItemStack stack = campfire.getItems().get(slot);
            helper.assertTrue(
                    stack.isEmpty(), "костёр обязан быть пуст после готовки, в слоте " + slot + " лежит " + stack);
        }
    }

    private static void assertFurnaceEmpty(GameTestHelper helper) {
        BlockEntity entity = helper.getBlockEntity(FURNACE_CELL);
        helper.assertTrue(entity instanceof AbstractFurnaceBlockEntity, "в клетке печи нет печи");
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) entity;
        for (int slot = 0; slot < furnace.getContainerSize(); slot++) {
            ItemStack stack = furnace.getItem(slot);
            helper.assertTrue(
                    stack.isEmpty(), "печь обязана быть пуста после работы, в слоте " + slot + " лежит " + stack);
        }
    }
}

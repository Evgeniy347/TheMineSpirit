package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.accessChest;
import static com.theminespirit.gametest.InventorySceneSupport.chestCount;
import static com.theminespirit.gametest.InventorySceneSupport.coordsOf;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.ContainerTarget;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerSort;
import com.theminespirit.neoforge.inventory.DepositScript;
import com.theminespirit.neoforge.inventory.TransferRunner;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventoryDepositLimitsGameTests {
    private InventoryDepositLimitsGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_ChestFull_FailsWithoutMoving(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        Item[] fillers = {
            Items.DIRT,
            Items.SAND,
            Items.GRAVEL,
            Items.OAK_LOG,
            Items.BIRCH_LOG,
            Items.SPRUCE_LOG,
            Items.OAK_PLANKS,
            Items.STICK,
            Items.TORCH,
            Items.COAL,
            Items.CHARCOAL,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.COPPER_INGOT,
            Items.REDSTONE,
            Items.LAPIS_LAZULI,
            Items.DIAMOND,
            Items.EMERALD,
            Items.BREAD,
            Items.COOKED_BEEF,
            Items.APPLE,
            Items.ARROW,
            Items.BOW,
            Items.STONE,
            Items.ANDESITE,
            Items.GRANITE,
            Items.DIORITE
        };
        for (int slot = 0; slot < 27; slot++) {
            chest.setItem(slot, new ItemStack(fillers[slot]));
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 10, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "полный сундук обязан провалить deposit, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("полон") && reason.contains("0 из 10"),
                    "причина обязана говорить, что контейнер полон и перенесено 0 из 10, фактически: " + reason);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 10,
                    "инвентарь духа обязан остаться нетронутым: все 10 булыжника при нём");
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 0,
                    "в полный сундук не должно попасть ни одного булыжника");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_SixOfTenRequested_MovesSixAndFails(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 6));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 10, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "недостача обязана проваливать deposit, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("только 6 из 10"),
                    "причина обязана называть недостачу «только 6 из 10», фактически: " + reason);
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 6,
                    "уже перенесённое остаётся перенесённым: в сундуке обязано быть 6 булыжника, фактически "
                            + chestCount(helper, Items.COBBLESTONE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "у духа не должно остаться булыжника: всё имевшееся уехало");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_LootChestCoords_RefusesWithoutTouching(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(6, 1, 4));
        RandomizableContainerBlockEntity entity =
                (RandomizableContainerBlockEntity) helper.getLevel().getBlockEntity(chest);
        entity.setLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("minecraft:chests/simple_dungeon")));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of(
                                "container",
                                coordsOf(chest),
                                "item",
                                "minecraft:cobblestone",
                                "count",
                                10,
                                "radius",
                                8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "сундук с нераскрытой таблицей добычи обязан проваливать deposit, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("добыч"),
                    "причина обязана пояснять про нераскрытую добычу, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 10,
                    "инвентарь духа обязан остаться нетронутым: в запретный сундук ничего не кладут");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_FurnaceCoords_RefusesWithoutTouching(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.FURNACE);
        BlockPos furnace = helper.absolutePos(new BlockPos(6, 1, 4));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of(
                                "container",
                                coordsOf(furnace),
                                "item",
                                "minecraft:cobblestone",
                                "count",
                                10,
                                "radius",
                                8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "печь обязана проваливать deposit, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("не допустимый контейнер"),
                    "причина обязана пояснять, что печь не допустимый контейнер, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 10,
                    "инвентарь духа обязан остаться нетронутым");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_ManyStacks_LimitedMovesSlicesAndCachedTables(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        for (int slot = 0; slot < 10; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 25));
        }
        DepositScript script =
                new DepositScript(spirit, new TransferParams(ContainerTarget.NEAREST, "minecraft:cobblestone", 250, 8));
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<ScriptStatus> state = new AtomicReference<>(ScriptStatus.RUNNING);
        helper.onEachTick(() -> {
            if (state.get() == ScriptStatus.RUNNING) {
                ticks.incrementAndGet();
                state.set(script.tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    state.get() == ScriptStatus.SUCCEEDED,
                    "перенос 640 булыжника обязан завершиться успехом, причина: " + script.failureReason());
            helper.assertTrue(
                    ticks.get() >= 3,
                    "10 перемещений порциями не укладываются в один тик: тиков должно быть не меньше 3, " + "а прошло "
                            + ticks.get());
            helper.assertTrue(
                    TransferRunner.TRANSFER_MOVES_PER_TICK == 4,
                    "лимит перемещений за тик равен 4: больше вешало бы серверный тик. Фактически "
                            + TransferRunner.TRANSFER_MOVES_PER_TICK);
            helper.assertTrue(
                    script.maxMovesPerTick() <= TransferRunner.TRANSFER_MOVES_PER_TICK,
                    "скрипт контейнера делает не больше 4 перемещений за тик: иначе долгий перенос вешал бы "
                            + "серверный тик. Максимум за тик: " + script.maxMovesPerTick());
            helper.assertTrue(
                    script.maxSlicesPerTick() <= 4,
                    "поиск ближайшего контейнера идёт порциями не больше 4 срезов за тик: иначе поиск "
                            + "в радиусе вешал бы тик. Максимум за тик: " + script.maxSlicesPerTick());
            helper.assertTrue(
                    ContainerSort.MAX_SLOTS == 90,
                    "раскладка сортёра применяется одним проходом на контейнеры до 90 слотов. Фактически "
                            + ContainerSort.MAX_SLOTS);
            GameTestFixtures.finish(helper);
        });
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.ContainerTarget;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.ContainerSort;
import com.theminespirit.neoforge.inventory.DepositScript;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import com.theminespirit.neoforge.inventory.TransferRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InventoryGameTests {
    private InventoryGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_SingleChest_Opens27SlotChest(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 4)));

        helper.assertTrue(
                open instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 27,
                "одиночный сундук обязан открываться как контейнер на 27 слотов, фактически " + describe(open));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_AdjacentChests_OpenAsDouble(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        placeDoubleChest(helper, 5, 6, 1, 4);
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 4)));

        helper.assertTrue(
                open instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 54,
                "два соседних сундука обязаны открываться как двойной на 54 слота, фактически " + describe(open));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_BarrelAndShulker_Open(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(5, 1, 3, Blocks.BARREL);
        helper.setBlock(6, 1, 5, Blocks.WHITE_SHULKER_BOX);
        ContainerAccess.Open barrel =
                ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 3)));
        ContainerAccess.Open shulker =
                ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 5)));

        helper.assertTrue(
                barrel instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 27,
                "бочка обязана открываться как контейнер на 27 слотов, фактически " + describe(barrel));
        helper.assertTrue(
                shulker instanceof ContainerAccess.Opened opened
                        && opened.container().getContainerSize() == 27,
                "шалкер-бокс обязан открываться как контейнер на 27 слотов, фактически " + describe(shulker));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void containerAccess_FurnaceHopperDispenserEnder_Refused(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(2, 1, 2, Blocks.FURNACE);
        helper.setBlock(3, 1, 2, Blocks.HOPPER);
        helper.setBlock(4, 1, 2, Blocks.DISPENSER);
        helper.setBlock(5, 1, 2, Blocks.ENDER_CHEST);

        for (int x = 2; x <= 5; x++) {
            ContainerAccess.Open open =
                    ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 2)));
            helper.assertTrue(
                    open instanceof ContainerAccess.Refused refused
                            && !refused.reason().isBlank(),
                    "печи, воронки, раздатчики и эндер-сундук запрещены: блок в (" + x + ",1,2) обязан дать отказ "
                            + "с пояснением, фактически " + describe(open));
        }
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void stackMapper_DamagedPickaxe_SplitsVariant(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SimpleContainer container = new SimpleContainer(4);
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(10);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        container.setItem(1, new ItemStack(Items.COBBLESTONE, 7));
        container.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        container.setItem(3, damaged);

        StackMapper mapper = new StackMapper();
        List<Stack> stacks = mapper.map(container);

        helper.assertTrue(
                stacks.size() == 4,
                "снимок обязан повторять слоты контейнера один в один: 4 слота дали " + stacks.size());
        helper.assertTrue(
                stacks.get(0) != null && stacks.get(1) != null && stacks.get(0).sameKindAs(stacks.get(1)),
                "две стопки обычного булыжника обязаны иметь один вариант и сливаться сортёром");
        helper.assertTrue(
                stacks.get(2) != null && stacks.get(3) != null && !stacks.get(2).sameKindAs(stacks.get(3)),
                "целая и повреждённая кирки обязаны получить разные варианты: иначе сортёр сольёт зачарованные "
                        + "и повреждённые предметы с обычными и игрок потеряет вещь");
        helper.assertTrue(
                mapper.sampleFor(stacks.get(0).variant()).is(Items.COBBLESTONE),
                "образец варианта обязан хранить предмет для обратной раскладки в контейнер");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void transferRunner_TenStacks_MovesAtMostFourPerTick(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        SimpleContainer from = new SimpleContainer(36);
        for (int slot = 0; slot < 10; slot++) {
            from.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        TransferRunner runner = TransferRunner.filtered(from, chest, IdFilter.matchAll(), 640);
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<TransferRunner.State> state = new AtomicReference<>(TransferRunner.State.RUNNING);
        helper.onEachTick(() -> {
            if (state.get() == TransferRunner.State.RUNNING) {
                ticks.incrementAndGet();
                state.set(runner.tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    state.get() == TransferRunner.State.DONE,
                    "Бегун обязан перенести 640 булыжника порциями и закончить успехом, состояние " + state.get()
                            + ", причина: " + runner.failureReason());
            helper.assertTrue(
                    runner.moved() == 640,
                    "Бегун обязан перенести все 640 булыжника (10 стопок), перенесено " + runner.moved());
            helper.assertTrue(
                    ticks.get() >= 3,
                    "10 перемещений порциями не укладываются в один тик: тиков должно быть не меньше 3, " + "а прошло "
                            + ticks.get());
            helper.assertTrue(
                    TransferRunner.TRANSFER_MOVES_PER_TICK == 4,
                    "Лимит перемещений за тик равен 4: больше вешало бы серверный тик, меньше тянуло бы "
                            + "перенос; при смене лимита пересмотреть сценарий, фактически "
                            + TransferRunner.TRANSFER_MOVES_PER_TICK);
            helper.assertTrue(
                    runner.maxMovesPerTick() <= TransferRunner.TRANSFER_MOVES_PER_TICK,
                    "За тик бегун делает не больше 4 перемещений: иначе долгий перенос вешал бы серверный "
                            + "тик. Максимум за тик: " + runner.maxMovesPerTick());
            helper.assertTrue(
                    countAll(from) + countAll(chest) == 640,
                    "Предметы не теряются и не дублируются при порционном переносе");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void transferRunner_CancelMidway_ConservesTotal(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        SimpleContainer from = new SimpleContainer(36);
        for (int slot = 0; slot < 6; slot++) {
            from.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        TransferRunner runner = TransferRunner.filtered(from, chest, IdFilter.matchAll(), 384);
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<TransferRunner.State> state = new AtomicReference<>(TransferRunner.State.RUNNING);
        helper.onEachTick(() -> {
            if (state.get() == TransferRunner.State.RUNNING) {
                ticks.incrementAndGet();
                state.set(runner.tick());
                if (ticks.get() == 1) {
                    runner.cancel();
                    state.set(runner.state());
                }
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    state.get() == TransferRunner.State.CANCELLED,
                    "Отмена обязана останавливать бегуна, состояние " + state.get());
            helper.assertTrue(
                    runner.moved() == 256,
                    "За первый тик переносятся ровно 4 стопки (256): перенесено " + runner.moved());
            helper.assertTrue(
                    countAll(from) == 128 && countAll(chest) == 256,
                    "Отмена оставляет состояние целым: каждое перемещение — целая операция, в источнике 128, "
                            + "в сундуке 256, фактически " + countAll(from) + " и " + countAll(chest));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void transferRunner_SourceStolenMidway_FailsWithoutDuplication(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        SimpleContainer from = new SimpleContainer(36);
        for (int slot = 0; slot < 12; slot++) {
            from.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        TransferRunner runner = TransferRunner.filtered(from, chest, IdFilter.matchAll(), 768);
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<TransferRunner.State> state = new AtomicReference<>(TransferRunner.State.RUNNING);
        helper.onEachTick(() -> {
            if (state.get() == TransferRunner.State.RUNNING) {
                ticks.incrementAndGet();
                if (ticks.get() == 2) {
                    from.setItem(4, ItemStack.EMPTY);
                    from.setItem(5, ItemStack.EMPTY);
                }
                if (ticks.get() == 3) {
                    from.setItem(10, ItemStack.EMPTY);
                }
                state.set(runner.tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    state.get() == TransferRunner.State.FAILED,
                    "Второе изменение источника обязано проваливать перенос: первое чинится пересчётом плана, "
                            + "второе — провал. Состояние " + state.get() + ", перенесено " + runner.moved());
            helper.assertTrue(
                    runner.failureReason() != null && runner.failureReason().contains("изменилось"),
                    "Причина обязана говорить, что содержимое контейнера изменилось, фактически: "
                            + runner.failureReason());
            helper.assertTrue(
                    runner.moved() == 512,
                    "До провала переносятся только целые перемещения (4 + 4 стопки = 512): перенесено "
                            + runner.moved());
            helper.assertTrue(
                    countAll(from) + countAll(chest) == 576,
                    "Бегун не теряет и не дублирует предметы при провале: было 768, извне украдено 192, "
                            + "в контейнерах обязано остаться 576, фактически "
                            + (countAll(from) + countAll(chest)));
            GameTestFixtures.finish(helper);
        });
    }

    private static void placeDoubleChest(GameTestHelper helper, int westX, int eastX, int y, int z) {
        BlockPos west = helper.absolutePos(new BlockPos(westX, y, z));
        BlockPos east = helper.absolutePos(new BlockPos(eastX, y, z));
        helper.getLevel()
                .setBlock(
                        west,
                        Blocks.CHEST
                                .defaultBlockState()
                                .setValue(ChestBlock.FACING, Direction.NORTH)
                                .setValue(ChestBlock.TYPE, ChestType.LEFT),
                        3);
        helper.getLevel()
                .setBlock(
                        east,
                        Blocks.CHEST
                                .defaultBlockState()
                                .setValue(ChestBlock.FACING, Direction.NORTH)
                                .setValue(ChestBlock.TYPE, ChestType.RIGHT),
                        3);
    }

    private static Container accessChest(GameTestHelper helper) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 4)));
        if (!(open instanceof ContainerAccess.Opened opened)) {
            throw new IllegalStateException("Тестовый сундук не открылся: " + describe(open));
        }
        return opened.container();
    }

    private static int countAll(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += container.getItem(slot).getCount();
        }
        return total;
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_TenCobblestone_MovesAllAndSorts(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
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
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit 10 булыжника обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() <= 120,
                    "сценарий обязан укладываться в 120 тиков духа, заняло " + run.spiritTicks());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "у духа не должно остаться булыжника: все 10 уехали в сундук");
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 10,
                    "в сундуке обязано оказаться 10 булыжника, фактически " + chestCount(helper, Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_MessyChest_MergesPartialStacks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        chest.setItem(7, new ItemStack(Items.COBBLESTONE, 7));
        chest.setItem(20, new ItemStack(Items.COBBLESTONE, 3));
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
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit в сундук с разбросанными стопками обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 25,
                    "сумма булыжника обязана сохраниться: было 5+7+3, дух положил 10, стало "
                            + chestCount(helper, Items.COBBLESTONE) + " вместо 25");
            helper.assertTrue(
                    itemStacks(helper, Items.COBBLESTONE) == 1,
                    "разрозненные неполные стопки обязаны слиться в одну: стопок булыжника "
                            + itemStacks(helper, Items.COBBLESTONE) + " вместо 1");
            helper.assertTrue(
                    chest.getItem(0).is(Items.COBBLESTONE) && chest.getItem(0).getCount() == 25,
                    "собранная стопка обязана лежать первой и содержать все 25, слот 0 — " + chest.getItem(0));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void withdraw_FiveIngotsFromMessyChest_TakesAndSorts(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        chest.setItem(0, new ItemStack(Items.DIRT, 64));
        chest.setItem(2, new ItemStack(Items.IRON_INGOT, 4));
        chest.setItem(5, new ItemStack(Items.IRON_INGOT, 3));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "withdraw",
                        Map.of("container", "nearest", "item", "minecraft:iron_ingot", "count", 5, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "withdraw 5 слитков обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT) == 5,
                    "у духа обязано оказаться 5 слитков железа");
            helper.assertTrue(
                    chestCount(helper, Items.IRON_INGOT) == 2,
                    "в сундуке обязаны остаться 2 слитка (было 7, забрали 5), фактически "
                            + chestCount(helper, Items.IRON_INGOT));
            helper.assertTrue(
                    chest.getItem(0).is(Items.IRON_INGOT) && chest.getItem(0).getCount() == 2,
                    "остаток слитков обязан быть слит в одну стопку и стоять первым (материалы раньше блоков), "
                            + "иначе сундук не отсортирован: слот 0 — " + chest.getItem(0));
            helper.assertTrue(
                    chest.getItem(1).is(Items.DIRT) && chest.getItem(2).isEmpty(),
                    "земля обязана стоять второй без дырок между стопками: слот 1 — " + chest.getItem(1) + ", слот 2 — "
                            + chest.getItem(2));
            GameTestFixtures.finish(helper);
        });
    }

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
    public static void sortChest_MessyChest_MergesAndKeepsVariants(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        for (int slot = 0; slot < 20; slot++) {
            chest.setItem(slot, new ItemStack(Items.COBBLESTONE, slot + 1));
        }
        chest.setItem(20, new ItemStack(Items.IRON_PICKAXE));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(5);
        chest.setItem(21, worn);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        Map<String, Object> params = Map.of("container", "nearest", "radius", 8);
        SpiritRun first = SpiritRun.launch(helper, spirit, new ScriptCall("sort_chest", params), 1);
        AtomicBoolean relaunched = new AtomicBoolean();
        AtomicReference<SpiritRun> second = new AtomicReference<>();
        AtomicReference<List<String>> afterFirst = new AtomicReference<>();

        helper.succeedWhen(() -> {
            first.assertStarted();
            if (!relaunched.get()) {
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                        "первая sort_chest обязана завершиться успехом, причина: "
                                + spirit.taskFailureReason().orElse("нет"));
                afterFirst.set(snapshotChest(helper));
                second.set(SpiritRun.launch(helper, spirit, new ScriptCall("sort_chest", params), spirit.tickCount));
                relaunched.set(true);
            }
            helper.assertTrue(
                    second.get() != null,
                    "повторная сортировка обязана быть запущена после первой: иначе идемпотентность не проверена");
            second.get().assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "повторный sort_chest обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    snapshotChest(helper).equals(afterFirst.get()),
                    "повторная сортировка обязана ничего не менять (идемпотентность): иначе каждый вызов "
                            + "тасовал бы сундук заново");
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 210,
                    "сумма булыжника обязана сохраниться (1+…+20 = 210), фактически "
                            + chestCount(helper, Items.COBBLESTONE));
            helper.assertTrue(
                    pickaxeStacks(helper) == 2,
                    "целая и повреждённая кирки обязаны остаться разными стопками: слияние разных вариантов "
                            + "потеряло бы вещь игрока");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_DoubleChest_Uses54Slots(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        placeDoubleChest(helper, 5, 6, 1, 4);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 36));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 100, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit в двойной сундук обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 100,
                    "в двойном сундуке обязано оказаться 100 булыжника, фактически "
                            + chestCount(helper, Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unload_FullInventory_MovesOnlySurplus(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        spirit.getInventory().setItem(1, new ItemStack(Items.BOW));
        spirit.getInventory().setItem(2, new ItemStack(Items.ARROW, 16));
        for (int slot = 0; slot < 5; slot++) {
            spirit.getInventory().setItem(3 + slot, new ItemStack(Items.BREAD, 4));
        }
        for (int slot = 0; slot < 16; slot++) {
            spirit.getInventory().setItem(8 + slot, new ItemStack(Items.TORCH, 4));
        }
        for (int slot = 0; slot < 3; slot++) {
            spirit.getInventory().setItem(24 + slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        spirit.getInventory().setItem(27, new ItemStack(Items.DIAMOND, 5));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("unload", Map.of("container", "nearest", "radius", 8, "force", false)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() <= 120,
                    "сценарий обязан укладываться в 120 тиков духа, заняло " + run.spiritTicks());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_SWORD) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BOW) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.ARROW) == 16,
                    "постоянная часть остаётся целиком: меч, лук и 16 стрел");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD) == 16
                            && chestCount(helper, Items.BREAD) == 4,
                    "из 20 хлеба 16 остаются, 4 лишних уходят: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD)
                            + ", в сундуке " + chestCount(helper, Items.BREAD));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 24
                            && chestCount(helper, Items.TORCH) == 40,
                    "из 64 факелов 24 остаются, 40 уходят: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH)
                            + ", в сундуке " + chestCount(helper, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0
                            && chestCount(helper, Items.COBBLESTONE) == 192,
                    "булыжник вне постоянной части уходит целиком: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE)
                            + ", в сундуке " + chestCount(helper, Items.COBBLESTONE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND) == 0
                            && chestCount(helper, Items.DIAMOND) == 5,
                    "алмазы не входят в постоянную часть и уходят целиком");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unload_BelowTrigger_SucceedsWithoutTouching(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        spirit.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 5));
        spirit.getInventory().setItem(2, new ItemStack(Items.BREAD, 20));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("unload", Map.of("container", "nearest", "radius", 8, "force", false)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "при занятых слотах меньше 24 разгрузка не нужна и завершается успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 0
                            && chestCount(helper, Items.DIAMOND) == 0
                            && chestCount(helper, Items.BREAD) == 0,
                    "без триггера сундук обязан остаться пустым: разгрузка ничего не делает");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD) == 20,
                    "инвентарь духа обязан остаться нетронутым");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_IronChestplate_SwapsAndPinsDropChance(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_CHESTPLATE));
        Map<String, Object> params = Map.of("item", "minecraft:iron_chestplate");
        SpiritRun first = SpiritRun.launch(helper, spirit, new ScriptCall("equip", params), 1);
        AtomicBoolean relaunched = new AtomicBoolean();
        AtomicReference<SpiritRun> second = new AtomicReference<>();
        AtomicReference<List<String>> afterFirst = new AtomicReference<>();

        helper.succeedWhen(() -> {
            first.assertStarted();
            if (!relaunched.get()) {
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                        "equip железного нагрудника обязан завершиться успехом, причина: "
                                + spirit.taskFailureReason().orElse("нет"));
                helper.assertTrue(first.spiritTicks() <= 120, "сценарий обязан укладываться в 120 тиков духа");
                afterFirst.set(snapshotInventory(spirit));
                second.set(SpiritRun.launch(helper, spirit, new ScriptCall("equip", params), spirit.tickCount));
                relaunched.set(true);
            }
            helper.assertTrue(
                    second.get() != null && spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "повторный equip обязан завершиться успехом без изменений, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            second.get().assertStarted();
            helper.assertTrue(
                    spirit.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "железный нагрудник обязан остаться надетым");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.LEATHER_CHESTPLATE) == 1,
                    "прежний кожаный нагрудник обязан вернуться в инвентарь, а не исчезнуть");
            helper.assertTrue(
                    snapshotInventory(spirit).equals(afterFirst.get()),
                    "повторный вызов обязан ничего не менять (идемпотентность)");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_PickaxePattern_TakesMostValuable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.STONE_PICKAXE));
        spirit.getInventory().setItem(2, new ItemStack(Items.IRON_PICKAXE));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("equip", Map.of("item", "minecraft:*_pickaxe")), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "equip по шаблону обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.IRON_PICKAXE),
                    "по шаблону *_pickaxe берётся самая ценная (железная), а не первая попавшаяся: в руке "
                            + spirit.getMainHandItem());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 0,
                    "остальные кирки обязаны остаться в инвентаре");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_WornWoodenPickaxeWithIronInInventory_SwapsToIron(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("equip", Map.of("item", "minecraft:*_pickaxe")), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "equip по шаблону обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.IRON_PICKAXE),
                    "в руке деревянная кирка, а в инвентаре железная подходит под тот же шаблон: берётся более "
                            + "ценная, иначе дух вечно работал бы худшим инструментом. В руке "
                            + spirit.getMainHandItem());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE) == 1,
                    "прежняя деревянная кирка обязана вернуться в инвентарь");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_MissingItem_FailsWithoutChanges(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("equip", Map.of("item", "minecraft:diamond_sword")), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "отсутствующий предмет обязан проваливать equip, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("нет предмета"),
                    "причина обязана говорить, что предмета нет в инвентаре, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(
                    spirit.getMainHandItem().isEmpty(),
                    "при провале рука обязана остаться пустой: ничего не перемещено");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void equip_CraftEquipGatherSwap_KeepsEveryTool(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        helper.setBlock(FullCycleScene.TABLE, Blocks.CRAFTING_TABLE);
        FullCycleScene.placeCobblestoneRow(helper, 3);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 3));
        spirit.getInventory().addItem(new ItemStack(Items.STICK, 2));
        spirit.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        EquipRoundtrip roundtrip = new EquipRoundtrip(helper, spirit);
        helper.succeedWhen(() -> {
            roundtrip.step();
        });
    }

    private static final class EquipRoundtrip {
        private final GameTestHelper helper;
        private final SpiritEntity spirit;
        private SpiritRun current;
        private int phase;

        private EquipRoundtrip(GameTestHelper helper, SpiritEntity spirit) {
            this.helper = helper;
            this.spirit = spirit;
            current = launch("craft", Map.of("item", "stone_pickaxe", "count", 1, "radius", 8));
        }

        private SpiritRun launch(String script, Map<String, Object> params) {
            return SpiritRun.launch(helper, spirit, new ScriptCall(script, params), Math.max(1, spirit.tickCount));
        }

        private void step() {
            current.assertStarted();
            RunStatus status = spirit.taskStatus().orElse(null);
            helper.assertTrue(status != RunStatus.RUNNING, "шаг цепочки ещё выполняется: " + status);
            helper.assertTrue(
                    status == RunStatus.SUCCEEDED,
                    "шаг " + phase + " цепочки craft → equip → gather → equip другого обязан завершиться успехом, "
                            + "статус: " + status + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            switch (phase) {
                case 0 -> afterCraft();
                case 1 -> afterEquip();
                case 2 -> afterRelease();
                case 3 -> afterGather();
                case 4 -> afterSwap();
                default -> helper.assertTrue(false, "лишний шаг " + phase + ": цепочка уже закончилась");
            }
        }

        private void afterCraft() {
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "craft обязан положить каменную кирку в инвентарь, фактически: "
                            + countStored(spirit, Items.STONE_PICKAXE));
            current = launch("equip", Map.of("item", "minecraft:stone_pickaxe"));
            phase = 1;
            helper.assertTrue(false, "ждём equip: кирка в инвентаре, рука пока пуста");
        }

        private void afterEquip() {
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_PICKAXE),
                    "equip обязан надеть кирку в настоящую руку, в руке: " + spirit.getMainHandItem());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE) == 0,
                    "кирка после equip живёт в руке, а не в инвентаре: "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE));
            spirit.gear().holdNothing();
            phase = 2;
            helper.assertTrue(false, "ждём проверки кирки после освобождения руки");
        }

        private void afterRelease() {
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_PICKAXE),
                    "INC-12: тактика освобождает только свою копию, а настоящая кирка после equip обязана "
                            + "остаться в руке, в руке: " + spirit.getMainHandItem());
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "INC-12: кирка, вынутая из инвентаря для equip, не имеет права исчезнуть; в руке "
                            + spirit.getMainHandItem() + ", всего " + countStored(spirit, Items.STONE_PICKAXE));
            current = launch("gather", Map.of("block", "cobblestone", "count", 1, "radius", 8));
            phase = 3;
            helper.assertTrue(false, "ждём gather: кирка снова у духа");
        }

        private void afterGather() {
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "gather не вправе потерять кирку: в руке " + spirit.getMainHandItem() + ", всего "
                            + countStored(spirit, Items.STONE_PICKAXE));
            current = launch("equip", Map.of("item", "minecraft:stone_axe"));
            phase = 4;
            helper.assertTrue(false, "ждём equip топора: кирка должна остаться у духа");
        }

        private void afterSwap() {
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_AXE),
                    "equip другого инструмента обязан надеть топор, в руке: " + spirit.getMainHandItem());
            helper.assertTrue(
                    countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "equip другого инструмента обязан вернуть прежнюю кирку в инвентарь, всего "
                            + countStored(spirit, Items.STONE_PICKAXE));
            spirit.gear().holdNothing();
            helper.assertTrue(
                    countStored(spirit, Items.STONE_AXE) == 1 && countStored(spirit, Items.STONE_PICKAXE) == 1,
                    "после освобождения руки оба инструмента обязаны остаться у духа: топор "
                            + countStored(spirit, Items.STONE_AXE) + ", кирка "
                            + countStored(spirit, Items.STONE_PICKAXE));
            TacticsScene.finish(helper, spirit);
        }
    }

    private static int countStored(SpiritEntity spirit, Item item) {
        ItemStack hand = spirit.getMainHandItem();
        return GameTestFixtures.countInInventory(spirit.getInventory(), item) + (hand.is(item) ? hand.getCount() : 0);
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void blockBreaker_MainHandPickaxe_BreaksFasterAndWears(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        BlockPos stone = helper.absolutePos(new BlockPos(6, 1, 4));
        BlockBreaker breaker = new BlockBreaker(spirit);
        helper.assertTrue(
                breaker.begin(stone) instanceof BlockBreaker.Started, "камень в досягаемости обязан начинать ломаться");
        int bareTicks = breaker.requiredTicks();
        spirit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(
                breaker.begin(stone) instanceof BlockBreaker.Started,
                "камень с киркой в руке обязан начинать ломаться");
        int fastTicks = breaker.requiredTicks();
        helper.assertTrue(
                breaker.choice().slot() == ToolChoice.MAIN_HAND,
                "ломка обязана выбрать кирку из основной руки, а не руку или инвентарь: слот "
                        + breaker.choice().slot());
        helper.assertTrue(
                fastTicks < bareTicks,
                "ломка камня железной киркой из руки обязана быть быстрее, чем рукой: рукой " + bareTicks
                        + " тиков, киркой " + fastTicks);
        AtomicReference<BlockBreaker.Progress> progress = new AtomicReference<>(BlockBreaker.Progress.BREAKING);
        helper.onEachTick(() -> {
            if (progress.get() == BlockBreaker.Progress.BREAKING) {
                progress.set(breaker.tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    progress.get() == BlockBreaker.Progress.BROKEN,
                    "кирка из руки обязана доломать камень до конца, состояние " + progress.get());
            helper.assertTrue(
                    spirit.getMainHandItem().getDamageValue() == 1,
                    "износ обязан списываться с кирки в руке: урон должен быть 1, фактически "
                            + spirit.getMainHandItem().getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unloadAuto_PartialTorchesInFarChest_TopsItUpAndLeavesNear(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 7, Blocks.CHEST);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        Container far = chestAt(helper, 2, 1, 7);
        Container near = chestAt(helper, 5, 1, 4);
        far.setItem(0, new ItemStack(Items.TORCH, 10));
        near.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.TORCH, 52));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("unload", Map.of("container", "auto", "radius", 8, "force", true)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload auto обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(far, Items.TORCH) == 38,
                    "в дальнем сундуке неполная стопка факелов (10) обязана дополниться 28 излишка до 38, "
                            + "фактически " + GameTestFixtures.countInInventory(far, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.TORCH) == 0,
                    "ближний сундук не тронут: факелов в нём быть не должно, фактически "
                            + GameTestFixtures.countInInventory(near, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.OAK_PLANKS) == 64,
                    "доски в ближнем сундуке обязаны остаться на месте");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 24,
                    "у духа обязаны остаться 24 факела постоянной части, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unloadAuto_FarChestFull_TorchesGoToNearestFree(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 7, Blocks.CHEST);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        Container far = chestAt(helper, 2, 1, 7);
        Container near = chestAt(helper, 5, 1, 4);
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
            ItemStack filler = new ItemStack(fillers[slot]);
            far.setItem(slot, filler.copyWithCount(filler.getMaxStackSize()));
        }
        near.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.TORCH, 52));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("unload", Map.of("container", "auto", "radius", 8, "force", true)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload auto при полном дальнем сундуке обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.TORCH) == 28,
                    "дальний сундук полон: 28 излишка факелов обязаны лечь в ближний свободный, фактически "
                            + GameTestFixtures.countInInventory(near, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(far, Items.TORCH) == 64,
                    "в полный дальний сундук добавился только заполнитель: факелов в нём быть не должно, "
                            + "фактически " + GameTestFixtures.countInInventory(far, Items.TORCH));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void depositAuto_PartialCobbleInFarChest_TopsItUp(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 7, Blocks.CHEST);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        Container far = chestAt(helper, 2, 1, 7);
        Container near = chestAt(helper, 5, 1, 4);
        far.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        near.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "auto", "item", "minecraft:cobblestone", "count", 10, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit auto обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(far, Items.COBBLESTONE) == 20,
                    "deposit auto обязан дополнить неполную стопку в дальнем сундуке до 20, фактически "
                            + GameTestFixtures.countInInventory(far, Items.COBBLESTONE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.COBBLESTONE) == 0,
                    "ближний сундук не тронут: булыжник идёт к неполной стопке в дальнем");
            GameTestFixtures.finish(helper);
        });
    }

    private static Container chestAt(GameTestHelper helper, int x, int y, int z) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
        if (open instanceof ContainerAccess.Opened opened) {
            return opened.container();
        }
        throw new IllegalStateException(
                "Тестовый сундук (" + x + "," + y + "," + z + ") не открылся: " + describe(open));
    }

    private static List<String> snapshotInventory(SpiritEntity spirit) {
        List<String> slots = new ArrayList<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            slots.add(stack.isEmpty() ? "empty" : StackMapper.itemId(stack) + "x" + stack.getCount());
        }
        return slots;
    }

    private static int chestCount(GameTestHelper helper, Item item) {
        int total = 0;
        Container chest = accessChest(helper);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) {
                total += chest.getItem(slot).getCount();
            }
        }
        return total;
    }

    private static List<String> snapshotChest(GameTestHelper helper) {
        List<String> slots = new ArrayList<>();
        Container chest = accessChest(helper);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) {
                slots.add("empty");
            } else {
                slots.add(StackMapper.itemId(stack) + "x" + stack.getCount() + "d" + stack.getDamageValue());
            }
        }
        return slots;
    }

    private static int pickaxeStacks(GameTestHelper helper) {
        return itemStacks(helper, Items.IRON_PICKAXE);
    }

    private static int itemStacks(GameTestHelper helper, Item item) {
        Container chest = accessChest(helper);
        int stacks = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) {
                stacks++;
            }
        }
        return stacks;
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
    public static void registryGuard_ExactIdsInTables_ExistInGameRegistry(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        List<String> matches = exactMatches();
        List<String> missing = new ArrayList<>();
        for (String match : matches) {
            String id = match.contains(":") ? match : "minecraft:" + match;
            if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id))) {
                missing.add(id);
            }
        }

        helper.assertTrue(
                !matches.isEmpty(), "сторож бессмыслен без точных id: в таблицах обязаны быть не только шаблоны");
        helper.assertTrue(
                missing.isEmpty(),
                "все точные id из item_values.json и base_kit.json обязаны существовать в реестре предметов: "
                        + "иначе ценность и набор ссылаются на несуществующие предметы. Отсутствуют: " + missing);
        GameTestFixtures.finish(helper);
        helper.succeed();
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

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_ReloadedMidway_CompletesCountNotMore(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        for (int slot = 0; slot < 20; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 1));
        }
        Map<String, Object> params =
                Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 10, "radius", 8);
        AtomicReference<SpiritEntity> current = new AtomicReference<>(spirit);
        AtomicBoolean launched = new AtomicBoolean();
        AtomicBoolean reloaded = new AtomicBoolean();
        helper.onEachTick(() -> {
            SpiritEntity live = current.get();
            if (!launched.get() && live.tickCount >= 1) {
                launched.set(true);
                live.startTask(new ScriptCall("deposit", params));
            } else if (launched.get() && !reloaded.get() && chestCount(helper, Items.COBBLESTONE) >= 4) {
                reloaded.set(true);
                CompoundTag tag = new CompoundTag();
                live.saveWithoutId(tag);
                live.discard();
                SpiritEntity next = ModEntities.SPIRIT.get().create(helper.getLevel());
                next.load(tag);
                helper.getLevel().addFreshEntity(next);
                current.set(next);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    reloaded.get(),
                    "дух обязан быть перезагружен посередине переноса: иначе возобновление не проверено");
            SpiritEntity live = current.get();
            helper.assertTrue(
                    live.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный deposit обязан завершиться успехом, причина: "
                            + live.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 10,
                    "итого в сундуке обязано оказаться 10, а не 14: после восстановления переносят "
                            + "count − moved, фактически " + chestCount(helper, Items.COBBLESTONE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(live.getInventory(), Items.COBBLESTONE) == 10,
                    "у духа обязано остаться 10 булыжника из 20: перенесены только недостающие 6, фактически "
                            + GameTestFixtures.countInInventory(live.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    private static String coordsOf(BlockPos absolute) {
        return absolute.getX() + "," + absolute.getY() + "," + absolute.getZ();
    }

    private static List<String> exactMatches() {
        List<String> exact = new ArrayList<>();
        InventoryTables.values()
                .rules()
                .forEach(rule -> rule.match().stream()
                        .filter(match -> !IdFilter.isPattern(match))
                        .forEach(exact::add));
        InventoryTables.kit()
                .rules()
                .forEach(rule -> rule.match().stream()
                        .filter(match -> !IdFilter.isPattern(match))
                        .forEach(exact::add));
        InventoryTables.kit().neverGiveAway().stream()
                .filter(match -> !IdFilter.isPattern(match))
                .forEach(exact::add);
        return exact;
    }

    private static String describe(ContainerAccess.Open open) {
        return switch (open) {
            case ContainerAccess.Opened opened ->
                "открыт контейнер на " + opened.container().getContainerSize() + " слотов";
            case ContainerAccess.Refused refused -> "отказ: " + refused.reason();
        };
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void swapTool_StoneBlock_HoldsPickaxe(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("swap_tool", Map.of("block", "stone")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "swap_tool обязан сменить инструмент и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_PICKAXE),
                    "для камня в руке обязана оказаться каменная кирка, фактически: " + spirit.getMainHandItem());
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void drinkPotion_HealingPotion_HealsSpirit(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.setHealth(1.0F);
        spirit.getInventory().setItem(0, PotionContents.createItemStack(Items.POTION, Potions.HEALING));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("drink_potion", Map.of("type", "healing")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "drink_potion обязан выпить зелье и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getHealth() >= 5.0F,
                    "зелье лечения обязано вылечить на 4 единицы с 1 до 5 и выше, фактически: " + spirit.getHealth());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.POTION) == 0,
                    "выпитое зелье обязано исчезнуть из инвентаря, осталось: "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.POTION));
            TacticsScene.finish(helper, spirit);
        });
    }
}

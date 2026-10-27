package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.accessChest;
import static com.theminespirit.gametest.InventorySceneSupport.countAll;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.inventory.StackMapper;
import com.theminespirit.neoforge.inventory.TransferRunner;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventoryTransferGameTests {
    private InventoryTransferGameTests() {}

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
}

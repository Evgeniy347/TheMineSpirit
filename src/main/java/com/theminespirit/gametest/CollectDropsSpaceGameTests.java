package com.theminespirit.gametest;

import static com.theminespirit.gametest.CollectDropsSupport.ENDLESS_PICKUP_DELAY;
import static com.theminespirit.gametest.CollectDropsSupport.PILLAR_HEIGHT;
import static com.theminespirit.gametest.CollectDropsSupport.PILLAR_SETTLE_TICKS;
import static com.theminespirit.gametest.CollectDropsSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.CollectDropsSupport.SPIRIT_START;
import static com.theminespirit.gametest.CollectDropsSupport.totalItems;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CollectDropsSpaceGameTests {
    private CollectDropsSpaceGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_InventoryFullOfOtherStacks_FailsWithoutLosingItems(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        ItemEntity heap = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 2.5, 1, 2.5);
        int totalBefore = totalItems(helper, spirit);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6, "overflow", "fail")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "полный инвентарь обязан провалить collect_drops, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("инвентарь полон") && reason.contains("cobblestone"),
                    "причина обязана называть переполнение и предмет, фактически: " + reason);
            helper.assertTrue(
                    !heap.isRemoved() && heap.getItem().getCount() == 5,
                    "кучка, которая не поместилась, обязана остаться в мире целой: предметы не выбрасываются и не пропадают");
            helper.assertTrue(
                    totalItems(helper, spirit) == totalBefore,
                    "суммарное число предметов (инвентарь + мир) обязано остаться прежним: было " + totalBefore
                            + ", стало " + totalItems(helper, spirit));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_HeapOnTallPillar_PicksRestAndFailsUnreachable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int y = 1; y <= PILLAR_HEIGHT; y++) {
            helper.setBlock(7, y, 4, Blocks.STONE);
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 2.5, 1, 4.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 4.5, 1, 6.5);
        ItemEntity onPillar = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 4, 7.5, PILLAR_HEIGHT + 1, 4.5);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), PILLAR_SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "недостижимая кучка обязана провалить collect_drops, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("не дотянулся"),
                    "причина обязана говорить о недостижимых предметах, фактически: " + reason);
            int took = run.spiritTicks();
            helper.assertTrue(
                    took <= 120,
                    "недостижимая кучка обязана определяться детектором застревания за 120 тиков, а не тайм-аутом "
                            + "маршрута: заняло " + took);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 5,
                    "достижимые кучки (2+3) обязаны быть подобраны до провала, в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(
                    !onPillar.isRemoved() && onPillar.getItem().getCount() == 4,
                    "кучка на столбе обязана остаться целой");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_ItemAtBottomOfFourDeepPit_PulledByMagnetWithoutDescending(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int y = 0; y >= -3; y--) {
            helper.setBlock(4, y, 2, Blocks.AIR);
        }
        helper.setBlock(4, -4, 2, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        ItemEntity drop = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 4.5, -3, 2.5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);
        double startY = spirit.getY();

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "предмет на дне ямы в радиусе магнита (5) притягивается без спуска (упрощение 44.1): collect_drops "
                            + "обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    Math.abs(spirit.getY() - startY) < 0.1,
                    "дух не должен спускаться в яму за предметом: y был " + startY + ", стал " + spirit.getY());
            helper.assertTrue(
                    drop.isRemoved()
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 1,
                    "предмет со дна ямы обязан оказаться в инвентаре, а не на дне");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_PreyInWebSealedNearestStand_ReachesItFromAnotherStand(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(5, 1, 5, Blocks.COBWEB);
        helper.setBlock(2, 1, 5, Blocks.STONE);
        helper.setBlock(2, 2, 5, Blocks.STONE);
        helper.setBlock(3, 1, 4, Blocks.STONE);
        helper.setBlock(3, 2, 4, Blocks.STONE);
        helper.setBlock(3, 1, 6, Blocks.STONE);
        helper.setBlock(3, 2, 6, Blocks.STONE);
        helper.setBlock(4, 1, 5, Blocks.STONE);
        helper.setBlock(4, 2, 5, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 5));
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 5.5, 1.5, 5.5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "предмет в углу за запечатанной стоянкой обязан подобраться с другой стоянки, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 1,
                    "булыжник из паутины обязан попасть в инвентарь, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_HeapWithEndlessPickupDelay_IsSkippedAfterBoundedWait(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        ItemEntity display = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 3.5, 1, 4.5);
        display.setPickUpDelay(ENDLESS_PICKUP_DELAY);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 7.5, 1, 4.5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "кучка с бесконечной задержкой обязана провалить итог collect_drops, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("не дотянулся до 1"),
                    "причина обязана называть одну недоступную кучку, фактически: " + reason);
            helper.assertTrue(
                    run.spiritTicks() <= 120,
                    "ожидание задержки ограничено 40 тиками: итог за 120 тиков духа, а заняло " + run.spiritTicks());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "обычная кучка обязана быть подобрана, пока дух не ждёт вечную: в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(
                    !display.isRemoved() && display.getItem().getCount() == 1,
                    "кучка с бесконечной задержкой не должна быть тронута");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_HeapAddressedToAnotherPlayer_IsLeftUntouched(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        ItemEntity foreign = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 4, 3.5, 1, 4.5);
        foreign.setTarget(UUID.randomUUID());
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 7.5, 1, 4.5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "чужая кучка не входит в подбор: collect_drops обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "в инвентаре обязана быть только обычная кучка (2): вещь, адресованную игроку, дух не забирает");
            helper.assertTrue(
                    !foreign.isRemoved() && foreign.getItem().getCount() == 4,
                    "кучка, адресованная другому игроку, обязана остаться нетронутой");
            GameTestFixtures.finish(helper);
        });
    }
}

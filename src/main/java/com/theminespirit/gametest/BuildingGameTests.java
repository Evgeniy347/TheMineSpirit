package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.SpiritBuildSupplies;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingGameTests {
    private static final int SETTLE_TICKS = 5;

    private BuildingGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, int cobble) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        if (cobble > 0) {
            spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, cobble));
        }
        return spirit;
    }

    @GameTest(template = "arena", batch = "build_supplies", timeoutTicks = 100)
    public static void supplies_TwoChestsWithCobble_NearestFirstSkipsEmpty(GameTestHelper helper) {
        prepare(helper);
        BlockPos near = new BlockPos(2, 1, 2);
        BlockPos far = new BlockPos(6, 1, 6);
        BlockPos empty = new BlockPos(2, 1, 6);
        BlockPos beyond = new BlockPos(8, 1, 8);
        helper.setBlock(near, Blocks.CHEST);
        helper.setBlock(far, Blocks.CHEST);
        helper.setBlock(empty, Blocks.CHEST);
        helper.setBlock(beyond, Blocks.CHEST);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.FURNACE);
        chest(helper, near).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        chest(helper, far).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        chest(helper, beyond).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        ((Container) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(4, 1, 1))))
                .setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 2);
        SpiritBuildSupplies supplies = new SpiritBuildSupplies(spirit);

        helper.assertTrue(supplies.inInventory("cobblestone") == 2,
                "счёт обязан идти из инвентаря духа: 2 булыжника, посчитано " + supplies.inInventory("cobblestone"));
        helper.assertTrue(supplies.inInventory("nope_block") == 0,
                "неизвестный блок обязан считаться нулём, а не падать");
        var steps = supplies.withdraw("cobblestone", 8);
        helper.assertTrue(steps.size() == 2,
                "шагов обязано быть 2: ближний сундук (5) и дальний (3), пустой и печь пропускаются, дальний за радиусом исключён, шагов " + steps.size());
        BlockPos nearAbs = helper.absolutePos(near);
        BlockPos farAbs = helper.absolutePos(far);
        helper.assertTrue(steps.get(0).params().get("container").equals(nearAbs.getX() + "," + nearAbs.getY() + "," + nearAbs.getZ())
                && steps.get(0).params().get("count").equals(5),
                "первым обязан идти ближний сундук с пятёркой: ближний первым, фактически " + steps.get(0).params());
        helper.assertTrue(steps.get(1).params().get("container").equals(farAbs.getX() + "," + farAbs.getY() + "," + farAbs.getZ())
                && steps.get(1).params().get("count").equals(3),
                "вторым обязан идти дальний с остатком 3, фактически " + steps.get(1).params());
        helper.assertTrue(supplies.withdraw("cobblestone", 0).isEmpty(),
                "запрос на ноль обязан давать пустые шаги");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static ScriptCall placeCall(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new ScriptCall("place_block",
                Map.of("block", "cobblestone", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ()));
    }

    @GameTest(template = "arena", batch = "build_place", timeoutTicks = 400)
    public static void placeBlock_EmptyCellWithBlock_SucceedsAndConsumes(GameTestHelper helper) {
        prepare(helper);
        BlockPos target = new BlockPos(3, 1, 3);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 1);
        SpiritRun run = SpiritRun.launch(helper, spirit, placeCall(helper, target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "пустая клетка с блоком в инвентаре обязана застроиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(target).is(Blocks.COBBLESTONE),
                    "в целевой клетке обязан стоять булыжник");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "предмет обязан израсходоваться: стройка бесплатно не строит, в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(PlacedBlocksData.get(helper.getLevel()).isSpiritMarked(helper.absolutePos(target)),
                    "поставленный духом блок обязан помечаться меткой духа: иначе дух не разберёт свою постройку");
            helper.assertTrue(run.spiritTicks() <= 300,
                    "постановка одного блока обязана уложиться в 300 тиков духа, заняло " + run.spiritTicks());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_place", timeoutTicks = 200)
    public static void placeBlock_OccupiedCell_FailsNamedOccupied(GameTestHelper helper) {
        prepare(helper);
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 1);
        SpiritRun run = SpiritRun.launch(helper, spirit, placeCall(helper, target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "занятая клетка обязана дать отказ, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("занята"),
                    "причина обязана называть, что место занято, фактически: " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(target).is(Blocks.STONE),
                    "занятая клетка обязана остаться камнем");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 1,
                    "материал при отказе обязан остаться в инвентаре");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_place", timeoutTicks = 400)
    public static void placeBlock_FarCell_SpiritApproachesAndPlaces(GameTestHelper helper) {
        prepare(helper);
        BlockPos target = new BlockPos(7, 1, 7);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 1);
        SpiritRun run = SpiritRun.launch(helper, spirit, placeCall(helper, target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "далёкая клетка обязана застроиться после подхода, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(target).is(Blocks.COBBLESTONE),
                    "в дальней клетке обязан стоять булыжник: дух подошёл и поставил");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_place", timeoutTicks = 200)
    public static void placeBlock_WithoutMaterial_FailsWithNumbers(GameTestHelper helper) {
        prepare(helper);
        BlockPos target = new BlockPos(2, 1, 2);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 0);
        SpiritRun run = SpiritRun.launch(helper, spirit, placeCall(helper, target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "без материала задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("нужно 1, есть 0"),
                    "причина обязана называть числа «нужно 1, есть 0», фактически: " + reason);
            helper.assertTrue(helper.getBlockState(target).isAir(),
                    "без материала клетка обязана остаться пустой");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_place", timeoutTicks = 100)
    public static void placer_RefusalsInOrder_OccupiedBeatsNoMaterial(GameTestHelper helper) {
        prepare(helper);
        BlockPos occupied = new BlockPos(2, 1, 1);
        helper.setBlock(occupied, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 0);
        BlockPlacer placer = new BlockPlacer(spirit);
        BlockState wanted = BlockStates.stateFor("cobblestone");

        BlockPlacer.Begin busy = placer.begin(helper.absolutePos(occupied), wanted, "cobblestone");
        helper.assertTrue(busy instanceof BlockPlacer.Refused refused
                && refused.reason() == BlockPlacer.RefuseReason.OCCUPIED,
                "занятая клетка без материала обязана отказать занятостью, а не нехваткой: проверка места идёт раньше материала, ответ " + busy);

        BlockPos far = new BlockPos(8, 1, 8);
        BlockPlacer.Begin distant = placer.begin(helper.absolutePos(far), wanted, "cobblestone");
        helper.assertTrue(distant instanceof BlockPlacer.Refused refused
                && refused.reason() == BlockPlacer.RefuseReason.OUT_OF_REACH,
                "далёкая клетка без материала обязана отказать досягаемостью: подход идёт раньше списания, ответ " + distant);

        BlockPos free = new BlockPos(2, 1, 2);
        BlockPlacer.Begin poor = placer.begin(helper.absolutePos(free), wanted, "cobblestone");
        helper.assertTrue(poor instanceof BlockPlacer.Refused refused
                && refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL,
                "пустая клетка рядом без материала обязана отказать нехваткой с числами, ответ " + poor);
        helper.assertTrue(helper.getBlockState(free).isAir(),
                "отказ не трогает мир: клетка обязана остаться пустой");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos relative) {
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }
}

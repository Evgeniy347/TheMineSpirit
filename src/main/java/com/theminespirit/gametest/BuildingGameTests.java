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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private BuildingGameTests() {}

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

    private static ScriptCall fillCall(GameTestHelper helper, BlockPos first, BlockPos second) {
        BlockPos from = helper.absolutePos(first);
        BlockPos to = helper.absolutePos(second);
        return new ScriptCall(
                "fill",
                Map.of(
                        "block",
                        "cobblestone",
                        "x1",
                        from.getX(),
                        "y1",
                        from.getY(),
                        "z1",
                        from.getZ(),
                        "x2",
                        to.getX(),
                        "y2",
                        to.getY(),
                        "z2",
                        to.getZ()));
    }

    @GameTest(template = "arena", batch = "build_fill", timeoutTicks = 400)
    public static void fill_TwoCubeBottomUp_SpiritEndsOutside(GameTestHelper helper) {
        prepare(helper);
        BlockPos first = new BlockPos(6, 1, 6);
        BlockPos second = new BlockPos(7, 2, 7);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 6; x <= 7; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 6; z <= 7; z++) {
                    cells.add(new BlockPos(x, y, z));
                }
            }
        }
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 8);
        Map<BlockPos, Integer> seenAt = new HashMap<>();
        helper.onEachTick(() -> {
            for (BlockPos cell : cells) {
                if (!seenAt.containsKey(cell) && helper.getBlockState(cell).is(Blocks.COBBLESTONE)) {
                    seenAt.put(cell, spirit.tickCount);
                }
            }
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, fillCall(helper, first, second), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "коробка 2×2×2 обязана залиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(seenAt.size() == 8, "все 8 клеток обязаны появиться в мире, замечено " + seenAt.size());
            List<BlockPos> sequence = new ArrayList<>(seenAt.keySet());
            sequence.sort((left, right) -> Integer.compare(seenAt.get(left), seenAt.get(right)));
            for (int i = 1; i < sequence.size(); i++) {
                helper.assertTrue(
                        sequence.get(i - 1).getY() <= sequence.get(i).getY(),
                        "порядок постановки обязан идти снизу вверх: клетка " + sequence.get(i - 1) + " позже "
                                + sequence.get(i) + ", верхний блок без опоры снизу обвалится");
            }
            BlockPos feet = spirit.blockPosition();
            BlockPos boxMin = helper.absolutePos(first);
            BlockPos boxMax = helper.absolutePos(second);
            boolean inside = feet.getX() >= boxMin.getX()
                    && feet.getX() <= boxMax.getX()
                    && feet.getY() >= boxMin.getY()
                    && feet.getY() <= boxMax.getY()
                    && feet.getZ() >= boxMin.getZ()
                    && feet.getZ() <= boxMax.getZ();
            helper.assertTrue(
                    !inside, "дух обязан стоять снаружи залитого: иначе он замуровал себя, клетка духа " + feet);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "8 блоков обязаны израсходоваться ровно: двойного расхода нет");
            helper.assertTrue(
                    run.spiritTicks() <= 380,
                    "заливка 8 блоков обязана уложиться в 380 тиков духа, заняло " + run.spiritTicks());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_reserve", timeoutTicks = 400)
    public static void fill_TwoSpiritsOneBox_EachCellPlacedOnce(GameTestHelper helper) {
        prepare(helper);
        BlockPos first = new BlockPos(4, 1, 4);
        BlockPos second = new BlockPos(5, 1, 5);
        SpiritEntity firstSpirit = spawnWith(helper, new BlockPos(1, 1, 1), 4);
        SpiritEntity secondSpirit = spawnWith(helper, new BlockPos(7, 1, 7), 4);
        SpiritRun firstRun = SpiritRun.launch(helper, firstSpirit, fillCall(helper, first, second), SETTLE_TICKS);
        SpiritRun secondRun = SpiritRun.launch(helper, secondSpirit, fillCall(helper, first, second), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            firstRun.assertStarted();
            secondRun.assertStarted();
            helper.assertTrue(
                    firstSpirit.taskStatus().isPresent()
                            && firstSpirit.taskStatus().orElse(null) != RunStatus.RUNNING
                            && secondSpirit.taskStatus().isPresent()
                            && secondSpirit.taskStatus().orElse(null) != RunStatus.RUNNING,
                    "обе задачи обязаны завершиться: первая "
                            + firstSpirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", вторая "
                            + secondSpirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            int inWorld = 0;
            for (int x = 4; x <= 5; x++) {
                for (int z = 4; z <= 5; z++) {
                    if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.COBBLESTONE)) {
                        inWorld++;
                    }
                }
            }
            helper.assertTrue(
                    inWorld == 4,
                    "все 4 клетки обязаны стоять: каждая поставлена ровно одним духом, в мире " + inWorld);
            int spent = 8
                    - GameTestFixtures.countInInventory(firstSpirit.getInventory(), Items.COBBLESTONE)
                    - GameTestFixtures.countInInventory(secondSpirit.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    spent == 4,
                    "потрачено обязано быть ровно 4 блока: двойной постановки и двойного расхода нет, потрачено "
                            + spent);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_fill_fail", timeoutTicks = 400)
    public static void fill_NeedEightHaveThree_FailsWithNumbersKeepsThree(GameTestHelper helper) {
        prepare(helper);
        BlockPos first = new BlockPos(4, 1, 4);
        BlockPos second = new BlockPos(5, 2, 5);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 3);
        SpiritRun run = SpiritRun.launch(helper, spirit, fillCall(helper, first, second), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "нехватка материала обязана провалить задачу, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("8") && reason.contains("3"),
                    "причина обязана называть числа 8 и 3 (нужно и есть), фактически: " + reason);
            int inWorld = 0;
            for (int x = 4; x <= 5; x++) {
                for (int y = 1; y <= 2; y++) {
                    for (int z = 4; z <= 5; z++) {
                        if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBBLESTONE)) {
                            inWorld++;
                        }
                    }
                }
            }
            helper.assertTrue(
                    inWorld == 3,
                    "в мире обязано стоять ровно 3 блока: что было, то поставили, дальше честный отказ, стоит "
                            + inWorld);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_fill_fail", timeoutTicks = 400)
    public static void fill_ProtectedBox_FailsNamingPolicyKeepsWorld(GameTestHelper helper) {
        prepare(helper);
        BlockPos first = new BlockPos(4, 1, 4);
        BlockPos second = new BlockPos(5, 2, 5);
        BlockPos from = helper.absolutePos(first);
        BlockPos to = helper.absolutePos(second);
        String dimension = helper.getLevel().dimension().location().toString();
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 8);
        try {
            GameTestFilter.setProtectedZones(List.of(dimension + " " + from.getX() + " " + from.getY() + " "
                    + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ()));
            SpiritRun run = SpiritRun.launch(helper, spirit, fillCall(helper, first, second), SETTLE_TICKS);

            helper.succeedWhen(() -> {
                run.assertStarted();
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                        "коробка в защищённой зоне обязана провалиться, статус "
                                + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
                helper.assertTrue(
                        spirit.taskFailureReason().orElse("").contains("защищён"),
                        "причина обязана называть защищённую зону, фактически: "
                                + spirit.taskFailureReason().orElse("нет"));
                int inWorld = 0;
                for (int x = 4; x <= 5; x++) {
                    for (int y = 1; y <= 2; y++) {
                        for (int z = 4; z <= 5; z++) {
                            if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBBLESTONE)) {
                                inWorld++;
                            }
                        }
                    }
                }
                helper.assertTrue(inWorld == 0, "мир обязан остаться нетронутым: в зону ни блока, стоит " + inWorld);
                helper.assertTrue(
                        GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 8,
                        "материал при отказе по зоне обязан остаться целым");
                GameTestFilter.setProtectedZones(List.of());
                GameTestFixtures.finish(helper);
            });
        } catch (RuntimeException error) {
            GameTestFilter.setProtectedZones(List.of());
            throw error;
        }
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

        helper.assertTrue(
                supplies.inInventory("cobblestone") == 2,
                "счёт обязан идти из инвентаря духа: 2 булыжника, посчитано " + supplies.inInventory("cobblestone"));
        helper.assertTrue(
                supplies.inInventory("nope_block") == 0, "неизвестный блок обязан считаться нулём, а не падать");
        var steps = supplies.withdraw("cobblestone", 8);
        helper.assertTrue(
                steps.size() == 2,
                "шагов обязано быть 2: ближний сундук (5) и дальний (3), пустой и печь пропускаются, дальний за радиусом исключён, шагов "
                        + steps.size());
        BlockPos nearAbs = helper.absolutePos(near);
        BlockPos farAbs = helper.absolutePos(far);
        helper.assertTrue(
                steps.get(0)
                                .params()
                                .get("container")
                                .equals(nearAbs.getX() + "," + nearAbs.getY() + "," + nearAbs.getZ())
                        && steps.get(0).params().get("count").equals(5),
                "первым обязан идти ближний сундук с пятёркой: ближний первым, фактически "
                        + steps.get(0).params());
        helper.assertTrue(
                steps.get(1).params().get("container").equals(farAbs.getX() + "," + farAbs.getY() + "," + farAbs.getZ())
                        && steps.get(1).params().get("count").equals(3),
                "вторым обязан идти дальний с остатком 3, фактически "
                        + steps.get(1).params());
        helper.assertTrue(supplies.withdraw("cobblestone", 0).isEmpty(), "запрос на ноль обязан давать пустые шаги");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static ScriptCall placeCall(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new ScriptCall(
                "place_block",
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
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "пустая клетка с блоком в инвентаре обязана застроиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(target).is(Blocks.COBBLESTONE), "в целевой клетке обязан стоять булыжник");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "предмет обязан израсходоваться: стройка бесплатно не строит, в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(
                    PlacedBlocksData.get(helper.getLevel()).isSpiritMarked(helper.absolutePos(target)),
                    "поставленный духом блок обязан помечаться меткой духа: иначе дух не разберёт свою постройку");
            helper.assertTrue(
                    run.spiritTicks() <= 300,
                    "постановка одного блока обязана уложиться в 300 тиков духа, заняло " + run.spiritTicks());
            GameTestFixtures.finish(helper);
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
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "занятая клетка обязана дать отказ, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("занята"),
                    "причина обязана называть, что место занято, фактически: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(target).is(Blocks.STONE), "занятая клетка обязана остаться камнем");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 1,
                    "материал при отказе обязан остаться в инвентаре");
            GameTestFixtures.finish(helper);
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
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "далёкая клетка обязана застроиться после подхода, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(target).is(Blocks.COBBLESTONE),
                    "в дальней клетке обязан стоять булыжник: дух подошёл и поставил");
            GameTestFixtures.finish(helper);
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
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "без материала задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("нужно 1, есть 0"),
                    "причина обязана называть числа «нужно 1, есть 0», фактически: " + reason);
            helper.assertTrue(helper.getBlockState(target).isAir(), "без материала клетка обязана остаться пустой");
            GameTestFixtures.finish(helper);
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
        helper.assertTrue(
                busy instanceof BlockPlacer.Refused refused && refused.reason() == BlockPlacer.RefuseReason.OCCUPIED,
                "занятая клетка без материала обязана отказать занятостью, а не нехваткой: проверка места идёт раньше материала, ответ "
                        + busy);

        BlockPos far = new BlockPos(8, 1, 8);
        BlockPlacer.Begin distant = placer.begin(helper.absolutePos(far), wanted, "cobblestone");
        helper.assertTrue(
                distant instanceof BlockPlacer.Refused refused
                        && refused.reason() == BlockPlacer.RefuseReason.OUT_OF_REACH,
                "далёкая клетка без материала обязана отказать досягаемостью: подход идёт раньше списания, ответ "
                        + distant);

        BlockPos free = new BlockPos(2, 1, 2);
        BlockPlacer.Begin poor = placer.begin(helper.absolutePos(free), wanted, "cobblestone");
        helper.assertTrue(
                poor instanceof BlockPlacer.Refused refused && refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL,
                "пустая клетка рядом без материала обязана отказать нехваткой с числами, ответ " + poor);
        helper.assertTrue(helper.getBlockState(free).isAir(), "отказ не трогает мир: клетка обязана остаться пустой");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos relative) {
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }
}

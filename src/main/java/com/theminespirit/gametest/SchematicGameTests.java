package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SchematicGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos ANCHOR = new BlockPos(3, 1, 3);

    private static final int[][] HOUSE_FLOOR = {
        {0, 0, 0}, {1, 0, 0}, {2, 0, 0},
        {0, 0, 1}, {1, 0, 1}, {2, 0, 1},
        {0, 0, 2}, {1, 0, 2}, {2, 0, 2},
    };
    private static final int[][] HOUSE_WALLS = {
        {0, 1, 0}, {1, 1, 0}, {2, 1, 0},
        {0, 1, 2}, {1, 1, 2}, {2, 1, 2},
        {0, 1, 1}, {2, 1, 1},
    };

    private SchematicGameTests() {}

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

    private static ScriptCall schematicCall(GameTestHelper helper, BlockPos anchor) {
        BlockPos absolute = helper.absolutePos(anchor);
        return new ScriptCall(
                "build_schematic",
                Map.of("file", "fixture_plate", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ()));
    }

    private static void buildHouseByHand(GameTestHelper helper, BlockPos anchor) {
        for (int[] cell : HOUSE_FLOOR) {
            helper.setBlock(anchor.offset(cell[0], cell[1], cell[2]), Blocks.COBBLESTONE);
        }
        for (int[] cell : HOUSE_WALLS) {
            helper.setBlock(anchor.offset(cell[0], cell[1], cell[2]), Blocks.COBBLESTONE);
        }
    }

    private static int countHouse(GameTestHelper helper, BlockPos anchor) {
        int found = 0;
        for (int[] cell : HOUSE_FLOOR) {
            if (helper.getBlockState(anchor.offset(cell[0], cell[1], cell[2])).is(Blocks.COBBLESTONE)) {
                found++;
            }
        }
        for (int[] cell : HOUSE_WALLS) {
            if (helper.getBlockState(anchor.offset(cell[0], cell[1], cell[2])).is(Blocks.COBBLESTONE)) {
                found++;
            }
        }
        return found;
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void schematic_TwoSpiritsOneHouse_EachBlockPlacedOnce(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity firstSpirit = spawnWith(helper, new BlockPos(1, 1, 1), 17);
        SpiritEntity secondSpirit = spawnWith(helper, new BlockPos(7, 1, 7), 17);
        SpiritRun firstRun = SpiritRun.launch(helper, firstSpirit, schematicCall(helper, ANCHOR), SETTLE_TICKS);
        SpiritRun secondRun = SpiritRun.launch(helper, secondSpirit, schematicCall(helper, ANCHOR), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            firstRun.assertStarted();
            secondRun.assertStarted();
            helper.assertTrue(
                    finished(firstSpirit) && finished(secondSpirit),
                    "обе стройки обязаны завершиться успехом: первая "
                            + firstSpirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", вторая "
                            + secondSpirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    countHouse(helper, ANCHOR) == 17,
                    "домик по схеме обязан встать целиком: двое строителей делят клетки, вместе 17, фактически "
                            + countHouse(helper, ANCHOR));
            int remaining = GameTestFixtures.countInInventory(firstSpirit.getInventory(), Items.COBBLESTONE)
                    + GameTestFixtures.countInInventory(secondSpirit.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    34 - remaining == 17,
                    "потрачено обязано быть ровно 17 блоков: двойной постановки и двойного расхода нет, потрачено "
                            + (34 - remaining));
            GameTestFixtures.finish(helper);
        });
    }

    // Свободный дух стоит в клетке схемы: строитель отводит его за габарит и ставит блок (хижина вставала 100 из 101
    // «клетку занимает живое существо», приёмка «джунгли» 24.10).
    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void schematic_IdleSpiritInCell_MovedAsideAndHouseDone(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity builder = spawnWith(helper, new BlockPos(1, 1, 1), 17);
        SpiritEntity idle = spawnWith(helper, ANCHOR.offset(1, 0, 1), 0);
        SpiritRun run = SpiritRun.launch(helper, builder, schematicCall(helper, ANCHOR), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    finished(builder) && countHouse(helper, ANCHOR) == 17,
                    "стройка обязана завершиться, отведя свободного духа из клетки: статус "
                            + builder.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + builder.taskFailureReason().orElse("нет") + ", блоков " + countHouse(helper, ANCHOR)
                            + ", свободный дух в " + idle.blockPosition());
            idle.discard();
            GameTestFixtures.finish(helper);
        });
    }

    private static boolean finished(SpiritEntity spirit) {
        return spirit.taskStatus().isPresent() && spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED;
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void schematic_HouseFromFile_StandsWithCountedMaterials(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 17);
        SpiritRun run = SpiritRun.launch(helper, spirit, schematicCall(helper, ANCHOR), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "домик по схеме обязан построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    countHouse(helper, ANCHOR) == 17,
                    "все 17 блоков схемы обязаны стоять на месте, фактически " + countHouse(helper, ANCHOR));
            helper.assertTrue(
                    helper.getBlockState(ANCHOR.offset(1, 1, 1)).isAir(),
                    "дверной проём схемы обязан остаться пустым: лишних блоков нет");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "материалы списываются по видам: 17 блоков — 17 булыжников, в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void schematic_AlreadyBuilt_SucceedsWithoutSpending(GameTestHelper helper) {
        prepare(helper);
        buildHouseByHand(helper, ANCHOR);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 17);
        SpiritRun run = SpiritRun.launch(helper, spirit, schematicCall(helper, ANCHOR), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "повтор на готовом домике обязан завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    countHouse(helper, ANCHOR) == 17,
                    "домик обязан остаться целым, блоков " + countHouse(helper, ANCHOR));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 17,
                    "уже стоящие блоки ставятся повторно: двойного расхода нет, в инвентаре обязано остаться 17, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void schematic_NeedSeventeenHaveFive_FailsWithNumbersKeepsFive(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 5);
        SpiritRun run = SpiritRun.launch(helper, spirit, schematicCall(helper, ANCHOR), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "нехватка материала обязана провалить стройку по схеме, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("17") && reason.contains("5"),
                    "причина обязана называть числа 17 и 5 (нужно и поставлено), фактически: " + reason);
            helper.assertTrue(
                    countHouse(helper, ANCHOR) == 5,
                    "в мире обязано стоять ровно 5 блоков: что было, то поставили, стоит "
                            + countHouse(helper, ANCHOR));
            GameTestFixtures.finish(helper);
        });
    }
}

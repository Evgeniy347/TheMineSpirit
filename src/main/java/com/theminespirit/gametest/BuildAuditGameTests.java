package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.RunStatus;
import ScriptResult;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildAuditGameTests {
    private static final int SETTLE_TICKS = 5;

    private BuildAuditGameTests() {
    }

    private static BlockCoord origin(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static Schematic oneStairs() {
        return new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "minecraft:oak_stairs",
                Map.of("facing", "east"))));
    }

    @GameTest(template = "arena", batch = "build_audit_selftest", timeoutTicks = 200)
    public static void audit_BlockShiftedByOne_ReportsMissingAndExtra(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        BlockCoord origin = origin(helper, new BlockPos(3, 1, 3));
        Schematic schematic = oneStairs();
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, 3);
        BlockCoord shifted = new BlockCoord(origin.x() + 1, origin.y(), origin.z());
        helper.getLevel().setBlock(pos(shifted),
                BlockStates.stateFor("minecraft:oak_stairs", Map.of("facing", "east")), 3);
        PlacedBlocksData.get(helper.getLevel()).markSpirit(pos(shifted));

        BuildAudit.Result result = BuildAudit.audit(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, before, 0);

        helper.assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("не совпадает")),
                "Сдвинутый на 1 блок обязан дать расхождение схемы: " + result.problems());
        helper.assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("лишний блок")),
                "Сдвинутый блок обязан остаться лишним в районе стройки: " + result.problems());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "build_audit_selftest", timeoutTicks = 200)
    public static void audit_ExtraBlock_ReportsExtraPlacement(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        BlockCoord origin = origin(helper, new BlockPos(3, 1, 3));
        Schematic schematic = oneStairs();
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, 3);
        helper.getLevel().setBlock(pos(origin),
                BlockStates.stateFor("minecraft:oak_stairs", Map.of("facing", "east")), 3);
        PlacedBlocksData.get(helper.getLevel()).markSpirit(pos(origin));
        BlockCoord extra = new BlockCoord(origin.x() + 2, origin.y(), origin.z() + 1);
        helper.getLevel().setBlock(pos(extra), Blocks.COBBLESTONE.defaultBlockState(), 3);

        BuildAudit.Result result = BuildAudit.audit(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, before, 0);

        helper.assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("лишний блок")),
                "Поставленный духом лишний блок обязан попасть в расхождения: " + result.problems());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "build_audit_selftest", timeoutTicks = 200)
    public static void audit_WrongFacing_ReportsStateMismatch(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        BlockCoord origin = origin(helper, new BlockPos(3, 1, 3));
        Schematic schematic = oneStairs();
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, 3);
        helper.getLevel().setBlock(pos(origin),
                BlockStates.stateFor("minecraft:oak_stairs", Map.of("facing", "west")), 3);
        PlacedBlocksData.get(helper.getLevel()).markSpirit(pos(origin));

        BuildAudit.Result result = BuildAudit.audit(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, before, 0);

        helper.assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("по состоянию")),
                "Неверный facing обязан дать расхождение состояния: " + result.problems());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "build_audit_wall", timeoutTicks = 400)
    public static void build_WallSixHigh_PlacedWithoutScaffoldsAndExtras(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Schematic schematic = SchematicResources.load("reach_wall");
        BlockCoord origin = origin(helper, new BlockPos(4, 1, 4));
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, 3);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 7));
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE, 6));
        BlockPos absolute = helper.absolutePos(new BlockPos(4, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_schematic",
                Map.of("file", "reach_wall", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Стена высотой 6 обязана построиться без лесов, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = resultData(helper, spirit);
            BuildAudit.Result audit = BuildAudit.audit(helper.getLevel(), schematic, origin,
                    BuildRotation.NONE, before, skipped(data));
            helper.assertTrue(audit.ok(),
                    "Стена обязана совпасть со схемой и не оставить лишних блоков: " + audit.problems());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_audit_state", timeoutTicks = 400)
    public static void build_TwoPartBlocksAndNeighbourShapes_StandsWhole(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Schematic schematic = SchematicResources.load("state_demo");
        BlockCoord origin = origin(helper, new BlockPos(4, 1, 4));
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin,
                BuildRotation.NONE, 3);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 7));
        spirit.getInventory().setItem(0, new ItemStack(Items.OAK_DOOR, 2));
        spirit.getInventory().setItem(1, new ItemStack(Items.GLASS_PANE, 1));
        spirit.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 1));
        spirit.getInventory().setItem(3, new ItemStack(Items.WHITE_BED, 1));
        spirit.getInventory().setItem(4, new ItemStack(Items.OAK_FENCE, 2));
        spirit.getInventory().setItem(5, new ItemStack(Items.WHITE_BED, 1));
        BlockPos absolute = helper.absolutePos(new BlockPos(4, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_schematic",
                Map.of("file", "state_demo", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Схема с дверью, кроватью, панелью и забором обязана построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = resultData(helper, spirit);
            BuildAudit.Result audit = BuildAudit.audit(helper.getLevel(), schematic, origin,
                    BuildRotation.NONE, before, skipped(data));
            helper.assertTrue(audit.ok(), "Состояния и двухчастные блоки обязаны совпасть со схемой: "
                    + audit.problems());
            BlockState upperDoor = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(4, 2, 4)));
            helper.assertTrue(upperDoor.is(Blocks.OAK_DOOR)
                            && upperDoor.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                            == DoubleBlockHalf.UPPER,
                    "Верхняя половина двери обязана стоять: иначе дверь поставлена одной половиной");
            BlockState bedHead = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(6, 1, 5)));
            helper.assertTrue(bedHead.is(Blocks.WHITE_BED)
                            && bedHead.getValue(BlockStateProperties.BED_PART)
                            == BedPart.HEAD,
                    "Изголовье кровати обязано стоять: иначе кровать поставлена одной половиной");
            BlockState pane = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(4, 1, 5)));
            helper.assertTrue(pane.getValue(BlockStateProperties.EAST),
                    "Панель обязана соединиться с соседней доской через updateFromNeighbourShapes: " + pane);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_audit_placer", timeoutTicks = 200)
    public static void blockPlacer_LivingEntityInCell_RefusesOccupied(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 2));
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE, 1));
        helper.spawn(EntityType.CHICKEN, new BlockPos(4, 1, 4));
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));

        BlockPlacer.Begin begin = new BlockPlacer(spirit).begin(target, Blocks.STONE.defaultBlockState(), "minecraft:stone");

        helper.assertTrue(begin instanceof BlockPlacer.Refused refused
                        && refused.reason() == BlockPlacer.RefuseReason.OCCUPIED,
                "BlockPlacer обязан отказать, если клетку занимает живое существо: иначе дух ставит блок в моба, "
                        + "тот получает урон и уходит, а стройка идёт не туда; фактически " + begin);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    // [флак] Дом из 17 блоков с одной занятой клеткой: перебор стоянок и повторный проход перед провалом.
    @GameTest(template = "arena", batch = "build_audit_blocked", timeoutTicks = 1200)
    public static void build_BlockedCell_FailsWithSkipCount(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos anchorRelative = new BlockPos(3, 1, 3);
        helper.setBlock(anchorRelative, Blocks.DIRT);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 17));
        BlockPos anchor = helper.absolutePos(anchorRelative);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_schematic",
                Map.of("file", "house", "x", anchor.getX(), "y", anchor.getY(), "z", anchor.getZ())), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "Занятая клетка обязана провалить стройку, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("пропущено 1"),
                    "Причина обязана называть число пропусков, фактически: " + reason);
            GameTestFixtures.finish(helper);
        });
    }


    // [флак] Восемь блоков с поворотом: дух подходит к каждой клетке с запасом.
    @GameTest(template = "arena", batch = "build_audit_state_rot", timeoutTicks = 1800)
    public static void build_StateDemoRotated90_StatesTurnWithScheme(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Schematic schematic = SchematicResources.load("state_demo");
        BlockCoord origin = origin(helper, new BlockPos(4, 1, 4));
        BuildRotation rotation =
                BuildRotation.CLOCKWISE_90;
        BuildAudit.Before before = BuildAudit.snapshot(helper.getLevel(), schematic, origin, rotation, 3);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 7));
        spirit.getInventory().setItem(0, new ItemStack(Items.OAK_DOOR, 2));
        spirit.getInventory().setItem(1, new ItemStack(Items.GLASS_PANE, 1));
        spirit.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 1));
        spirit.getInventory().setItem(3, new ItemStack(Items.WHITE_BED, 1));
        spirit.getInventory().setItem(4, new ItemStack(Items.OAK_FENCE, 2));
        spirit.getInventory().setItem(5, new ItemStack(Items.WHITE_BED, 1));
        BlockPos absolute = helper.absolutePos(new BlockPos(4, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_schematic",
                Map.of("file", "state_demo", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ(),
                        "rotation", rotation.quarterTurns())), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Схема с поворотом 90 обязана построиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = resultData(helper, spirit);
            BuildAudit.Result audit = BuildAudit.audit(helper.getLevel(), schematic, origin, rotation, before,
                    skipped(data));
            helper.assertTrue(audit.ok(), "facing обязан повернуться вместе со схемой, иначе клетки не совпадут: "
                    + audit.problems());
            GameTestFixtures.finish(helper);
        });
    }

    private static Map<String, Object> resultData(GameTestHelper helper, SpiritEntity spirit) {
        var result = spirit.taskResult().orElse(null);
        helper.assertTrue(result instanceof ScriptResult.Data,
                "Успешная стройка обязана нести Data-итог: " + (result == null ? "пусто" : result));
        return ((ScriptResult.Data) result).fields();
    }

    private static int skipped(Map<String, Object> data) {
        return ((Number) data.get("skipped")).intValue();
    }
}

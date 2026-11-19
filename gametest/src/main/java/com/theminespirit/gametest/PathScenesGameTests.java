package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.board.route.RouteTrailBook;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PathScenesGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos FLAT_ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos LOW_ORIGIN = new BlockPos(2, 1, 2);
    private static final BlockPos WEED_ORIGIN = new BlockPos(4, 2, 4);
    private static final BlockPos SUNK_ORIGIN = new BlockPos(3, 1, 3);

    private PathScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_path_arena", timeoutTicks = 400)
    public static void pathScene_UnevenGround_PathStaysOnPlanLevel(GameTestHelper helper) {
        prepareUnevenGround(helper);
        SpiritEntity flat = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(8, 1, 8));
        SpiritEntity low = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(7, 1, 7));
        SpiritEntity weed = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(6, 1, 6));
        SpiritEntity sunk = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, 1, 5));
        shovelAndDirt(low);
        shovelAndDirt(weed);
        shovelAndDirt(sunk);
        SpiritRun flatRun = SpiritRun.launch(helper, flat, build(helper, FLAT_ORIGIN), SETTLE_TICKS);
        SpiritRun lowRun = SpiritRun.launch(helper, low, build(helper, LOW_ORIGIN), SETTLE_TICKS);
        SpiritRun weedRun = SpiritRun.launch(helper, weed, build(helper, WEED_ORIGIN), SETTLE_TICKS);
        SpiritRun sunkRun = SpiritRun.launch(helper, sunk, build(helper, SUNK_ORIGIN), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            flatRun.assertStarted();
            lowRun.assertStarted();
            weedRun.assertStarted();
            sunkRun.assertStarted();
            assertFlatPath(helper, flat);
            assertLowPath(helper, low);
            assertWeedPath(helper, weed);
            assertSunkPath(helper, sunk);
            helper.setBlock(LOW_ORIGIN.getX(), -1, LOW_ORIGIN.getZ(), Blocks.AIR);
            GameTestFixtures.finish(helper, 16);
        });
    }

    private static void prepareUnevenGround(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        for (int x = -1; x <= 10; x++) {
            for (int z = -1; z <= 10; z++) {
                helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(LOW_ORIGIN.getX(), 0, LOW_ORIGIN.getZ(), Blocks.AIR);
        helper.setBlock(LOW_ORIGIN.getX(), -1, LOW_ORIGIN.getZ(), Blocks.DIRT);
        helper.setBlock(FLAT_ORIGIN.getX(), FLAT_ORIGIN.getY(), FLAT_ORIGIN.getZ(), Blocks.DIRT_PATH);
        helper.setBlock(10, 1, 10, Blocks.CHEST);
        ChestBlockEntity chest =
                (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(10, 1, 10)));
        chest.setItem(0, new ItemStack(Items.IRON_SHOVEL));
        helper.setBlock(WEED_ORIGIN.getX(), WEED_ORIGIN.getY() - 1, WEED_ORIGIN.getZ(), Blocks.SHORT_GRASS);
        helper.setBlock(SUNK_ORIGIN.getX(), SUNK_ORIGIN.getY() - 1, SUNK_ORIGIN.getZ(), Blocks.AIR);
        helper.setBlock(SUNK_ORIGIN.getX(), SUNK_ORIGIN.getY() - 2, SUNK_ORIGIN.getZ(), Blocks.DIRT_PATH);
    }

    private static void shovelAndDirt(SpiritEntity spirit) {
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SHOVEL));
        spirit.getInventory().setItem(1, new ItemStack(Items.DIRT, 4));
    }

    private static void assertFlatPath(GameTestHelper helper, SpiritEntity flat) {
        helper.assertTrue(
                flat.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "тропа на ровной земле обязана встать целиком, статус "
                        + flat.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + flat.taskFailureReason().orElse("нет"));
        helper.assertTrue(
                helper.getBlockState(new BlockPos(FLAT_ORIGIN.getX(), 0, FLAT_ORIGIN.getZ()))
                        .is(Blocks.DIRT_PATH),
                "тропа на ровной земле заменяет верхний блок земли: её быть на уровне поверхности");
        helper.assertTrue(
                helper.getBlockState(FLAT_ORIGIN).isAir(),
                "старая тропа блоком над поверхностью снята, а не осталась висеть над новой");
    }

    private static void assertLowPath(GameTestHelper helper, SpiritEntity low) {
        helper.assertTrue(
                low.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "тропа на низкой колонке обязана встать целиком, статус "
                        + low.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + low.taskFailureReason().orElse("нет"));
        helper.assertTrue(
                helper.getBlockState(new BlockPos(LOW_ORIGIN.getX(), 0, LOW_ORIGIN.getZ()))
                        .is(Blocks.DIRT_PATH),
                "тропа на низкой колонке поднята в уровень плана, а не утоплена ниже схемы");
        helper.assertTrue(
                helper.getBlockState(new BlockPos(LOW_ORIGIN.getX(), -1, LOW_ORIGIN.getZ()))
                        .is(Blocks.DIRT),
                "под поднятой тропой остаётся подсыпанная земля — дорожка не висит");
        helper.assertTrue(
                helper.getBlockState(LOW_ORIGIN).isAir(),
                "клетка схемы над дорожкой остаётся воздухом: тропа не встаёт блоком над землёй");
    }

    private static void assertWeedPath(GameTestHelper helper, SpiritEntity weed) {
        helper.assertTrue(
                weed.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "сорняк в клетке тропы снят и тропа легла в уровень плана, статус "
                        + weed.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + weed.taskFailureReason().orElse("нет"));
        helper.assertTrue(
                helper.getBlockState(new BlockPos(WEED_ORIGIN.getX(), WEED_ORIGIN.getY() - 1, WEED_ORIGIN.getZ()))
                        .is(Blocks.DIRT_PATH),
                "тропа поднята в клетку плана: сорняк снят, земля подсыпана");
        helper.assertTrue(
                helper.getBlockState(new BlockPos(WEED_ORIGIN.getX(), WEED_ORIGIN.getY() - 2, WEED_ORIGIN.getZ()))
                        .is(Blocks.GRASS_BLOCK),
                "опора под поднятой тропой цела");
    }

    private static void assertSunkPath(GameTestHelper helper, SpiritEntity sunk) {
        helper.assertTrue(
                sunk.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "утопленная тропа поднята в уровень плана, статус "
                        + sunk.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + sunk.taskFailureReason().orElse("нет"));
        helper.assertTrue(
                helper.getBlockState(new BlockPos(SUNK_ORIGIN.getX(), 0, SUNK_ORIGIN.getZ()))
                        .is(Blocks.DIRT_PATH),
                "старая утопленная тропа переложена в клетку плана");
        helper.assertTrue(
                helper.getBlockState(new BlockPos(SUNK_ORIGIN.getX(), -1, SUNK_ORIGIN.getZ()))
                        .is(Blocks.DIRT),
                "старая тропа снята лопатой до земли: под новой дорожкой ступеньки нет");
    }

    @GameTest(template = "arena", batch = "scene_path_tool", timeoutTicks = 400)
    public static void pathScene_NoShovel_FailsAsMissingToolNotMaterial(GameTestHelper helper) {
        flatGrass(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(8, 1, 8));
        SpiritRun run = SpiritRun.launch(helper, spirit, build(helper, FLAT_ORIGIN), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "без лопаты стройка тропы обязана честно провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("нужен инструмент"),
                    "Причина — отсутствие лопаты, а не 'не хватило материала dirt_path': " + reason);
            helper.assertTrue(
                    !reason.contains("не хватило материала"),
                    "Предмет dirt_path не добывают, поэтому и 'не хватило материала' быть не может: " + reason);
            GameTestFixtures.finish(helper, 16);
        });
    }

    @GameTest(template = "arena", batch = "scene_path_trail", timeoutTicks = 400)
    public static void routeScene_MovedQuarry_ClearsOldTrail(GameTestHelper helper) {
        flatGrass(helper);
        List<BlockPos> placed = List.of(new BlockPos(3, 1, 3), new BlockPos(4, 1, 3), new BlockPos(5, 1, 3));
        for (BlockPos at : placed) {
            helper.setBlock(at, Blocks.SAND);
        }
        List<BlockCoord> cells = placed.stream()
                .map(at -> {
                    BlockPos absolute = helper.absolutePos(at);
                    return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
                })
                .toList();
        RouteTrailBook book = new RouteTrailBook();
        book.plan(
                RouteResource.SAND,
                new PlotLeveling(
                        List.of(),
                        cells.stream()
                                .map(cell -> new PlotLeveling.Box(
                                        cell.x(), cell.y(), cell.z(), cell.x(), cell.y(), cell.z()))
                                .toList(),
                        "sand"));
        book.built(RouteResource.SAND);
        book.retire(RouteResource.SAND);
        TaskNode removal = TicketPlans.plan(
                BoardCase.REPAIR_BUILDING,
                TicketTarget.at(cells.get(0))
                        .withLeveling(book.retired().get(0).trail().removal()));

        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SHOVEL));
        SpiritRun run = SpiritRun.launch(helper, spirit, removal, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "снятие старой тропы обязано закончиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            for (BlockPos at : placed) {
                helper.assertTrue(
                        helper.getBlockState(at).isAir(),
                        "старая тропа при переносе места снята: в клетке " + at + " остался "
                                + helper.getBlockState(at));
            }
            GameTestFixtures.finish(helper, 16);
        });
    }

    private static void flatGrass(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 12; z++) {
                helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            }
        }
    }

    private static ScriptCall build(GameTestHelper helper, BlockPos origin) {
        BlockPos absolute = helper.absolutePos(origin);
        return new ScriptCall(
                "build_schematic",
                Map.of(
                        "file", "street_patch",
                        "x", absolute.getX(),
                        "y", absolute.getY(),
                        "z", absolute.getZ()));
    }
}

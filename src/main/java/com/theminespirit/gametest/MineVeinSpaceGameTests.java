package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.CAVE_SHIFT_INSIDE_ARENA;
import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_START;
import static com.theminespirit.gametest.MineSceneSupport.buildCave;
import static com.theminespirit.gametest.MineSceneSupport.mineCall;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class MineVeinSpaceGameTests {
    private MineVeinSpaceGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void mineVein_SealedOreWithinReach_MinedFromPlaceNeighboursStay(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "замурованная руда в досягаемости (упрощение 44.3: жила с места, без прямой видимости) обязана "
                            + "добыться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .isAir(),
                    "руда обязана быть сломана");
            for (BlockPos neighbour : List.of(
                    new BlockPos(1, 2, 2),
                    new BlockPos(3, 2, 2),
                    new BlockPos(2, 1, 2),
                    new BlockPos(2, 3, 2),
                    new BlockPos(2, 2, 1),
                    new BlockPos(2, 2, 3))) {
                helper.assertTrue(
                        helper.getBlockState(neighbour.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA))
                                .is(Blocks.STONE),
                        "камень вокруг руды не трогается: ломается только цель, а не проход к ней (" + neighbour + ")");
            }
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 1,
                    "сырое железо обязано лечь в инвентарь: дроп целевого блока сразу в инвентарь");
            helper.assertTrue(
                    spirit.pathBuilds() == 0,
                    "руда в досягаемости ломается без подхода, путь строился " + spirit.pathBuilds() + " раз");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void mineVein_OreAboveThreeDeepPit_MinedFromPlaceSpiritKeepsHeight(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        for (int y = 0; y >= -2; y--) {
            helper.setBlock(0 + CAVE_SHIFT_INSIDE_ARENA, y, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.AIR);
        }
        helper.setBlock(0 + CAVE_SHIFT_INSIDE_ARENA, -3, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);
        double startY = spirit.getY();

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "руда у ямы в досягаемости добывается с места (упрощение 44.3), статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .isAir(),
                    "руда обязана быть сломана");
            helper.assertTrue(
                    Math.abs(spirit.getY() - startY) < 0.1,
                    "дух не должен спускаться в яму глубиной 3: стоянка без пола запрещена, а руда берётся с места. "
                            + "y был " + startY + ", стал " + spirit.getY());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_OreUnderCeilingGravel_MinesFromSideAndIsNotBuried(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 4, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.GRAVEL);
        SpiritEntity spirit = spawnWith(
                helper, new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    !helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "руду на потолке под гравием обязано быть добыта, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            BlockPos targetAbs =
                    helper.absolutePos(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 3, 2 + CAVE_SHIFT_INSIDE_ARENA));
            BlockPos spiritCell = spirit.blockPosition();
            helper.assertTrue(
                    spiritCell.getX() != targetAbs.getX() || spiritCell.getZ() != targetAbs.getZ(),
                    "стоянку в столбце руды под гравием запрещено выбирать: дух добыл руду, стоя в " + spiritCell
                            + ", столбец руды " + targetAbs);
            GameTestFixtures.finish(helper);
        });
    }
}

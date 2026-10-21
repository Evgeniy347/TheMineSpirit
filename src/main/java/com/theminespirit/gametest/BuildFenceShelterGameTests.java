package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildFenceShelterGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final int FEET_Y = 1;

    private BuildFenceShelterGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos at, ItemStack... supplies) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), at);
        for (int slot = 0; slot < supplies.length; slot++) {
            spirit.getInventory().setItem(slot, supplies[slot]);
        }
        return spirit;
    }

    private static void assertSucceeded(GameTestHelper helper, SpiritEntity spirit, String message) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                message + ": статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + spirit.taskFailureReason().orElse("нет"));
    }

    @GameTest(template = "arena", batch = "build_fence_script", timeoutTicks = 400)
    public static void buildFence_RectangleWithGate_PlacesFenceAroundAndGateInOpening(GameTestHelper helper) {
        prepare(helper);
        List<BlockPos> corners = List.of(
                new BlockPos(2, FEET_Y, 2),
                new BlockPos(6, FEET_Y, 2),
                new BlockPos(6, FEET_Y, 6),
                new BlockPos(2, FEET_Y, 6));
        List<String> points = new ArrayList<>();
        for (BlockPos corner : corners) {
            BlockPos absolute = helper.absolutePos(corner);
            points.add(absolute.getX() + "," + absolute.getY() + "," + absolute.getZ());
        }
        BlockPos gate = new BlockPos(4, FEET_Y, 2);
        SpiritEntity spirit =
                spawnWith(helper, START, new ItemStack(Items.OAK_FENCE, 15), new ItemStack(Items.OAK_FENCE_GATE, 1));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_fence",
                        Map.of("block", "minecraft:oak_fence", "gate", "minecraft:oak_fence_gate", "points", points)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "забор по прямоугольнику обязан построиться целиком");
            for (BlockPos cell : perimeter(corners)) {
                if (cell.equals(gate)) {
                    helper.assertTrue(
                            helper.getBlockState(cell).is(Blocks.OAK_FENCE_GATE),
                            "в середине длинной стороны обязана стоять калитка, а там "
                                    + helper.getBlockState(cell).getBlock());
                } else {
                    helper.assertTrue(
                            helper.getBlockState(cell).is(Blocks.OAK_FENCE),
                            "клетка периметра " + cell + " обязана быть забором, а там "
                                    + helper.getBlockState(cell).getBlock());
                }
            }
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_FENCE) == 0,
                    "пятнадцать блоков забора обязаны израсходоваться ровно: двойного расхода нет");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_FENCE_GATE) == 0,
                    "калитка обязана быть поставлена: в инвентаре не осталось ни одной");
            GameTestFixtures.finish(helper);
        });
    }

    private static List<BlockPos> perimeter(List<BlockPos> corners) {
        List<BlockPos> cells = new ArrayList<>();
        for (int index = 0; index < corners.size(); index++) {
            BlockPos from = corners.get(index);
            BlockPos to = corners.get((index + 1) % corners.size());
            int dx = Integer.signum(to.getX() - from.getX());
            int dz = Integer.signum(to.getZ() - from.getZ());
            int length = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
            for (int step = 0; step < length; step++) {
                cells.add(new BlockPos(from.getX() + dx * step, from.getY(), from.getZ() + dz * step));
            }
        }
        return cells;
    }
}

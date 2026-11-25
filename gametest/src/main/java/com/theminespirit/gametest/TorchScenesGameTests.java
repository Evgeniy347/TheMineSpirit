package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.BitSet;
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
public final class TorchScenesGameTests {
    private static final BlockPos GROUND = new BlockPos(7, 1, 4);
    private static final BlockPos OCCUPIED = new BlockPos(8, 1, 4);
    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final BlockPos STREET = new BlockPos(4, 1, 0);

    private TorchScenesGameTests() {}

    @GameTest(template = "mine_arena", batch = "scene_torch_ground", timeoutTicks = 400)
    public static void placeBlockAdHocTorch_GroundAndOccupied_PlacedAndSkipped(GameTestHelper helper) {
        SettlementPlotStore store = prepare(helper);
        helper.setBlock(OCCUPIED, Blocks.DIRT);
        SpiritEntity onGround = spawnWithTorches(helper, new BlockPos(6, 1, 4));
        SpiritEntity onDirt = spawnWithTorches(helper, new BlockPos(8, 1, 3));
        SpiritRun groundRun = SpiritRun.launch(helper, onGround, torchCall(helper, GROUND), 1);
        SpiritRun dirtRun = SpiritRun.launch(helper, onDirt, torchCall(helper, OCCUPIED), 1);
        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, onGround, groundRun, RunStatus.SUCCEEDED);
            CraftTestSupport.assertFinished(helper, onDirt, dirtRun, RunStatus.SUCCEEDED);
            helper.assertTrue(
                    helper.getBlockState(GROUND).is(Blocks.TORCH),
                    "разовый факел на y0+1 свободного грунта обязан ставиться, а в клетке " + GROUND + " стоит "
                            + helper.getBlockState(GROUND) + ", причина: "
                            + onGround.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(OCCUPIED).is(Blocks.DIRT),
                    "занятая клетка не расчищается под разовый факел: земля обязана остаться, а в " + OCCUPIED
                            + " стоит " + helper.getBlockState(OCCUPIED) + ", причина: "
                            + onDirt.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(OCCUPIED.above()).isAir(),
                    "пропущенный факел не переносится вверх: сверху обязан остаться воздух");
            cleanup(helper, List.of(onGround, onDirt), store);
        });
    }

    @GameTest(template = "mine_arena", batch = "scene_torch_fence", timeoutTicks = 400)
    public static void placeBlockAdHocTorch_FenceAndStreet_ShiftedAndSkipped(GameTestHelper helper) {
        SettlementPlotStore store = prepare(helper);
        helper.setBlock(CENTER, Blocks.OAK_FENCE);
        SpiritEntity atFence = spawnWithTorches(helper, new BlockPos(5, 1, 4));
        SpiritEntity atStreet = spawnWithTorches(helper, new BlockPos(4, 1, 1));
        SpiritRun fenceRun = SpiritRun.launch(helper, atFence, torchCall(helper, CENTER), 1);
        SpiritRun streetRun = SpiritRun.launch(helper, atStreet, torchCall(helper, STREET), 1);
        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, atFence, fenceRun, RunStatus.SUCCEEDED);
            CraftTestSupport.assertFinished(helper, atStreet, streetRun, RunStatus.SUCCEEDED);
            helper.assertTrue(
                    helper.getBlockState(CENTER.above()).is(Blocks.TORCH),
                    "заявленный на y0+1 факел при заборе в клетке обязан переехать на верх забора y0+2, а в "
                            + CENTER.above() + " стоит " + helper.getBlockState(CENTER.above()) + ", причина: "
                            + atFence.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(CENTER).is(Blocks.OAK_FENCE),
                    "забор под факелом обязан остаться: факел ставится на него, а не вместо него");
            helper.assertTrue(
                    helper.getBlockState(STREET).isAir(),
                    "разовый факел в клетку улицы не ставится: свет улицы задаёт схема, а в клетке " + STREET
                            + " стоит " + helper.getBlockState(STREET));
            cleanup(helper, List.of(atFence, atStreet), store);
        });
    }

    @GameTest(template = "mine_arena", batch = "scene_torch_below", timeoutTicks = 400)
    public static void placeBlockAdHocTorch_BelowLevel_Failed(GameTestHelper helper) {
        SettlementPlotStore store = prepare(helper);
        SpiritEntity spirit = spawnWithTorches(helper, new BlockPos(6, 1, 4));
        BlockPos below = GROUND.below();
        SpiritRun run = SpiritRun.launch(helper, spirit, torchCall(helper, below), 1);
        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.FAILED);
            helper.assertTrue(
                    helper.getBlockState(GROUND).isAir(),
                    "факел ниже y0+1 обязан быть отвергнут без постановки: над грунтом не появилось света");
            cleanup(helper, List.of(spirit), store);
        });
    }

    private static void cleanup(GameTestHelper helper, List<SpiritEntity> spirits, SettlementPlotStore store) {
        GameTestFixtures.finish(helper, 16);
        spirits.forEach(SpiritEntity::discard);
        store.clear();
    }

    private static SpiritEntity spawnWithTorches(GameTestHelper helper, BlockPos at) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), at);
        spirit.getInventory().addItem(new ItemStack(Items.TORCH, 8));
        return spirit;
    }

    private static SettlementPlotStore prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SettlementPlotStore store = SettlementPlotStore.get(helper.getLevel());
        store.clear();
        int walk = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        TownPlan plan = plan(helper, walk);
        store.fixTown(plan);
        store.settle(new SettlementPlot(
                new BlockCoord(plan.anchor().x(), walk, plan.anchor().z()), walk - 2, walk + 2, 9, "oak"));
        return store;
    }

    private static ScriptCall torchCall(GameTestHelper helper, BlockPos cell) {
        BlockPos at = helper.absolutePos(cell);
        return new ScriptCall(
                "place_block",
                Map.of(
                        "block", "minecraft:torch",
                        "x", at.getX(),
                        "y", at.getY(),
                        "z", at.getZ()));
    }

    private static TownPlan plan(GameTestHelper helper, int level) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(new BlockPos(10, 0, 10));
        int x1 = Math.min(min.getX(), max.getX());
        int z1 = Math.min(min.getZ(), max.getZ());
        int x2 = Math.max(min.getX(), max.getX());
        int z2 = Math.max(min.getZ(), max.getZ());
        ColumnMask empty = new ColumnMask(x1, z1, x2 - x1 + 1, z2 - z1 + 1, new BitSet());
        List<Placement> places = List.of(
                new Placement("hut", "дом", 1, x1 + 1, z1 + 1, 3, 3, Placement.Facing.NORTH),
                new Placement("yard", "двор", 1, x1 + 5, z1 + 1, 3, 3, Placement.Facing.NORTH),
                new Placement("mine", "шахта", 1, x1 + 1, z1 + 5, 3, 3, Placement.Facing.NORTH),
                new Placement("orchard", "сад", 1, x1 + 5, z1 + 5, 3, 3, Placement.Facing.NORTH));
        return new TownPlan(
                new BlockCoord(x1 + 5, level, z1 + 5),
                ColumnMask.rect(x1, z1, x2, z2),
                new TownStreets(ColumnMask.rect(x1, z1, x2, z1), empty),
                places,
                new FenceLine(List.of(), List.of()),
                List.of());
    }
}

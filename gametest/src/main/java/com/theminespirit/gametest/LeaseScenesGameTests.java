package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.ReachApproach;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.lease.LeaseData;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LeaseScenesGameTests {
    private LeaseScenesGameTests() {}

    @GameTest(template = "big_arena", batch = "scene_lease_pillar", timeoutTicks = 400)
    public static void leaseScene_TallPillarWithoutStand_ClimbsAndRollsBackTopDown(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 24);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        int height = 20;
        BlockPos base = new BlockPos(5, 1, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, 1, 8));
        spirit.getInventory().addItem(new ItemStack(Items.DIRT, height + 8));
        spirit.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        LeaseRun abandoned = LeaseRun.open(spirit, "scaffold");
        for (int dy = 0; dy < height; dy++) {
            BlockPos cell = base.above(dy);
            helper.setBlock(cell, Blocks.DIRT);
            abandoned.placed(helper.absolutePos(cell), "minecraft:dirt");
        }
        abandoned.orphan();
        assertOneProbePerTick(helper);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("undo_lease", Map.of("id", abandoned.id())), 1);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            for (int dy = 0; dy < height; dy++) {
                helper.assertTrue(
                        helper.getBlockState(base.above(dy)).isAir(),
                        "брошенный столб без стоянки разбирается сверху вниз до земли: в " + base.above(dy)
                                + " остался " + helper.getBlockState(base.above(dy)) + ", дух на "
                                + spirit.blockPosition());
            }
            Lease lease = LeaseData.get(helper.getLevel()).book().require(abandoned.id());
            helper.assertTrue(
                    lease.status() == LeaseStatus.DONE,
                    "после разбора брошенная аренда закрывается DONE: " + lease.status() + ", клеток "
                            + lease.cells().size());
            List<Lease> scaffolds = LeaseData.get(helper.getLevel()).book().all().stream()
                    .filter(other -> "scaffold".equals(other.purpose())
                            && other.ownedBy(spirit.getUUID())
                            && !other.id().equals(abandoned.id()))
                    .toList();
            helper.assertTrue(
                    scaffolds.stream().allMatch(other -> other.status() == LeaseStatus.DONE),
                    "свои подмости для подъёма откатываются вместе с работой, а не бросаются: " + scaffolds);
            helper.assertTrue(
                    spirit.getY() <= 2.5,
                    "дух обязан спуститься по своим подмостям на землю: стоит на " + spirit.blockPosition());
            spirit.discard();
            GameTestFixtures.finish(helper, 24);
        });
    }

    private static void assertOneProbePerTick(GameTestHelper helper) {
        BlockPos sealed = new BlockPos(2, 1, 2);
        SpiritEntity probe = helper.spawn(ModEntities.SPIRIT.get(), sealed);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(sealed.relative(direction), Blocks.STONE);
        }
        helper.setBlock(sealed.above(), Blocks.STONE);
        helper.setBlock(sealed.above(2), Blocks.STONE);
        ReachApproach approach = new ReachApproach(probe);
        approach.begin(helper.absolutePos(new BlockPos(9, 1, 9)));
        int total = 0;
        int walks = approach.walks();
        for (int tick = 0; tick < 4; tick++) {
            int before = approach.probes();
            Approach.State state = approach.tick();
            int delta = approach.probes() - before;
            helper.assertTrue(
                    delta <= 1,
                    "Один тик подхода к блоку проверяет не больше одной стоянки: иначе 8 дорогих поисков пути "
                            + "встают в тик (сервер 0.1.3614: UndoLeaseScript держал поток 1,5 с), проверено "
                            + delta);
            total += delta;
            if (state == Approach.State.STUCK) {
                break;
            }
        }
        helper.assertTrue(
                approach.walks() - walks <= 1,
                "Обход достижимых клеток считается один раз на подход, а не заново для каждой стоянки: "
                        + (approach.walks() - walks) + " обходов");
        helper.assertTrue(total > 0, "Предусловие: замурованный дух обязан проверить хотя бы одну стоянку");
        probe.discard();
    }

    @GameTest(template = "big_arena", batch = "scene_lease_dig", timeoutTicks = 400)
    public static void leaseScene_BuriedBrokenCellUnderStone_DigsInAndFillsTunnelBack(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 24);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos low = new BlockPos(3, 1, 3);
        BlockPos high = new BlockPos(7, 5, 7);
        for (int x = low.getX(); x <= high.getX(); x++) {
            for (int y = low.getY(); y <= high.getY(); y++) {
                for (int z = low.getZ(); z <= high.getZ(); z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        BlockPos target = new BlockPos(5, 3, 5);
        helper.setBlock(target, Blocks.AIR);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        LeaseRun abandoned = LeaseRun.open(spirit, "dig_out");
        BlockPos at = helper.absolutePos(target);
        abandoned.broken(at, Blocks.STONE.defaultBlockState());
        abandoned.orphan();
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("undo_lease", Map.of("id", abandoned.id())), 1);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            helper.assertTrue(
                    helper.getBlockState(target).is(Blocks.STONE),
                    "сломанное под поселением возвращается на место: в " + target + " сейчас "
                            + helper.getBlockState(target));
            for (int x = low.getX(); x <= high.getX(); x++) {
                for (int y = low.getY(); y <= high.getY(); y++) {
                    for (int z = low.getZ(); z <= high.getZ(); z++) {
                        BlockPos cell = new BlockPos(x, y, z);
                        helper.assertTrue(
                                helper.getBlockState(cell).is(Blocks.STONE),
                                "проход копкой закладывается за спиной: в " + cell + " остался воздух, "
                                        + "мир не вернулся как был");
                    }
                }
            }
            List<Lease> approaches = LeaseData.get(helper.getLevel()).book().all().stream()
                    .filter(other -> "undo_approach".equals(other.purpose()) && other.ownedBy(spirit.getUUID()))
                    .toList();
            helper.assertTrue(
                    approaches.stream().allMatch(other -> other.status() == LeaseStatus.DONE),
                    "аренда подхода копкой закрывается DONE вместе с откатом: " + approaches);
            spirit.discard();
            GameTestFixtures.finish(helper, 24);
        });
    }

    @GameTest(template = "arena", batch = "scene_lease_own_pillar", timeoutTicks = 400)
    public static void leaseScene_PillarUpThenDown_LeavesNoAbandonedScaffoldLease(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().addItem(new ItemStack(Items.DIRT, 8));
        ScriptCall up = new ScriptCall("pillar_up", Map.of("height", 5, "block", "minecraft:dirt", "down", false));
        ScriptCall down = new ScriptCall("pillar_down", Map.of());
        SpiritRun run = SpiritRun.launch(helper, spirit, new SequenceNode(List.of(up, down)), 1);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            for (int dy = 0; dy < 5; dy++) {
                BlockPos cell = new BlockPos(4, 1 + dy, 4);
                helper.assertTrue(
                        helper.getBlockState(cell).isAir(),
                        "разбор столба убирает свои клетки: в " + cell + " остался " + helper.getBlockState(cell));
            }
            List<Lease> scaffolds = LeaseData.get(helper.getLevel()).book().all().stream()
                    .filter(lease -> "scaffold".equals(lease.purpose()) && lease.ownedBy(spirit.getUUID()))
                    .toList();
            helper.assertTrue(
                    scaffolds.stream().allMatch(lease -> lease.status() == LeaseStatus.DONE),
                    "разобранный своими руками столб не остаётся брошенной арендой: " + scaffolds);
            spirit.discard();
            GameTestFixtures.finish(helper);
        });
    }
}

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
    public static void leaseScene_OwnPillar_ClimbsAndTearsDownTopDown(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 24);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity pillar = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 12));
        pillar.getInventory().addItem(new ItemStack(Items.DIRT, 8));
        ScriptCall up = new ScriptCall("pillar_up", Map.of("height", 5, "block", "minecraft:dirt", "down", false));
        ScriptCall down = new ScriptCall("pillar_down", Map.of());
        SpiritRun pillarRun = SpiritRun.launch(helper, pillar, new SequenceNode(List.of(up, down)), 1);
        assertOneProbePerTick(helper);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, pillar, pillarRun, RunStatus.SUCCEEDED);
            for (int dy = 0; dy < 5; dy++) {
                BlockPos cell = new BlockPos(1, 1 + dy, 12);
                helper.assertTrue(
                        helper.getBlockState(cell).isAir(),
                        "разбор своего столба убирает его клетки: в " + cell + " остался "
                                + helper.getBlockState(cell));
            }
            List<Lease> ownPillars = LeaseData.get(helper.getLevel()).book().all().stream()
                    .filter(lease -> "scaffold".equals(lease.purpose()) && lease.ownedBy(pillar.getUUID()))
                    .toList();
            helper.assertTrue(
                    ownPillars.stream().allMatch(lease -> lease.status() == LeaseStatus.DONE),
                    "разобранный своими руками столб не остаётся брошенной арендой: " + ownPillars);
            helper.assertTrue(
                    pillar.getY() <= 2.5, "после разбора своего столба дух стоит на земле: " + pillar.blockPosition());
            pillar.discard();
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
                            + "встают в тик (сервер 0.1.3614: подход держал поток 1,5 с), проверено "
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
}

package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GraphHarvestGameTests {
    private static final String FIELD = "arena";
    private static final int PLATFORM = 11;
    private static final int SETTLE_TICKS = 1;

    private GraphHarvestGameTests() {}

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void graph_OakChopped_DropsCollected(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
        buildOakTree(helper, 4, 4, 4);
        helper.setBlock(2, 1, 2, Blocks.OAK_LOG);
        helper.setBlock(2, 2, 2, Blocks.OAK_LOG);

        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        GameTestFixtures.dropAt(helper, Items.OAK_SAPLING, 1, 5.5, 1, 5.5);
        GameTestFixtures.dropAt(helper, Items.APPLE, 1, 5.5, 1, 6.5);
        helper.assertTrue(
                worldLogs(helper) >= 6,
                "сцена обязана начаться с шести дубовых брёвен (ствол и столб), в мире " + worldLogs(helper));

        TaskNode chain = new SequenceNode(List.of(
                new ScriptCall("strip_log", Map.of("species", "oak", "count", 1, "radius", 16)),
                new ScriptCall("collect_drops", Map.of("radius", 16, "filter", List.of("stripped_oak_log"))),
                new ScriptCall("chop_tree", Map.of("species", "oak", "count", 1, "radius", 8, "replant", false)),
                new ScriptCall("collect_drops", Map.of("radius", 16, "filter", List.of("oak_sapling", "apple")))));
        SpiritRun run = SpiritRun.launch(helper, spirit, chain, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "рубка дуба, подбор саженца с яблоком и обтёсывание бревна обязаны пройти: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет") + ", брёвен в мире " + worldLogs(helper)
                            + ", обтёсанных в мире " + worldBlocks(helper, Blocks.STRIPPED_OAK_LOG));
            int saplings = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_SAPLING);
            int apples = GameTestFixtures.countInInventory(spirit.getInventory(), Items.APPLE);
            int stripped = GameTestFixtures.countInInventory(spirit.getInventory(), Items.STRIPPED_OAK_LOG);
            helper.assertTrue(saplings >= 1, "саженец дуба обязан быть подобран, в инвентаре " + saplings);
            helper.assertTrue(apples >= 1, "яблоко обязано быть подобрано, в инвентаре " + apples);
            helper.assertTrue(stripped >= 1, "обтёсанное бревно обязано быть в инвентаре, там " + stripped);

            GameTestFixtures.finish(helper);
        });
    }

    private static int worldLogs(GameTestHelper helper) {
        return worldBlocks(helper, Blocks.OAK_LOG);
    }

    private static int worldBlocks(GameTestHelper helper, Block block) {
        int count = 0;
        for (int x = 0; x < PLATFORM; x++) {
            for (int y = 0; y <= 8; y++) {
                for (int z = 0; z < PLATFORM; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void buildOakTree(GameTestHelper helper, int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            helper.setBlock(x, y, z, Blocks.OAK_LOG);
        }
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        helper.setBlock(x, height + 1, z, leaves);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                helper.setBlock(x + dx, height, z + dz, leaves);
                helper.setBlock(x + dx, height + 1, z + dz, leaves);
            }
        }
    }
}

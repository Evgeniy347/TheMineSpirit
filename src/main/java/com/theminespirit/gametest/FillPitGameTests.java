package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
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

/**
 * Подсыпка ямы участка (INC-66): яма 3×3 глубиной 4 засыпается слоями снизу вверх, дух в яме засыпает вокруг себя и
 * поднимается столбом под себя, в конце наверху, а не «без стоянки» и не застрял в яме.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FillPitGameTests {
    private static final int GROUND = 4;
    private static final int PIT_FROM = 4;
    private static final int PIT_TO = 6;
    private static final int SETTLE_TICKS = 5;

    private FillPitGameTests() {}

    @GameTest(template = "arena", batch = "fill_pit_inside", timeoutTicks = 400)
    public static void fill_SpiritAtPitBottom_FillsUnderItselfAndClimbs(GameTestHelper helper) {
        run(helper, new BlockPos(5, 1, 5));
    }

    private static void run(GameTestHelper helper, BlockPos start) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                boolean pit = x >= PIT_FROM && x <= PIT_TO && z >= PIT_FROM && z <= PIT_TO;
                for (int y = 1; y <= GROUND && !pit; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), start);
        spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, 64));
        // Слои снизу вверх, как подсыпку раскладывает TicketPlans.layers (ряды одного слоя слиты в ящик).
        List<TaskNode> rows = new ArrayList<>();
        for (int y = 1; y <= GROUND; y++) {
            BlockPos from = helper.absolutePos(new BlockPos(PIT_FROM, y, PIT_FROM));
            BlockPos to = helper.absolutePos(new BlockPos(PIT_TO, y, PIT_TO));
            rows.add(new ScriptCall(
                    "fill",
                    Map.of(
                            "block",
                            "dirt",
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
                            to.getZ())));
        }
        SpiritRun run = SpiritRun.launch(helper, spirit, new SequenceNode(rows), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "подсыпка ямы обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int filled = 0;
            for (int x = PIT_FROM; x <= PIT_TO; x++) {
                for (int z = PIT_FROM; z <= PIT_TO; z++) {
                    for (int y = 1; y <= GROUND; y++) {
                        filled += helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT) ? 1 : 0;
                    }
                }
            }
            helper.assertTrue(filled == 36, "яма 3×3×4 засыпана целиком: поставлено " + filled + " из 36");
            helper.assertTrue(
                    helper.relativePos(spirit.blockPosition()).getY() > GROUND,
                    "дух наверху, а не в яме: стоит на y "
                            + helper.relativePos(spirit.blockPosition()).getY());
            GameTestFixtures.finish(helper);
        });
    }
}

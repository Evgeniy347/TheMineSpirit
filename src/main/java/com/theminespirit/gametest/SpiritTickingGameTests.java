package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritTickingGameTests {
    private static final BlockPos EDGE_START = new BlockPos(10, 1, 5);
    private static final int DISTANCE_BEYOND_STRUCTURE = 24;
    private static final int PATH_HALF_WIDTH = 1;

    private SpiritTickingGameTests() {
    }

    // [флак] дух идёт за габариты структуры, ждём тики духа и сдвиг чанков
    @GameTest(template = "arena", batch = "ticking_beyond", timeoutTicks = 600)
    public static void spirit_GotoBeyondStructureChunks_KeepsTickingAndArrives(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        int targetX = EDGE_START.getX() + DISTANCE_BEYOND_STRUCTURE;
        for (int x = 0; x <= targetX + 2; x++) {
            for (int z = EDGE_START.getZ() - PATH_HALF_WIDTH; z <= EDGE_START.getZ() + PATH_HALF_WIDTH; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), EDGE_START);
        BlockPos target = helper.absolutePos(new BlockPos(targetX, 1, EDGE_START.getZ()));
        Vec3 start = helper.absoluteVec(Vec3.atBottomCenterOf(EDGE_START));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("goto", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ())), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дух, ушедший за габариты структуры, обязан продолжать тикать и дойти: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", тиков духа " + run.spiritTicks()
                            + ", ушёл на " + spirit.position().distanceTo(start));
            helper.assertTrue(spirit.position().distanceTo(start) >= DISTANCE_BEYOND_STRUCTURE - 2,
                    "дух обязан оказаться за пределами площадки, ушёл на " + spirit.position().distanceTo(start));
            GameTestFixtures.reset(helper);
        });
    }
}

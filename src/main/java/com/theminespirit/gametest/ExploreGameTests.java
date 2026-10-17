package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExploreGameTests {
    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final BlockPos ORE = new BlockPos(6, 1, 4);
    private static final int RADIUS = 2;
    private static final int BUDGET = 12;
    private static final double RETURN_TOLERANCE = 1.5;
    private static final long NIGHT = 13000L;
    private static final BlockPos HOME = new BlockPos(-30, 1, 1);

    private ExploreGameTests() {
    }

    @GameTest(template = "arena", batch = "explore_survey", timeoutTicks = 400)
    public static void explore_IronOreInRadius_RemembersResourceAndReturnsToStart(GameTestHelper helper) {
        MovementScene.prepare(helper);
        helper.setBlock(ORE, Blocks.IRON_ORE);
        SpiritEntity spirit = MovementScene.spirit(helper, CENTER);
        BlockCoord ore = MovementScene.coord(helper, ORE);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("explore", Map.of("radius", RADIUS, "budget", BUDGET)),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "explore обязан обойти маршрут и вернуться: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(spirit.memory().recallAll(MemoryType.RESOURCE).stream()
                            .anyMatch(point -> point.pos().equals(ore)),
                    "руда в радиусе обхода обязана попасть в память ресурсов: точка " + ore + ", память "
                            + spirit.memory().recallAll(MemoryType.RESOURCE));
            double distance = MovementScene.distanceTo(helper, spirit, CENTER);
            helper.assertTrue(distance <= RETURN_TOLERANCE,
                    "разведка обязана закончиться у точки старта, а дух в " + distance + " блоках от неё");
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "explore_night", timeoutTicks = 100)
    public static void explore_NightRouteOutsideSettlement_FailsBeforeLeaving(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(1, 1, 1));
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("explore", Map.of("radius", 3, "budget", 4)), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "ночью разведка за границу поселения обязана быть отвергнута, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + "; "
                            + MovementScene.facts(helper, spirit, NIGHT));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("ночь: внешняя работа не назначается"),
                    "причина обязана называть ночной запрет, фактически: " + reason);
            double distance = MovementScene.distanceTo(helper, spirit, new BlockPos(1, 1, 1));
            helper.assertTrue(distance <= RETURN_TOLERANCE,
                    "отказанная разведка не двигает духа: расстояние от старта " + distance);
            MovementScene.finish(helper);
        });
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoordinatorScaleGameTests {
    private static final int SPIRITS = 20;
    private static final int SCALE_TICKS = 100;
    private static final int SCALE_DISTANCE = 2;
    private static final double RING_RADIUS = 2.5;

    private CoordinatorScaleGameTests() {
    }

    @GameTest(template = "arena", batch = "coordinator_scale", timeoutTicks = 300)
    public static void scale_TwentySpiritsHundredTicks_CoordinatorWithinQueryBudget(GameTestHelper helper) {
        MovementScene.prepare(helper);
        ServerPlayer player = MovementScene.mockPlayer(helper, new BlockPos(4, 1, 4));
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(4, 1, 4));
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int i = 0; i < SPIRITS; i++) {
            double angle = 2 * Math.PI * i / SPIRITS;
            SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new Vec3(4.5 + RING_RADIUS * Math.cos(angle),
                    1, 4.5 + RING_RADIUS * Math.sin(angle)));
            spirit.setHome(new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ()));
            spirits.add(spirit);
        }
        int[] startedAt = {-1};
        int[] baseQueries = {0};
        int[] basePlans = {0};
        helper.onEachTick(() -> {
            if (startedAt[0] < 0 && spirits.stream().allMatch(spirit -> spirit.tickCount >= MovementScene.SETTLE_TICKS)) {
                startedAt[0] = spirits.get(0).tickCount;
                SpiritCoordinator coordinator = settlementOf(helper, absoluteHome);
                baseQueries[0] = coordinator.entityQueries();
                basePlans[0] = coordinator.planCalls();
                spirits.forEach(spirit -> spirit.startTask(MovementScene.call("keep_distance",
                        Map.of("target", player.getName().getString(), "distance", SCALE_DISTANCE))));
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(startedAt[0] >= 0, "духи ещё не получили задачу");
            helper.assertTrue(spirits.get(0).tickCount >= startedAt[0] + SCALE_TICKS,
                    "наблюдение за " + SCALE_TICKS + " тиками ещё не закончилось");
            helper.assertTrue(CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS == 10,
                    "Снимок поселения — раз в 10 тиков: чаще бюджет не сойдётся, реже — координатор слеп");
            SpiritCoordinator coordinator = settlementOf(helper, absoluteHome);
            int periods = SCALE_TICKS / 10 + 1;
            int perPeriod = CoordinatorTuning.SNAPSHOT_GROUP_QUERIES + SPIRITS * CoordinatorTuning.SNAPSHOT_SPIRIT_QUERIES;
            helper.assertTrue(coordinator.entityQueries() - baseQueries[0] <= periods * perPeriod,
                    "Двадцать духов за " + SCALE_TICKS + " тиков: запросов координатора не больше " + periods
                            + " периодов по " + perPeriod + " (перепись + чтения), иначе снимок читает мир каждый тик");
            helper.assertTrue(coordinator.planCalls() - basePlans[0] <= periods,
                    "План поселения — раз за период: иначе координатор планирует чаще, чем видит мир");
            List<SpiritSnapshot> seen = coordinator.snapshot().spirits();
            helper.assertTrue(seen.size() == SPIRITS,
                    "Снимок поселения видит всех " + SPIRITS + " духов: иначе координатор командует вслепую");
            for (SpiritEntity spirit : spirits) {
                helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "keep_distance работает все " + SCALE_TICKS + " тиков: упавший скрипт не ходит, "
                                + "и счёт запросов ничего бы не доказывал");
            }
            MovementScene.removePlayer(helper, player);
            MovementScene.finish(helper);
        });
    }

    private static SpiritCoordinator settlementOf(GameTestHelper helper, BlockPos absoluteHome) {
        return CoordinatorRegistry.get(helper.getLevel(), SettlementKey.homed(new BlockCoord(absoluteHome.getX(),
                absoluteHome.getY(), absoluteHome.getZ())));
    }
}

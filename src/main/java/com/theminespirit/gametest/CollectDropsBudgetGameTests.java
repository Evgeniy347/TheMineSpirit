package com.theminespirit.gametest;

import static com.theminespirit.gametest.CollectDropsSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.CollectDropsSupport.SPIRIT_START;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.neoforge.DropCollector;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CollectDropsBudgetGameTests {
    private CollectDropsBudgetGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void dropCollector_ThreeHeaps_QueriesEntitiesOncePerRefreshPeriod(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 1.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 7.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 4.5, 1, 7.5);
        BlockPos start = helper.absolutePos(SPIRIT_START);
        DropCollector collector = new DropCollector(
                spirit,
                IdFilter.matchAll(),
                new BlockCoord(start.getX(), start.getY(), start.getZ()),
                6,
                PickupTuning.DEFAULT,
                OverflowMode.FAIL);
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger lastSpiritTick = new AtomicInteger(SETTLE_TICKS - 1);
        AtomicReference<DropCollector.State> state = new AtomicReference<>(DropCollector.State.RUNNING);
        helper.onEachTick(() -> {
            if (state.get() == DropCollector.State.RUNNING
                    && spirit.tickCount >= SETTLE_TICKS
                    && spirit.tickCount != lastSpiritTick.get()) {
                lastSpiritTick.set(spirit.tickCount);
                ticks.incrementAndGet();
                state.set(collector.tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    state.get() == DropCollector.State.DONE,
                    "DropCollector обязан закончить успехом, состояние " + state.get() + ", причина: "
                            + collector.failureReason());
            helper.assertTrue(
                    collector.collected() == 9,
                    "коллектор обязан подобрать 9 предметов, подобрал " + collector.collected());
            int allowed = (ticks.get() + PickupTuning.REFRESH_TICKS - 1) / PickupTuning.REFRESH_TICKS + 1;
            helper.assertTrue(
                    collector.entityQueries() <= allowed,
                    "за " + ticks.get() + " тиков допустимо " + allowed + " запросов сущностей (раз в "
                            + PickupTuning.REFRESH_TICKS + " тиков и один финальный), а сделано "
                            + collector.entityQueries() + ": запрос каждый тик нагружает сервер");
            helper.assertTrue(
                    collector.entityQueries() >= 2,
                    "тест на масштаб бессмыслен без повторных опросов: запросов " + collector.entityQueries());
            GameTestFixtures.finish(helper);
        });
    }
}

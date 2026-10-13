package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdSession;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HusbandryScaleGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int OBSERVE_TICKS = 200;
    private static final int QUERY_SLACK = 12;
    private static final int RATE_SLACK = 3;
    private static final BlockPos START = new BlockPos(1, 1, 1);

    private HusbandryScaleGameTests() {
    }

    @GameTest(template = "arena", batch = "husbandry_scale", timeoutTicks = 400)
    public static void herdCheckAndFeed_TwelveAnimals_StaysWithinQueryAndTickBudgets(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 4));
        helper.spawn(EntityType.COW, new BlockPos(5, 1, 3));
        helper.spawn(EntityType.SHEEP, new BlockPos(2, 1, 5));
        helper.spawn(EntityType.SHEEP, new BlockPos(4, 1, 6));
        helper.spawn(EntityType.SHEEP, new BlockPos(6, 1, 2));
        helper.spawn(EntityType.PIG, new BlockPos(3, 1, 3));
        helper.spawn(EntityType.PIG, new BlockPos(5, 1, 5));
        helper.spawn(EntityType.PIG, new BlockPos(6, 1, 4));
        HusbandryGameTests.spawnStill(helper, EntityType.CHICKEN, new BlockPos(2, 1, 6));
        HusbandryGameTests.spawnStill(helper, EntityType.CHICKEN, new BlockPos(4, 1, 2));
        HusbandryGameTests.spawnStill(helper, EntityType.CHICKEN, new BlockPos(6, 1, 6));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 8));
        AtomicReference<HerdSession> check = new AtomicReference<>();
        AtomicReference<HerdSession> feed = new AtomicReference<>();
        AtomicInteger startTick = new AtomicInteger(-1);
        AtomicInteger feedStartTick = new AtomicInteger(-1);
        AtomicInteger feedActiveTicks = new AtomicInteger(-1);

        helper.onEachTick(() -> {
            if (spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            if (startTick.get() < 0) {
                startTick.set(spirit.tickCount);
            }
            HerdSession herdCheck = check.get();
            if (herdCheck == null) {
                herdCheck = HerdSession.herdCheck(spirit, Species.all(), 8);
                check.set(herdCheck);
            }
            if (herdCheck.outcome() == HerdSession.Outcome.RUNNING) {
                herdCheck.tick();
                return;
            }
            HerdSession feeding = feed.get();
            if (feeding == null) {
                feeding = HerdSession.feed(spirit, Species.CHICKEN, 8, 3, true);
                feed.set(feeding);
                feedStartTick.set(spirit.tickCount);
            }
            if (feeding.outcome() == HerdSession.Outcome.RUNNING) {
                feeding.tick();
            } else if (feedActiveTicks.get() < 0) {
                feedActiveTicks.set(spirit.tickCount - feedStartTick.get());
            }
        });

        helper.succeedWhen(() -> {
            HerdSession herdCheck = check.get();
            HerdSession feeding = feed.get();
            helper.assertTrue(herdCheck != null
                    && herdCheck.outcome() == HerdSession.Outcome.SUCCEEDED,
                    "сводка по 12 головам обязана завершиться успехом: "
                            + (herdCheck == null ? "сессия не стартовала" : herdCheck.outcome()));
            helper.assertTrue(feeding != null && feeding.outcome() == HerdSession.Outcome.SUCCEEDED,
                    "кормление трёх кур обязано завершиться успехом: "
                            + (feeding == null ? "сессия не стартовала" : feeding.outcome()));
            helper.assertTrue(spirit.tickCount - startTick.get() >= OBSERVE_TICKS,
                    "наблюдение обязано длиться 200 тиков духа, прошло " + (spirit.tickCount - startTick.get()));
            int active = feedActiveTicks.get() >= 0 ? feedActiveTicks.get() : spirit.tickCount - feedStartTick.get();
            int queryBudget = 12 * (OBSERVE_TICKS / CatalogLimits.HERD_REFRESH_TICKS) + QUERY_SLACK;
            helper.assertTrue(feeding.entityQueries() <= queryBudget,
                    "запросов сущностей обязано быть не больше " + queryBudget + " (12 голов × 200 тиков / "
                            + CatalogLimits.HERD_REFRESH_TICKS + " тиков обновления + запас " + QUERY_SLACK
                            + "), фактически " + feeding.entityQueries());
            int rateBudget = active / CatalogLimits.HERD_REFRESH_TICKS + RATE_SLACK;
            helper.assertTrue(feeding.entityQueries() <= rateBudget,
                    "опрос стада идёт раз в " + CatalogLimits.HERD_REFRESH_TICKS + " тиков: за " + active
                            + " активных тиков запросов обязано быть не больше " + rateBudget + ", фактически "
                            + feeding.entityQueries());
            helper.assertTrue(feeding.planCalls() <= feeding.actionTicks(),
                    "вызовов планировщика ядра обязано быть не больше числа тиков действий ("
                            + feeding.actionTicks() + "), фактически " + feeding.planCalls());
            helper.assertTrue(HusbandryGameTests.alive(helper, Cow.class).size() == 3
                    && HusbandryGameTests.alive(helper, Sheep.class).size() == 3
                    && HusbandryGameTests.alive(helper, Pig.class).size() == 3
                    && HusbandryGameTests.alive(helper, Chicken.class).size() == 3,
                    "все 12 голов обязаны остаться целы: сводка и кормление не убивают");
            GameTestFixtures.reset(helper);
        });
    }
}

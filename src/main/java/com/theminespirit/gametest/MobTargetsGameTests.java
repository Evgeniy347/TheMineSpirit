package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobTargetsGameTests {
    private static final int OBSERVED_TICKS = 100;
    private static final long DAYLIGHT = 6000;
    private static final long NIGHT = 18000;
    private static final String DISABLED_BATCH = "mob_targets_disabled";
    private static final BlockPos SPIRIT_AT = new BlockPos(4, 1, 4);
    private static final BlockPos MOB_AT = new BlockPos(4, 1, 10);

    private MobTargetsGameTests() {
    }

    private static SpiritEntity spirit(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        return helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_AT);
    }

    @BeforeBatch(batch = DISABLED_BATCH)
    public static void disableSpiritTargeting(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = DISABLED_BATCH)
    public static void restoreSpiritTargeting(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = "mob_targets_zombie", timeoutTicks = 200)
    public static void zombie_SixBlocksFromSpiritWithoutSetTarget_TakesSpiritAsTarget(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, MOB_AT);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() <= OBSERVED_TICKS,
                    "зомби обязан взять духа целью за " + OBSERVED_TICKS + " тиков, прошло " + helper.getTick());
            helper.assertTrue(zombie.getTarget() == spirit,
                    "зомби в шести блоках от духа без игроков рядом обязан сам выбрать духа целью, а его цель "
                            + zombie.getTarget());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "mob_targets_zombified_piglin", timeoutTicks = 200)
    public static void zombifiedPiglin_SixBlocksFromSpirit_DoesNotTakeTarget(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, MOB_AT);

        helper.runAfterDelay(OBSERVED_TICKS, () -> {
            helper.assertTrue(piglin.getTarget() == null,
                    "зомбифицированный пиглин нейтрален и не трогает духов без зла, а его цель " + piglin.getTarget());
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = DISABLED_BATCH, timeoutTicks = 200)
    public static void zombie_WhenMobsTargetSpiritsDisabled_DoesNotTakeTarget(GameTestHelper helper) {
        SpiritEntity spirit = spirit(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, MOB_AT);

        helper.runAfterDelay(OBSERVED_TICKS, () -> {
            helper.assertTrue(zombie.getTarget() == null,
                    "при mobsTargetSpirits=false зомби не берёт духа целью, а его цель " + zombie.getTarget());
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = "mob_targets_spider_day", timeoutTicks = 400)
    public static void spider_NextToSpiritInDaylight_DoesNotTakeSpiritAsTarget(GameTestHelper helper) {
        long savedDayTime = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(DAYLIGHT);
        spirit(helper);
        AtomicReference<Spider> spider = new AtomicReference<>();
        helper.runAfterDelay(2, () -> spider.set(helper.spawn(EntityType.SPIDER, MOB_AT)));

        helper.runAfterDelay(OBSERVED_TICKS, () -> {
            helper.assertTrue(spider.get().getTarget() == null,
                    "Паук днём на открытом месте не должен брать духа целью: ванильный SpiderTargetGoal при свете "
                            + "≥ 0.5 цель не берёт, а его цель " + spider.get().getTarget());
            helper.getLevel().setDayTime(savedDayTime);
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = "mob_targets_spider_night", timeoutTicks = 400)
    public static void spider_NextToSpiritAtNight_TakesSpiritAsTarget(GameTestHelper helper) {
        long savedDayTime = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(NIGHT);
        SpiritEntity spirit = spirit(helper);
        AtomicReference<Spider> spider = new AtomicReference<>();
        helper.runAfterDelay(2, () -> spider.set(helper.spawn(EntityType.SPIDER, MOB_AT)));

        helper.succeedWhen(() -> {
            Spider current = spider.get();
            helper.assertTrue(current != null && current.getTarget() == spirit,
                    "Паук ночью рядом с духом обязан сам выбрать духа целью за " + OBSERVED_TICKS
                            + " тиков, а его цель " + (current == null ? "ещё не заспавнен" : current.getTarget()));
            helper.getLevel().setDayTime(savedDayTime);
            GameTestFixtures.reset(helper);
        });
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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

    private static String describeTarget(Entity target, Entity ownSpirit) {
        if (target == null) {
            return "null";
        }
        String who = target == ownSpirit ? "свой дух" : "чужая цель";
        return who + " " + target.getType().getDescriptionId() + " uuid=" + target.getUUID()
                + " pos=" + target.blockPosition().toShortString();
    }

    private static String nearby(GameTestHelper helper, Entity center) {
        ServerLevel level = helper.getLevel();
        return level.getEntitiesOfClass(Entity.class, center.getBoundingBox().inflate(16)).stream()
                .map(MobTargetsGameTests::describeNearby)
                .collect(Collectors.joining("; "));
    }

    private static String describeNearby(Entity entity) {
        String target = "нетцели";
        String hurtBy = "нет";
        if (entity instanceof Mob mob) {
            target = describeTarget(mob.getTarget(), null);
            hurtBy = String.valueOf(mob.getLastHurtByMob());
        }
        return entity.getType().getDescriptionId() + "@" + entity.blockPosition().toShortString()
                + " цель=" + target + " бил=" + hurtBy;
    }

    @SuppressWarnings("deprecation") // ванильный SpiderTargetGoal в 1.21.1 читает свет этим методом; замены нет
    private static String spiderLight(GameTestHelper helper, Spider spider) {
        ServerLevel level = helper.getLevel();
        return "свет_магия=" + spider.getLightLevelDependentMagicValue()
                + " сырой_свет=" + level.getMaxLocalRawBrightness(spider.blockPosition())
                + " день=" + level.isDay() + " dayTime=" + level.getDayTime();
    }

    private static String spiritUuids(GameTestHelper helper, Entity center) {
        ServerLevel level = helper.getLevel();
        return level.getEntitiesOfClass(SpiritEntity.class, center.getBoundingBox().inflate(16)).stream()
                .map(spirit -> spirit.getUUID() + "@" + spirit.blockPosition().toShortString())
                .collect(Collectors.joining("; "));
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
                    "зомбифицированный пиглин нейтрален и не трогает духов без зла, а его цель "
                            + describeTarget(piglin.getTarget(), spirit)
                            + "; конфиг mobsTargetSpirits=" + SpiritConfig.mobsTargetSpirits()
                            + "; рядом в 16 блоках: [" + nearby(helper, piglin) + "]");
            helper.assertTrue(spirit.getTarget() == null,
                    "дух не дерётся первым с мирным мобом, а его цель " + describeTarget(spirit.getTarget(), spirit));
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
                    "при mobsTargetSpirits=false зомби не берёт духа целью, а его цель "
                            + describeTarget(zombie.getTarget(), spirit)
                            + "; конфиг mobsTargetSpirits=" + SpiritConfig.mobsTargetSpirits()
                            + "; рядом в 16 блоках: [" + nearby(helper, zombie) + "]");
            helper.assertTrue(spirit.getTarget() == null,
                    "дух не дерётся первым, а его цель " + describeTarget(spirit.getTarget(), spirit));
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = "mob_targets_spider_day", timeoutTicks = 400)
    public static void spider_NextToSpiritInDaylight_DoesNotTakeSpiritAsTarget(GameTestHelper helper) {
        long savedDayTime = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(DAYLIGHT);
        SpiritEntity spirit = spirit(helper);
        AtomicReference<Spider> spider = new AtomicReference<>();
        helper.runAfterDelay(2, () -> spider.set(helper.spawn(EntityType.SPIDER, MOB_AT)));

        helper.runAfterDelay(OBSERVED_TICKS, () -> {
            Spider current = spider.get();
            helper.assertTrue(current.getTarget() == null,
                    "Паук днём на открытом месте не должен брать духа целью: ванильный SpiderTargetGoal при свете "
                            + "≥ 0.5 цель не берёт, а его цель "
                            + describeTarget(current.getTarget(), spirit)
                            + "; " + spiderLight(helper, current)
                            + "; дух свой uuid=" + spirit.getUUID() + " pos=" + spirit.blockPosition().toShortString()
                            + "; цель духа " + describeTarget(spirit.getTarget(), spirit)
                            + "; духи в радиусе 16: [" + spiritUuids(helper, current) + "]");
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

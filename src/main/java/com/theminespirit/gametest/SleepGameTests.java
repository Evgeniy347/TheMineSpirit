package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.sleep.SleepFacts;
import com.theminespirit.core.sleep.SleepRule;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleepGameTests {
    private static final BlockPos SPIRIT = new BlockPos(1, 1, 1);
    private static final BlockPos HOME = new BlockPos(1, 1, 1);
    private static final BlockPos BED = new BlockPos(3, 1, 1);
    private static final long NIGHT = 13000L;
    private static final long NOON = 6000L;

    private SleepGameTests() {}

    @GameTest(template = "arena", batch = "sleep_lie", timeoutTicks = 200)
    public static void sleep_NightAtBed_LiesDown(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        helper.setBlock(BED.getX(), BED.getY(), BED.getZ(), Blocks.RED_BED);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, MovementScene.call("sleep", Map.of()), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.isSleeping(),
                    "Ночью у кровати дух обязан лечь спать, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "; " + facts(helper, spirit));
            BlockPos sleeping = spirit.getSleepingPos().orElse(null);
            helper.assertTrue(
                    sleeping != null && sleeping.equals(helper.absolutePos(BED)),
                    "Дух обязан спать именно в своей кровати " + helper.absolutePos(BED) + ", а спит в " + sleeping);
            helper.assertTrue(spirit.bed().isPresent(), " Своя кровать обязана запомниться после сна");
            helper.assertTrue(
                    SleepRule.isNight(SleepRule.dayFraction(helper.getLevel().getDayTime())),
                    "Духи — не игроки: сон духа не пропускает ночь, время должно оставаться ночным, а стало "
                            + helper.getLevel().getDayTime());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "sleep_wake", timeoutTicks = 300)
    public static void sleep_Morning_WakesUp(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        helper.setBlock(BED.getX(), BED.getY(), BED.getZ(), Blocks.RED_BED);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, MovementScene.call("sleep", Map.of()), MovementScene.SETTLE_TICKS);
        AtomicBoolean slept = new AtomicBoolean(false);
        AtomicBoolean dawn = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            if (spirit.isSleeping()) {
                slept.set(true);
                if (dawn.compareAndSet(false, true)) {
                    GameTestFixtures.holdDayTime(helper, NOON);
                }
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(slept.get(), "Дух обязан сперва заснуть, прежде чем вставать; " + facts(helper, spirit));
            helper.assertTrue(
                    !spirit.isSleeping(),
                    "Утром дух обязан встать из кровати, а он ещё спит; " + facts(helper, spirit));
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Задача сна обязана завершиться успехом после пробуждения, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "sleep_hurt", timeoutTicks = 300)
    public static void sleep_Attacked_WakesUp(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        helper.setBlock(BED.getX(), BED.getY(), BED.getZ(), Blocks.RED_BED);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, MovementScene.call("sleep", Map.of()), MovementScene.SETTLE_TICKS);
        AtomicBoolean hurt = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            if (spirit.isSleeping() && !hurt.getAndSet(true)) {
                spirit.hurt(spirit.damageSources().generic(), 1.0F);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(hurt.get(), "Дух обязан сперва заснуть, прежде чем его ударят; " + facts(helper, spirit));
            helper.assertTrue(
                    !spirit.isSleeping(), "Атакованный дух обязан проснуться, а он ещё спит; " + facts(helper, spirit));
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "sleep_two", timeoutTicks = 300)
    public static void sleep_TwoBedsTwoSpirits_SleepApart(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity first = MovementScene.spirit(helper, new BlockPos(1, 1, 1));
        SpiritEntity second = MovementScene.spirit(helper, new BlockPos(7, 1, 7));
        first.setHome(MovementScene.coord(helper, new BlockPos(4, 1, 4)));
        second.setHome(MovementScene.coord(helper, new BlockPos(4, 1, 4)));
        BlockPos firstBed = new BlockPos(2, 1, 1);
        BlockPos secondBed = new BlockPos(6, 1, 7);
        helper.setBlock(firstBed.getX(), firstBed.getY(), firstBed.getZ(), Blocks.RED_BED);
        helper.setBlock(secondBed.getX(), secondBed.getY(), secondBed.getZ(), Blocks.BLUE_BED);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun firstRun =
                SpiritRun.launch(helper, first, MovementScene.call("sleep", Map.of()), MovementScene.SETTLE_TICKS);
        SpiritRun secondRun =
                SpiritRun.launch(helper, second, MovementScene.call("sleep", Map.of()), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            firstRun.assertStarted();
            secondRun.assertStarted();
            helper.assertTrue(
                    first.isSleeping() && second.isSleeping(),
                    "Оба духа обязаны уснуть; первый "
                            + first.taskStatus().map(RunStatus::name).orElse("нет") + ", второй "
                            + second.taskStatus().map(RunStatus::name).orElse("нет"));
            BlockPos firstPos = first.getSleepingPos().orElse(null);
            BlockPos secondPos = second.getSleepingPos().orElse(null);
            helper.assertTrue(
                    firstPos != null && secondPos != null && !firstPos.equals(secondPos),
                    "Двое духов обязаны спать в разных кроватях, а спят в " + firstPos + " и " + secondPos);
            MovementScene.finish(helper);
        });
    }

    private static String facts(GameTestHelper helper, SpiritEntity spirit) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord cell = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        NightLeash leash = NightLeash.withDefaultRules();
        boolean inside = spirit.home()
                .map(home -> leash.insideSettlement(cell, home, leash.settlementRadius()))
                .orElse(false);
        int enemies = helper.getLevel()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        spirit.getBoundingBox().inflate(16.0),
                        entity -> !entity.equals(spirit) && entity.isAlive() && entity instanceof Enemy)
                .size();
        boolean night =
                SleepRule.isNight(SleepRule.dayFraction(helper.getLevel().getDayTime()));
        boolean defence = spirit.isDefenceAssigned();
        SleepFacts sleepFacts = new SleepFacts(night, spirit.hurtTime > 0 || enemies > 0, inside, defence);
        return "время " + helper.getLevel().getDayTime() + " ("
                + SleepRule.dayFraction(helper.getLevel().getDayTime())
                + "), ночь=" + night + ", враги=" + enemies + ", hurtTime=" + spirit.hurtTime + ", в поселении="
                + inside + ", оборона=" + defence + ", тактика="
                + spirit.tactics().combatMode() + ", контроль="
                + spirit.tactics().wantsControl() + ", спать=" + SleepRule.timeToSleep(sleepFacts)
                + ", статус=" + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина="
                + spirit.taskFailureReason().orElse("нет") + ", спит=" + spirit.isSleeping() + ", кровать="
                + spirit.bed().map(Object::toString).orElse("нет")
                + ", дом=" + spirit.home().map(Object::toString).orElse("нет");
    }
}

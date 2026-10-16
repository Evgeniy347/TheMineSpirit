package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.base.Camp;
import com.theminespirit.core.base.CampTuning;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritCampStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CampGameTests {
    private static final int FUTURE_VERSION = 99;
    private static final int ARRIVE_LIMIT_TICKS = 100;

    private CampGameTests() {
    }

    private static Camp campAt(GameTestHelper helper, BlockPos relative, double radius) {
        BlockPos absolute = helper.absolutePos(relative);
        return new Camp(new Point(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5),
                radius, 0, helper.getLevel().getGameTime());
    }

    private static void giveHome(GameTestHelper helper, SpiritEntity spirit, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        spirit.updateMemory(spirit.memory().remember(new MemoryPoint("home", MemoryType.BASE,
                helper.getLevel().dimension().location().toString(),
                new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()))));
    }

    @GameTest(template = "arena", batch = "camp_persist")
    public static void camp_SavedAndLoaded_SurvivesRoundTripAndRejectsFutureVersion(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        SpiritEntity saved = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        CompoundTag clean = new CompoundTag();
        saved.addAdditionalSaveData(clean);
        helper.assertTrue(!clean.contains(SpiritCampStore.KEY),
                "у духа без лагеря в NBT нет ключа " + SpiritCampStore.KEY + ": иначе он помнит лагерь, которого нет");

        Camp camp = new Camp(new Point(-12.5, 64, 345.5), 8, 0, 1234);
        saved.setCamp(camp);
        CompoundTag tag = new CompoundTag();
        saved.addAdditionalSaveData(tag);
        SpiritEntity loaded = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        loaded.readAdditionalSaveData(tag);
        helper.assertTrue(camp.equals(loaded.camp().orElse(null)),
                "лагерь обязан пережить сохранение и загрузку: записан " + camp + ", прочитан " + loaded.camp());

        tag.getCompound(SpiritCampStore.KEY).putInt("Version", FUTURE_VERSION);
        SpiritEntity future = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 1));
        future.readAdditionalSaveData(tag);
        helper.assertTrue(future.camp().isEmpty(),
                "лагерь чужой версии не читается молча: дух остаётся без лагеря, а получил " + future.camp());

        saved.clearCamp();
        CompoundTag cleared = new CompoundTag();
        saved.addAdditionalSaveData(cleared);
        helper.assertTrue(!cleared.contains(SpiritCampStore.KEY), "снятый лагерь обязан пропасть из NBT");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "camp_retreat_camp", timeoutTicks = 200)
    public static void retreat_CampNearerThanHome_RunsToCamp(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(5, 1, 5));
        giveHome(helper, spirit, new BlockPos(1, 1, 5));
        spirit.setCamp(campAt(helper, new BlockPos(8, 1, 5), CampTuning.CAMP_RADIUS));
        BlockPos camp = new BlockPos(8, 1, 5);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("wait", Map.of("ticks", 6000)),
                MovementScene.SETTLE_TICKS);
        int[] orderedAt = {-1};
        helper.onEachTick(() -> {
            if (orderedAt[0] < 0 && run.started()) {
                orderedAt[0] = spirit.tickCount;
                spirit.tactics().applyDirective(new CombatDirective(CombatMode.PASSIVE, Optional.empty(),
                        Optional.of(RetreatMethod.AWAY), Optional.empty()));
                spirit.tactics().setForcedRetreat(RetreatMethod.HOME, Optional.empty());
            }
            if (orderedAt[0] >= 0 && MovementScene.distanceTo(helper, spirit, camp) > 2.0
                    && spirit.tickCount - orderedAt[0] > ARRIVE_LIMIT_TICKS) {
                helper.fail("дух не дошёл до лагеря за " + ARRIVE_LIMIT_TICKS + " тиков духа, расстояние "
                        + MovementScene.distanceTo(helper, spirit, camp) + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(orderedAt[0] >= 0, "приказ отхода ещё не отдан: дух не тикает");
            helper.assertTrue(MovementScene.distanceTo(helper, spirit, camp) <= 2.0,
                    "дух идёт к ближнему лагерю, а не к дальнему дому");
            helper.assertTrue(spirit.tactics().retreat().method() == RetreatMethod.HOME,
                    "исполняемый отход — HOME, получено " + spirit.tactics().retreat().method());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "camp_retreat_home", timeoutTicks = 200)
    public static void retreat_HomeNearerThanCamp_RunsHome(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(5, 1, 5));
        giveHome(helper, spirit, new BlockPos(8, 1, 5));
        spirit.setCamp(campAt(helper, new BlockPos(1, 1, 5), CampTuning.CAMP_RADIUS));
        BlockPos home = new BlockPos(8, 1, 5);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("wait", Map.of("ticks", 6000)),
                MovementScene.SETTLE_TICKS);
        int[] orderedAt = {-1};
        helper.onEachTick(() -> {
            if (orderedAt[0] < 0 && run.started()) {
                orderedAt[0] = spirit.tickCount;
                spirit.tactics().applyDirective(new CombatDirective(CombatMode.PASSIVE, Optional.empty(),
                        Optional.of(RetreatMethod.AWAY), Optional.empty()));
                spirit.tactics().setForcedRetreat(RetreatMethod.HOME, Optional.empty());
            }
            if (orderedAt[0] >= 0 && MovementScene.distanceTo(helper, spirit, home) > 2.0
                    && spirit.tickCount - orderedAt[0] > ARRIVE_LIMIT_TICKS) {
                helper.fail("дух не дошёл до ближнего дома за " + ARRIVE_LIMIT_TICKS + " тиков духа, расстояние "
                        + MovementScene.distanceTo(helper, spirit, home) + ", решение "
                        + spirit.tactics().explain().orElse("нет") + ", директива "
                        + spirit.tactics().activeDirective().map(Object::toString).orElse("нет") + ", память BASE "
                        + spirit.memory().recallAll(MemoryType.BASE) + ", лагерь " + spirit.camp()
                        + ", сбоев тактики " + spirit.tactics().failures() + " "
                        + spirit.tactics().lastFailure().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(orderedAt[0] >= 0, "приказ отхода ещё не отдан: дух не тикает");
            helper.assertTrue(MovementScene.distanceTo(helper, spirit, home) <= 2.0,
                    "дух идёт к ближнему дому, а не к дальнему лагерю");
            helper.assertTrue(spirit.tactics().retreat().method() == RetreatMethod.HOME,
                    "исполняемый отход — HOME, получено " + spirit.tactics().retreat().method());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "camp_retreat_script", timeoutTicks = 200)
    public static void retreatScript_CampNearerThanHome_RunsToCamp(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.setHome(MovementScene.coord(helper, new BlockPos(1, 1, 5)));
        spirit.setCamp(campAt(helper, new BlockPos(8, 1, 5), CampTuning.CAMP_RADIUS));
        BlockPos camp = new BlockPos(8, 1, 5);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("retreat", Map.of()),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "план retreat при ближнем лагере обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = MovementScene.distanceTo(helper, spirit, camp);
            helper.assertTrue(distance <= 2.0,
                    "план retreat идёт к ближнему лагерю, а не к дальнему дому: расстояние до лагеря " + distance);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "camp_retreat_none", timeoutTicks = 100)
    public static void retreat_NoHomeNoCamp_FailsWithNoRetreatPoint(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(1, 1, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("retreat", Map.of()),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "отход без дома и лагеря обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("точки отхода нет"),
                    "причина обязана говорить, что точки отхода нет, фактически "
                            + spirit.taskFailureReason().orElse("нет"));
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "camp_defense_camp", timeoutTicks = 200)
    public static void defendedPoint_CampNearerThanHome_PointsAtCamp(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(7, 1, 5));
        spirit.setHome(MovementScene.coord(helper, new BlockPos(1, 1, 5)));
        Camp camp = campAt(helper, new BlockPos(8, 1, 5), CampTuning.CAMP_RADIUS);
        spirit.setCamp(camp);
        SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());

        helper.succeedWhen(() -> {
            helper.assertTrue(CoordinatorRegistry.get(helper.getLevel(), key).snapshot().tick() > 0,
                    "координатор ещё не тикнул поселение: снимка нет, точка обороны неизвестна");
            Point defended = CoordinatorRegistry.get(helper.getLevel(), key).snapshot().defendedPoint()
                    .orElse(null);
            helper.assertTrue(defended != null, "у поселения с домом обязана быть точка обороны");
            helper.assertTrue(defended.distanceTo(camp.center()) < 0.5,
                    "точка обороны — ближний лагерь, а не дальний дом: точка " + defended
                            + ", лагерь " + camp.center());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "camp_defense_home", timeoutTicks = 200)
    public static void defendedPoint_NoCamp_PointsAtHome(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(7, 1, 5));
        BlockCoord home = MovementScene.coord(helper, new BlockPos(1, 1, 5));
        spirit.setHome(home);
        SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());

        helper.succeedWhen(() -> {
            helper.assertTrue(CoordinatorRegistry.get(helper.getLevel(), key).snapshot().tick() > 0,
                    "координатор ещё не тикнул поселение: снимка нет, точка обороны неизвестна");
            Point defended = CoordinatorRegistry.get(helper.getLevel(), key).snapshot().defendedPoint()
                    .orElse(null);
            helper.assertTrue(defended != null, "у поселения с домом обязана быть точка обороны");
            Point homePoint = new Point(home.x() + 0.5, home.y(), home.z() + 0.5);
            helper.assertTrue(defended.distanceTo(homePoint) < 0.5,
                    "точка обороны без лагеря — дом: точка " + defended + ", дом " + homePoint);
            MovementScene.finish(helper);
        });
    }
}
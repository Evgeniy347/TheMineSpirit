package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritHomeStore;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementHomeGameTests {
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final BlockPos HOME = new BlockPos(7, 1, 1);
    private static final int ARRIVE_LIMIT_TICKS = 200;
    private static final int NO_HOME_LIMIT_TICKS = 2;
    private static final double STANDS_STILL = 0.1;
    private static final int FUTURE_VERSION = 99;

    private MovementHomeGameTests() {
    }

    @GameTest(template = "arena", batch = "move_home_arrive", timeoutTicks = 300)
    public static void returnHome_HomeSixBlocksAway_ArrivesWithinTolerance(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        spirit.setHome(MovementScene.coord(helper, HOME));
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("return_home", Map.of()),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дом в 6 блоках достижим: return_home обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = MovementScene.distanceTo(helper, spirit, HOME);
            helper.assertTrue(distance <= MovementScene.ARRIVAL,
                    "дух обязан стоять у дома в пределах допуска " + MovementScene.ARRIVAL + ", расстояние " + distance);
            helper.assertTrue(run.spiritTicks() <= ARRIVE_LIMIT_TICKS,
                    "шесть блоков обязаны пройтись за " + ARRIVE_LIMIT_TICKS + " тиков духа, ушло " + run.spiritTicks());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_home_none", timeoutTicks = 100)
    public static void returnHome_NoHomeSet_FailsWithinTwoTicksWithoutMoving(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("return_home", Map.of()),
                MovementScene.SETTLE_TICKS);
        double[] startAt = new double[2];
        helper.onEachTick(() -> {
            if (spirit.tickCount == MovementScene.SETTLE_TICKS) {
                startAt[0] = spirit.getX();
                startAt[1] = spirit.getZ();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "без дома return_home обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("дом не задан"),
                    "причина обязана говорить, что дом не задан, фактически " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(run.spiritTicks() <= NO_HOME_LIMIT_TICKS,
                    "отказ без дома идёт до первого шага: за " + NO_HOME_LIMIT_TICKS + " тика, а прошло "
                            + run.spiritTicks());
            double moved = Math.hypot(spirit.getX() - startAt[0], spirit.getZ() - startAt[1]);
            helper.assertTrue(moved < STANDS_STILL, "дух без дома не двигается, а сдвинулся на " + moved);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_home_nbt")
    public static void home_SavedAndLoaded_SurvivesRoundTripAndRejectsFutureVersion(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        SpiritEntity saved = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockCoord home = new BlockCoord(-12, 64, 345);
        CompoundTag clean = new CompoundTag();
        saved.addAdditionalSaveData(clean);
        SpiritEntity noHome = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, 1, 1));
        noHome.readAdditionalSaveData(clean);
        helper.assertTrue(noHome.home().isEmpty(),
                "у духа без дома загрузка не выдумывает дом: прочитан " + noHome.home());

        saved.setHome(home);
        CompoundTag tag = new CompoundTag();
        saved.addAdditionalSaveData(tag);
        SpiritEntity loaded = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        loaded.readAdditionalSaveData(tag);
        helper.assertTrue(home.equals(loaded.home().orElse(null)),
                "дом обязан пережить сохранение и загрузку: записан " + home + ", прочитан " + loaded.home());

        CompoundTag legacyFormat = tag.copy();
        legacyFormat.getCompound(SpiritHomeStore.KEY).remove(SpiritHomeStore.SET_KEY);
        SpiritEntity legacyFormatLoad = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 1));
        legacyFormatLoad.readAdditionalSaveData(legacyFormat);
        helper.assertTrue(home.equals(legacyFormatLoad.home().orElse(null)),
                "старое сохранение без флага " + SpiritHomeStore.SET_KEY + " читается как дом: записан " + home
                        + ", прочитан " + legacyFormatLoad.home());

        tag.getCompound(SpiritHomeStore.KEY).putInt("Version", FUTURE_VERSION);
        SpiritEntity future = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 1));
        future.readAdditionalSaveData(tag);
        helper.assertTrue(future.home().isEmpty(),
                "дом чужой версии не читается молча: дух остаётся без дома, а получил " + future.home());

        saved.clearHome();
        BlockPos legacyPos = helper.absolutePos(new BlockPos(5, 1, 1));
        saved.updateMemory(saved.memory().remember(new MemoryPoint("old-home", MemoryType.BASE,
                helper.getLevel().dimension().location().toString(),
                new BlockCoord(legacyPos.getX(), legacyPos.getY(), legacyPos.getZ()))));
        CompoundTag cleared = new CompoundTag();
        saved.addAdditionalSaveData(cleared);
        SpiritEntity clearedLoad = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        clearedLoad.readAdditionalSaveData(cleared);
        helper.assertTrue(clearedLoad.home().isEmpty(),
                "снятый дом не воскресает из легаси-памяти BASE: прочитан " + clearedLoad.home());
        helper.assertTrue(clearedLoad.memory().recallAll(MemoryType.BASE).size() == 1,
                "BASE в памяти — это отдельное место, а не дом: снятие дома не стирает память, осталось "
                        + clearedLoad.memory().recallAll(MemoryType.BASE));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "move_home_legacy")
    public static void home_LegacyBaseMemoryWithoutHomeKey_MigratesOnLoadAndKeepsMemory(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        SpiritEntity legacy = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos absolute = helper.absolutePos(new BlockPos(5, 1, 1));
        BlockCoord legacyHome = new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
        legacy.updateMemory(legacy.memory().remember(new MemoryPoint("old-home", MemoryType.BASE,
                helper.getLevel().dimension().location().toString(), legacyHome)));
        CompoundTag tag = new CompoundTag();
        legacy.addAdditionalSaveData(tag);
        tag.remove(SpiritHomeStore.KEY);
        helper.assertTrue(!tag.contains(SpiritHomeStore.KEY),
                "старое сохранение дома живёт только в памяти BASE: ключа " + SpiritHomeStore.KEY + " в нём нет");

        SpiritEntity loaded = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        loaded.readAdditionalSaveData(tag);
        helper.assertTrue(legacyHome.equals(loaded.home().orElse(null)),
                "старое сохранение читается: BASE-память обязана стать домом, записан " + legacyHome
                        + ", прочитан " + loaded.home());
        helper.assertTrue(loaded.memory().recallAll(MemoryType.BASE).size() == 1,
                "перенос дома не стирает память BASE: точка остаётся местом, осталось "
                        + loaded.memory().recallAll(MemoryType.BASE));

        CompoundTag migrated = new CompoundTag();
        loaded.addAdditionalSaveData(migrated);
        helper.assertTrue(migrated.contains(SpiritHomeStore.KEY),
                "перенесённый дом обязан сохраниться уже как " + SpiritHomeStore.KEY + ": иначе миграция повторяется "
                        + "каждую загрузку");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }
}

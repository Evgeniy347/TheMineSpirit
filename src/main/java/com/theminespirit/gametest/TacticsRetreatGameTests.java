package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsRetreatGameTests {
    private static final int HOME_LIMIT_TICKS = 100;
    private static final int COVER_LIMIT_TICKS = 100;
    private static final int KITE_LIMIT_TICKS = 150;
    private static final int WATER_LIMIT_TICKS = 100;
    private static final int NO_BUILD_WATCH_TICKS = 60;
    private static final int AWAY_LIMIT_TICKS = 100;
    private static final double HOME_ARRIVAL = 2.0;
    private static final double COVER_COLUMN = 10.0;
    private static final double SLOW_ENEMY_SPEED = 0.05;
    private static final double MIN_SEPARATION_GAIN = 3.0;
    private static final int TOWER_BLOCKS = 8;
    private static final int ARROWS = 32;
    private static final int FIRE_TICKS = 40;
    private static final int FIRE_LIMIT_TICKS = 60;
    private static final int WATER_CLEAR_RADIUS = 10;

    private TacticsRetreatGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void retreatHome_LowHpHuskFourBlocksAway_RunsToHomeEightBlocksAwayWithoutStriking(
            GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(9, 1, 1));
        BlockPos home = new BlockPos(1, 1, 1);
        spirit.setHome(MovementScene.coord(helper, home));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(9, 1, 9));
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, new BlockPos(9, 1, 5), true, TacticsScene.LOW_HP);
        int[] arrivedAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (arrivedAt[0] < 0 && TacticsScene.distance(helper, spirit, home) <= HOME_ARRIVAL) {
                arrivedAt[0] = scene.ticks();
            }
            if (arrivedAt[0] < 0 && scene.ticks() > HOME_LIMIT_TICKS) {
                helper.fail("дух не дошёл до дома за " + HOME_LIMIT_TICKS + " тиков, расстояние "
                        + TacticsScene.distance(helper, spirit, home) + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(arrivedAt[0] >= 0, "дух ещё не дошёл до дома");
            helper.assertTrue(spirit.tactics().combat().strikes() == 0, "отступающий дух не бьёт врага");
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.HOME,
                    "исполняемый отход — HOME, получено "
                            + spirit.tactics().retreat().method());
            helper.assertTrue(
                    spirit.getHealth() == TacticsScene.LOW_HP,
                    "по бегущему духу не попали: здоровье " + spirit.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void retreatCover_LowHpWitchInView_RunsBehindWallAndBreaksSight(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        for (int z = 3; z <= 5; z++) {
            helper.setBlock(9, 1, z, Blocks.STONE);
            helper.setBlock(9, 2, z, Blocks.STONE);
        }
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        BlockPos witchAt = new BlockPos(1, 1, 1);
        Witch witch = TacticsScene.watcher(helper, EntityType.WITCH, witchAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, witch, witchAt, false, TacticsScene.LOW_HP);
        int[] hiddenAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            boolean hidden = helper.relativeVec(spirit.position()).x >= COVER_COLUMN && !witch.hasLineOfSight(spirit);
            if (hiddenAt[0] < 0 && hidden) {
                hiddenAt[0] = scene.ticks();
            }
            if (hiddenAt[0] < 0 && scene.ticks() > COVER_LIMIT_TICKS) {
                helper.fail("дух не спрятался за стеной за " + COVER_LIMIT_TICKS + " тиков, позиция "
                        + helper.relativeVec(spirit.position()) + ", видимость " + witch.hasLineOfSight(spirit)
                        + ", решение " + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(hiddenAt[0] >= 0, "дух ещё не спрятался");
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.COVER,
                    "исполняемый отход — COVER, получено "
                            + spirit.tactics().retreat().method());
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void retreatKite_LowHpWithBowSlowHusk_BacksAwayAndHitsItWithArrows(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.BOW));
        spirit.getInventory().addItem(new ItemStack(Items.ARROW, ARROWS));
        BlockPos huskAt = new BlockPos(0, 1, 5);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        TacticsScene.slowDown(husk, SLOW_ENEMY_SPEED);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, TacticsScene.LOW_HP);
        float startHealth = husk.getHealth();
        int[] hitAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (hitAt[0] < 0 && husk.getHealth() < startHealth) {
                hitAt[0] = scene.ticks();
            }
            if (hitAt[0] < 0 && scene.ticks() > KITE_LIMIT_TICKS) {
                helper.fail("стрела не попала во врага за " + KITE_LIMIT_TICKS + " тиков, выстрелов "
                        + spirit.tactics().combat().shots() + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(hitAt[0] >= 0, "стрела ещё не попала во врага");
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.KITE,
                    "исполняемый отход — KITE, получено "
                            + spirit.tactics().retreat().method());
            helper.assertTrue(spirit.tactics().combat().strikes() == 0, "кайт не рукопашная: ударов мечом нет");
            helper.assertTrue(
                    spirit.distanceTo(husk) > 4.0,
                    "дух держит дистанцию больше начальной, сейчас " + spirit.distanceTo(husk));
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void retreatWater_LowHpHuskNear_RunsIntoPondSevenBlocksAway(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        for (int x = 7; x <= 9; x++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(x, 0, z, Blocks.WATER);
            }
        }
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 5));
        BlockPos huskAt = new BlockPos(1, 1, 9);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, TacticsScene.LOW_HP);
        int[] inWaterAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (inWaterAt[0] < 0 && spirit.isInWater()) {
                inWaterAt[0] = scene.ticks();
            }
            if (inWaterAt[0] < 0 && scene.ticks() > WATER_LIMIT_TICKS) {
                helper.fail("дух не вошёл в воду за " + WATER_LIMIT_TICKS + " тиков, позиция "
                        + helper.relativeVec(spirit.position()) + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(inWaterAt[0] >= 0, "дух ещё не в воде");
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.WATER,
                    "исполняемый отход — WATER, получено "
                            + spirit.tactics().retreat().method());
            helper.assertTrue(
                    spirit.tactics().wantsControl(),
                    "в воде дух остаётся под тактикой: политика воды не вытаскивает его на сушу");
            TacticsScene.finish(helper, spirit);
        });
    }

    private static void clearWaterAround(SpiritEntity spirit) {
        BlockPos center = spirit.blockPosition();
        for (int dx = -WATER_CLEAR_RADIUS; dx <= WATER_CLEAR_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -WATER_CLEAR_RADIUS; dz <= WATER_CLEAR_RADIUS; dz++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (spirit.level().getBlockState(at).is(Blocks.WATER)) {
                        spirit.level().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void retreatWater_OnFireWithBucket_PoursWaterUnderSelfAndExtinguishes(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        clearWaterAround(spirit);
        BlockPos huskAt = new BlockPos(0, 1, 5);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, false, TacticsScene.LOW_HP);
        boolean[] burned = {false};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (!burned[0]) {
                spirit.setRemainingFireTicks(200);
                spirit.getInventory().addItem(new ItemStack(Items.WATER_BUCKET));
                burned[0] = true;
            }
            if (burned[0] && spirit.getRemainingFireTicks() > 0 && scene.ticks() > FIRE_LIMIT_TICKS) {
                helper.fail("дух с ведром не потушился за " + FIRE_LIMIT_TICKS + " тиков, огонь "
                        + spirit.getRemainingFireTicks() + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(burned[0], "дух ещё не горел");
            helper.assertTrue(
                    spirit.getRemainingFireTicks() <= 0, "огонь на духе не погашен: " + spirit.getRemainingFireTicks());
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.WATER,
                    "исполняемый отход — WATER (ведро), получено "
                            + spirit.tactics().retreat().method()
                            + ", ведро в инвентаре " + spirit.gear().hasWaterBucket() + ", блок в клетке "
                            + spirit.blockPosition() + " = " + helper.getBlockState(spirit.blockPosition()));
            helper.assertTrue(
                    spirit.level().getBlockState(spirit.blockPosition()).is(Blocks.WATER),
                    "ведро вылито под себя: в клетке духа " + spirit.blockPosition() + " должна быть вода, а там "
                            + spirit.level().getBlockState(spirit.blockPosition()));
            spirit.level().setBlock(spirit.blockPosition(), Blocks.AIR.defaultBlockState(), 3);
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void retreatWater_OnFireWithoutBucket_RunsAwayInsteadOfPouring(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        clearWaterAround(spirit);
        BlockPos huskAt = new BlockPos(0, 1, 5);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, false, TacticsScene.LOW_HP);
        boolean[] burned = {false};
        double[] startGap = {Double.NaN};
        int[] farAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (!burned[0]) {
                spirit.setRemainingFireTicks(FIRE_TICKS);
                burned[0] = true;
            }
            if (Double.isNaN(startGap[0])) {
                startGap[0] = spirit.distanceTo(husk);
            }
            if (farAt[0] < 0 && spirit.distanceTo(husk) >= startGap[0] + MIN_SEPARATION_GAIN) {
                farAt[0] = scene.ticks();
            }
            if (farAt[0] < 0 && scene.ticks() > FIRE_LIMIT_TICKS) {
                helper.fail("дух без ведра не ушёл от врага за " + FIRE_LIMIT_TICKS + " тиков: дистанция "
                        + spirit.distanceTo(husk) + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(burned[0], "дух ещё не горел");
            helper.assertTrue(farAt[0] >= 0, "дистанция ещё не выросла");
            helper.assertTrue(
                    spirit.tactics().retreat().method() != RetreatMethod.WATER,
                    "без ведра WATER не исполняется, получено "
                            + spirit.tactics().retreat().method());
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.AWAY,
                    "вместо воды обычный отход — AWAY, получено "
                            + spirit.tactics().retreat().method());
            TacticsScene.finish(helper, spirit);
        });
    }

    // Отход без стройки (решение владельца 18.10): с блоками в инвентаре и врагом в четырёх блоках (раньше — сцена
    // баррикады) дух не строит ни баррикаду, ни столб — только уходит; блоки остаются в инвентаре.
    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void retreat_LowHpWithBlocksHuskFourAway_PlacesNoBlocks(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.COBBLESTONE, TOWER_BLOCKS));
        BlockPos huskAt = new BlockPos(8, 1, 5);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, false, TacticsScene.LOW_HP);
        int[] retreatedAt = {-1};
        helper.onEachTick(() -> {
            if (scene.started()
                    && retreatedAt[0] < 0
                    && spirit.tactics().retreat().retreating()) {
                retreatedAt[0] = scene.ticks();
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(
                    retreatedAt[0] >= 0 && scene.ticks() >= retreatedAt[0] + NO_BUILD_WATCH_TICKS,
                    "дух с низким здоровьем обязан отступать и мы смотрим " + NO_BUILD_WATCH_TICKS + " тиков отхода; "
                            + "решение " + spirit.tactics().explain().orElse("нет"));
            helper.assertTrue(
                    spirit.gear().blocks() == TOWER_BLOCKS,
                    "отход не тратит блоки: столб и баррикада убраны, а в инвентаре осталось "
                            + spirit.gear().blocks() + " из " + TOWER_BLOCKS);
            helper.assertTrue(
                    cobblestoneAround(helper) == 0,
                    "у места отхода не появилось ни одного булыжника: отход только уходит, фактически "
                            + cobblestoneAround(helper));
            TacticsScene.finish(helper, spirit);
        });
    }

    private static int cobblestoneAround(GameTestHelper helper) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(10, 4, 10))) {
            if (helper.getBlockState(pos).is(Blocks.COBBLESTONE)) {
                count++;
            }
        }
        return count;
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void retreatAway_LowHpSlowHuskFourBlocksBehind_SeparationGrowsWithoutBeingHit(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 5));
        BlockPos huskAt = new BlockPos(0, 1, 5);
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, huskAt);
        TacticsScene.slowDown(husk, SLOW_ENEMY_SPEED);
        PolicyScene scene = TacticsScene.ambush(helper, spirit, husk, huskAt, true, TacticsScene.LOW_HP);
        double[] startGap = {Double.NaN};
        int[] farAt = {-1};
        helper.onEachTick(() -> {
            if (!scene.started()) {
                return;
            }
            if (Double.isNaN(startGap[0])) {
                startGap[0] = spirit.distanceTo(husk);
            }
            if (farAt[0] < 0 && spirit.distanceTo(husk) >= startGap[0] + MIN_SEPARATION_GAIN) {
                farAt[0] = scene.ticks();
            }
            if (farAt[0] < 0 && scene.ticks() > AWAY_LIMIT_TICKS) {
                helper.fail("дистанция не выросла на " + MIN_SEPARATION_GAIN + " за " + AWAY_LIMIT_TICKS + " тиков: "
                        + spirit.distanceTo(husk) + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(farAt[0] >= 0, "дистанция ещё не выросла");
            helper.assertTrue(
                    spirit.tactics().retreat().method() == RetreatMethod.AWAY,
                    "исполняемый отход — AWAY, получено "
                            + spirit.tactics().retreat().method());
            helper.assertTrue(
                    spirit.getHealth() == TacticsScene.LOW_HP,
                    "по убегающему духу не попали: здоровье " + spirit.getHealth());
            TacticsScene.finish(helper, spirit);
        });
    }
}

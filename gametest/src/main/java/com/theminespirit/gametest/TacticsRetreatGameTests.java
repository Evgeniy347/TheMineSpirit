package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsRetreatGameTests {
    private static final int KITE_LIMIT_TICKS = 150;
    private static final double SLOW_ENEMY_SPEED = 0.05;
    private static final int ARROWS = 32;
    private static final int DODGE_LIMIT_TICKS = 40;
    private static final float ARROW_SPEED = 2.0F;
    private static final double DODGE_DISTANCE = 0.3;

    private TacticsRetreatGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void retreatKiteAndDodge_LowHpUnderHuskAndArrow_KeepsDistanceAndStepsAside(GameTestHelper helper) {
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
        boolean[] kitePassed = {false};
        SpiritEntity[] dodger = {null};
        AbstractArrow[] arrow = new AbstractArrow[1];
        double[] startX = new double[1];
        double[] startZ = new double[1];
        double[] lastX = new double[1];
        double[] lastZ = new double[1];
        double[] maxSideways = new double[1];
        double[] closest = {Double.MAX_VALUE};
        float[] dodgeHealth = new float[1];
        int[] firedAt = {-1};
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
            if (dodger[0] == null || arrow[0] == null) {
                return;
            }
            maxSideways[0] =
                    Math.max(maxSideways[0], Math.hypot(dodger[0].getX() - startX[0], dodger[0].getZ() - startZ[0]));
            boolean flying = arrow[0].isAlive() && arrow[0].getDeltaMovement().lengthSqr() > 0.0001;
            if (flying) {
                closest[0] = Math.min(
                        closest[0],
                        segmentDistance(startX[0], startZ[0], lastX[0], lastZ[0], arrow[0].getX(), arrow[0].getZ()));
                lastX[0] = arrow[0].getX();
                lastZ[0] = arrow[0].getZ();
            }
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            if (!kitePassed[0]) {
                helper.assertTrue(hitAt[0] >= 0, "стрела ещё не попала во врага");
                helper.assertTrue(
                        spirit.tactics().retreat().method() == RetreatMethod.KITE,
                        "исполняемый отход — KITE, получено "
                                + spirit.tactics().retreat().method());
                helper.assertTrue(spirit.tactics().combat().strikes() == 0, "кайт не рукопашная: ударов мечом нет");
                helper.assertTrue(
                        spirit.distanceTo(husk) > 4.0,
                        "дух держит дистанцию больше начальной, сейчас " + spirit.distanceTo(husk));
                kitePassed[0] = true;
                husk.discard();
                spirit.discard();
                dodger[0] = TacticsScene.spirit(helper, new BlockPos(5, 1, 8));
                dodger[0].setDemo(true);
                Skeleton skeleton = TacticsScene.watcher(helper, EntityType.SKELETON, new BlockPos(5, 1, 0));
                startX[0] = dodger[0].getX();
                startZ[0] = dodger[0].getZ();
                dodgeHealth[0] = dodger[0].getHealth();
                arrow[0] = shoot(skeleton, dodger[0]);
                lastX[0] = arrow[0].getX();
                lastZ[0] = arrow[0].getZ();
                firedAt[0] = (int) helper.getTick();
                helper.assertTrue(false, "ждём уклонения от стрелы скелета");
            }
            boolean flying = arrow[0] != null
                    && arrow[0].isAlive()
                    && arrow[0].getDeltaMovement().lengthSqr() > 0.0001;
            if (flying && helper.getTick() - firedAt[0] < DODGE_LIMIT_TICKS) {
                helper.assertTrue(false, "стрела ещё летит к духу");
            }
            helper.assertTrue(
                    dodger[0].getHealth() >= dodgeHealth[0],
                    "Стрела скелета не попала в духа: рефлекс уклонения обязан увести его с траектории, иначе первая "
                            + "стрела снимает здоровье. Здоровье " + dodger[0].getHealth() + " из " + dodgeHealth[0]
                            + ", дух " + dodger[0].position() + ", стрела " + arrowText(arrow[0]) + ", смещение "
                            + maxSideways[0] + ", ближайшая точка " + closest[0]);
            helper.assertTrue(
                    maxSideways[0] >= DODGE_DISTANCE,
                    "Дух шагнул в сторону от траектории: смещение " + maxSideways[0] + " меньше " + DODGE_DISTANCE
                            + ", стрела " + arrowText(arrow[0]));
            helper.assertTrue(
                    closest[0] <= 0.5,
                    "Стрела прошла через место, где дух стоял: иначе промах случайный и проверять нечего, ближайшая "
                            + "точка " + closest[0] + ", стрела " + arrowText(arrow[0]));
            TacticsScene.finish(helper, dodger[0]);
        });
    }

    private static String arrowText(AbstractArrow arrow) {
        return arrow == null
                ? "нет"
                : arrow.position() + " скорость " + arrow.getDeltaMovement() + " жива " + arrow.isAlive();
    }

    private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double length = dx * dx + dz * dz;
        if (length < 1e-9) {
            return Math.hypot(px - ax, pz - az);
        }
        double share = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / length));
        return Math.hypot(px - (ax + share * dx), pz - (az + share * dz));
    }

    private static AbstractArrow shoot(Skeleton skeleton, SpiritEntity spirit) {
        AbstractArrow arrow =
                ProjectileUtil.getMobArrow(skeleton, new ItemStack(Items.ARROW), 1.0F, new ItemStack(Items.BOW));
        Vec3 from = skeleton.getEyePosition();
        Vec3 to = new Vec3(spirit.getX(), spirit.getY() + 1.2, spirit.getZ());
        Vec3 direction = to.subtract(from);
        arrow.setPos(from.x, from.y, from.z);
        arrow.shoot(direction.x, direction.y, direction.z, ARROW_SPEED, 0.0F);
        skeleton.level().addFreshEntity(arrow);
        return arrow;
    }
}

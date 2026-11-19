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
        RetreatTrack track = new RetreatTrack(husk.getHealth());
        helper.onEachTick(() -> track.tick(helper, scene, spirit, husk));

        helper.succeedWhen(() -> {
            scene.assertStarted();
            if (!track.kitePassed) {
                assertKite(helper, spirit, husk, track);
                track.startDodge(helper);
                helper.assertTrue(false, "ждём уклонения от стрелы скелета");
            }
            assertDodge(helper, track);
        });
    }

    private static void assertKite(GameTestHelper helper, SpiritEntity spirit, Husk husk, RetreatTrack track) {
        helper.assertTrue(track.hitAt >= 0, "стрела ещё не попала во врага");
        helper.assertTrue(
                spirit.tactics().retreat().method() == RetreatMethod.KITE,
                "исполняемый отход — KITE, получено "
                        + spirit.tactics().retreat().method());
        helper.assertTrue(spirit.tactics().combat().strikes() == 0, "кайт не рукопашная: ударов мечом нет");
        helper.assertTrue(
                spirit.distanceTo(husk) > 4.0,
                "дух держит дистанцию больше начальной, сейчас " + spirit.distanceTo(husk));
        track.kitePassed = true;
        husk.discard();
        spirit.discard();
    }

    private static void assertDodge(GameTestHelper helper, RetreatTrack track) {
        boolean flying = track.arrow != null
                && track.arrow.isAlive()
                && track.arrow.getDeltaMovement().lengthSqr() > 0.0001;
        if (flying && helper.getTick() - track.firedAt < DODGE_LIMIT_TICKS) {
            helper.assertTrue(false, "стрела ещё летит к духу");
        }
        helper.assertTrue(
                track.dodger.getHealth() >= track.dodgeHealth,
                "Стрела скелета не попала в духа: рефлекс уклонения обязан увести его с траектории, иначе первая "
                        + "стрела снимает здоровье. Здоровье " + track.dodger.getHealth() + " из " + track.dodgeHealth
                        + ", дух " + track.dodger.position() + ", стрела " + arrowText(track.arrow) + ", смещение "
                        + track.maxSideways + ", ближайшая точка " + track.closest);
        helper.assertTrue(
                track.maxSideways >= DODGE_DISTANCE,
                "Дух шагнул в сторону от траектории: смещение " + track.maxSideways + " меньше " + DODGE_DISTANCE
                        + ", стрела " + arrowText(track.arrow));
        helper.assertTrue(
                track.closest <= 0.5,
                "Стрела прошла через место, где дух стоял: иначе промах случайный и проверять нечего, ближайшая "
                        + "точка " + track.closest + ", стрела " + arrowText(track.arrow));
        TacticsScene.finish(helper, track.dodger);
    }

    private static final class RetreatTrack {
        private final float startHealth;
        private int hitAt = -1;
        private boolean kitePassed;
        private SpiritEntity dodger;
        private AbstractArrow arrow;
        private double startX;
        private double startZ;
        private double lastX;
        private double lastZ;
        private double maxSideways;
        private double closest = Double.MAX_VALUE;
        private float dodgeHealth;
        private int firedAt = -1;

        private RetreatTrack(float startHealth) {
            this.startHealth = startHealth;
        }

        private void tick(GameTestHelper helper, PolicyScene scene, SpiritEntity spirit, Husk husk) {
            if (!scene.started()) {
                return;
            }
            if (hitAt < 0 && husk.getHealth() < startHealth) {
                hitAt = scene.ticks();
            }
            if (hitAt < 0 && scene.ticks() > KITE_LIMIT_TICKS) {
                helper.fail("стрела не попала во врага за " + KITE_LIMIT_TICKS + " тиков, выстрелов "
                        + spirit.tactics().combat().shots() + ", решение "
                        + spirit.tactics().explain().orElse("нет"));
            }
            if (dodger == null || arrow == null) {
                return;
            }
            maxSideways = Math.max(maxSideways, Math.hypot(dodger.getX() - startX, dodger.getZ() - startZ));
            boolean flying = arrow.isAlive() && arrow.getDeltaMovement().lengthSqr() > 0.0001;
            if (flying) {
                closest = Math.min(closest, segmentDistance(startX, startZ, lastX, lastZ, arrow.getX(), arrow.getZ()));
                lastX = arrow.getX();
                lastZ = arrow.getZ();
            }
        }

        private void startDodge(GameTestHelper helper) {
            dodger = TacticsScene.spirit(helper, new BlockPos(5, 1, 8));
            dodger.setDemo(true);
            Skeleton skeleton = TacticsScene.watcher(helper, EntityType.SKELETON, new BlockPos(5, 1, 0));
            startX = dodger.getX();
            startZ = dodger.getZ();
            dodgeHealth = dodger.getHealth();
            arrow = shoot(skeleton, dodger);
            lastX = arrow.getX();
            lastZ = arrow.getZ();
            firedAt = (int) helper.getTick();
        }
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

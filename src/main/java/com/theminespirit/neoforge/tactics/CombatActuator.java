package com.theminespirit.neoforge.tactics;

import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;

public final class CombatActuator {
    public static final double MELEE_ARRIVAL = 1.2;
    public static final int SHOT_COOLDOWN_TICKS = 25;
    public static final double MAX_SHOT_DISTANCE = 24.0;
    public static final double SHOOT_APPROACH = 15.0;
    public static final double SHOT_SPEED = 1.6;
    public static final int NEVER = -1_000_000;

    private final SpiritEntity spirit;
    private final Approach approach;
    private int lastAttackTick = NEVER;
    private int lastShotTick = NEVER;
    private int strikes;
    private int shots;

    public CombatActuator(SpiritEntity spirit, Approach approach) {
        this.spirit = spirit;
        this.approach = approach;
    }

    public void melee(LivingEntity target) {
        spirit.gear().holdMelee();
        spirit.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (spirit.isWithinMeleeAttackRange(target)) {
            approach.cancel();
            if (spirit.tickCount - lastAttackTick >= SpiritGear.ATTACK_COOLDOWN_TICKS) {
                spirit.swing(InteractionHand.MAIN_HAND);
                spirit.doHurtTarget(target);
                lastAttackTick = spirit.tickCount;
                strikes++;
            }
            return;
        }
        chase(target, MELEE_ARRIVAL);
    }

    public void shoot(LivingEntity target) {
        spirit.gear().holdBow();
        if (spirit.distanceTo(target) > MAX_SHOT_DISTANCE) {
            chase(target, SHOOT_APPROACH);
            return;
        }
        approach.cancel();
        fireWhenReady(target);
    }

    public void fireWhenReady(LivingEntity target) {
        spirit.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (spirit.tickCount - lastShotTick < SHOT_COOLDOWN_TICKS) {
            return;
        }
        ItemStack arrow = spirit.gear().takeArrow();
        if (arrow.isEmpty()) {
            return;
        }
        AbstractArrow projectile = ProjectileUtil.getMobArrow(spirit, arrow, 1.0F, spirit.gear().bow());
        double dx = target.getX() - spirit.getX();
        double dy = target.getY(0.3333333333333333) - projectile.getY();
        double dz = target.getZ() - spirit.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        projectile.shoot(dx, dy + flat * 0.2, dz, (float) SHOT_SPEED, 14 - spirit.level().getDifficulty().getId() * 4);
        spirit.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F);
        spirit.level().addFreshEntity(projectile);
        lastShotTick = spirit.tickCount;
        shots++;
    }

    public void stop() {
        approach.cancel();
    }

    public int lastStrikeTick() {
        return lastAttackTick;
    }

    public int strikes() {
        return strikes;
    }

    public int shots() {
        return shots;
    }

    private void chase(LivingEntity target, double arrival) {
        if (!approach.isActive()) {
            approach.begin(target, arrival);
        }
        approach.tick();
    }
}

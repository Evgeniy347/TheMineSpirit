package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.ProjectileDodge;
import com.theminespirit.core.movement.ProjectileDodge.Position;
import com.theminespirit.core.movement.ProjectileTrajectory;
import com.theminespirit.core.movement.ProjectileTrajectory.Bounds;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class ProjectileDodgeKeeper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEVER = -1_000_000;
    private static final double ARROW_GRAVITY = 0.05;
    private static final double ARROW_DRAG = 0.99;
    private static final double FIREBALL_DRAG = 0.95;
    private static final double WITHER_SKULL_DRAG = 0.73;
    private static final double MOB_SEARCH_MARGIN = 1.0;

    private final SpiritEntity spirit;
    private int lastDodgeTick = NEVER;

    ProjectileDodgeKeeper(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    void tick() {
        ProjectileDodge rules = ProjectileDodge.current();
        if (!rules.enabled()
                || spirit.isRemoved()
                || spirit.isDeadOrDying()
                || spirit.isSleeping()
                || spirit.isPassenger()) {
            return;
        }
        int now = spirit.tickCount;
        if (lastDodgeTick >= 0 && now - lastDodgeTick < rules.cooldownTicks()) {
            return;
        }
        List<Projectile> threats = projectiles(rules);
        if (threats.isEmpty()) {
            return;
        }
        List<ProjectileTrajectory.Projectile> projectiles = new ArrayList<>(threats.size());
        for (Projectile threat : threats) {
            projectiles.add(flight(threat));
        }
        LevelSafetyView safety = new LevelSafetyView(spirit.level());
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        ProjectileDodge.Request request =
                new ProjectileDodge.Request(projectiles, bounds(), feet, nearbyMobs(rules), lastDodgeTick, now);
        Optional<ProjectileDodge.Dodge> dodge = rules.decide(request, cell -> MiningSafety.canStand(cell, safety));
        dodge.ifPresent(step -> step(step, rules, now));
    }

    private List<Projectile> projectiles(ProjectileDodge rules) {
        return spirit.level()
                .getEntitiesOfClass(
                        Projectile.class,
                        spirit.getBoundingBox().inflate(rules.scanRadius()),
                        projectile -> projectile.isAlive()
                                && !projectile.isRemoved()
                                && (projectile instanceof AbstractArrow
                                        || projectile instanceof AbstractHurtingProjectile)
                                && !spirit.equals(projectile.getOwner())
                                && !(projectile.getOwner() instanceof SpiritEntity));
    }

    private static ProjectileTrajectory.Projectile flight(Projectile threat) {
        Vec3 at = threat.getBoundingBox().getCenter();
        Vec3 velocity = threat.getDeltaMovement();
        if (threat instanceof WitherSkull skull) {
            return new ProjectileTrajectory.Projectile(
                    at.x,
                    at.y,
                    at.z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    0,
                    skull.isDangerous() ? WITHER_SKULL_DRAG : FIREBALL_DRAG,
                    skull.accelerationPower);
        }
        if (threat instanceof AbstractHurtingProjectile hurling) {
            return new ProjectileTrajectory.Projectile(
                    at.x, at.y, at.z, velocity.x, velocity.y, velocity.z, 0, FIREBALL_DRAG, hurling.accelerationPower);
        }
        return new ProjectileTrajectory.Projectile(
                at.x, at.y, at.z, velocity.x, velocity.y, velocity.z, ARROW_GRAVITY, ARROW_DRAG, 0);
    }

    private List<Position> nearbyMobs(ProjectileDodge rules) {
        List<Mob> found = spirit.level()
                .getEntitiesOfClass(
                        Mob.class,
                        spirit.getBoundingBox().inflate(rules.scanRadius() + MOB_SEARCH_MARGIN),
                        mob -> mob.isAlive() && mob instanceof Enemy);
        List<Position> mobs = new ArrayList<>(found.size());
        for (Mob mob : found) {
            mobs.add(new Position(mob.getX(), mob.getY(), mob.getZ()));
        }
        return mobs;
    }

    private Bounds bounds() {
        AABB box = spirit.getBoundingBox();
        return new Bounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private void step(ProjectileDodge.Dodge step, ProjectileDodge rules, int now) {
        spirit.push(step.dx() * rules.stepSpeed(), 0, step.dz() * rules.stepSpeed());
        lastDodgeTick = now;
        LOGGER.info(
                "[dodge] дух {} ушёл с траектории снаряда шагом ({}, {})", spirit.displayName(), step.dx(), step.dz());
    }
}

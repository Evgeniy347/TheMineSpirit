package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritRoles;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.coordinator.WorkTargets;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class SpiritSnapshotReader {
    private static final double THREAT_RADIUS = 16.0;

    private SpiritSnapshotReader() {}

    public static SpiritSnapshot read(SpiritEntity spirit, long tick) {
        if (spirit == null) {
            throw new IllegalArgumentException("spirit не может быть null: снимок без духа не собрать");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick = " + tick + ": тик наблюдения не может быть отрицательным");
        }
        UUID id = spirit.getUUID();
        Point pos = new Point(spirit.getX(), spirit.getY(), spirit.getZ());
        double maxHp = spirit.getMaxHealth();
        double hpFraction = maxHp <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, spirit.getHealth() / maxHp));
        SpiritRole role = roleOf(spirit);
        CombatMode mode = spirit.tactics().combatMode();
        Level level = spirit.level();
        AABB box = spirit.getBoundingBox().inflate(THREAT_RADIUS);
        List<LivingEntity> near =
                level.getEntitiesOfClass(LivingEntity.class, box, entity -> !entity.equals(spirit) && entity.isAlive());
        int threatsNear = 0;
        Set<UUID> attackers = new HashSet<>();
        for (LivingEntity entity : near) {
            if ((entity instanceof Enemy || entity instanceof Mob)
                    && !(entity instanceof SpiritEntity)
                    && entity.distanceTo(spirit) <= THREAT_RADIUS) {
                threatsNear++;
            }
            if (entity instanceof Mob mob && spirit.equals(mob.getTarget())) {
                attackers.add(mob.getUUID());
            }
        }
        boolean attacked = !attackers.isEmpty();
        threatsNear = Math.max(threatsNear, attackers.size());
        return new SpiritSnapshot(
                id,
                pos,
                hpFraction,
                role,
                mode,
                threatsNear,
                attacked,
                currentWorkTarget(spirit),
                tick,
                spirit.hunger().satietyFraction());
    }

    private static Optional<WorkTarget> currentWorkTarget(SpiritEntity spirit) {
        return WorkTargets.current(WorkReserveHub.leasesFor(spirit.level()).ownedBy(spirit.getUUID()));
    }

    private static SpiritRole roleOf(SpiritEntity spirit) {
        return SpiritRoles.classify(
                spirit.currentTaskScript(), spirit.currentPlan().isPresent(), false);
    }
}

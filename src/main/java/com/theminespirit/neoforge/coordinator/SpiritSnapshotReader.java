package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SpiritSnapshotReader {
    private static final double THREAT_RADIUS = 16.0;

    private SpiritSnapshotReader() {
    }

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
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != spirit && entity.isAlive());
        spirit.countEntityQuery();
        int threatsNear = 0;
        Set<UUID> attackers = new HashSet<>();
        for (LivingEntity entity : near) {
            if (entity instanceof Enemy || entity instanceof Mob) {
                threatsNear++;
            }
            if (entity instanceof Mob mob && mob.getTarget() == spirit) {
                attackers.add(mob.getUUID());
            }
        }
        boolean attacked = !attackers.isEmpty();
        threatsNear = Math.max(threatsNear, attackers.size());
        return new SpiritSnapshot(id, pos, hpFraction, role, mode, threatsNear, attacked,
                Optional.<WorkTarget>empty(), tick);
    }

    private static SpiritRole roleOf(SpiritEntity spirit) {
        Optional<String> script = spirit.currentTaskScript();
        if (script.isEmpty()) {
            return SpiritRole.DEFENDER;
        }
        String name = script.get();
        if (isBuilding(name)) {
            return SpiritRole.BUILDING;
        }
        if (isWorker(name)) {
            return SpiritRole.WORKER;
        }
        return SpiritRole.IDLE;
    }

    private static boolean isBuilding(String name) {
        switch (name) {
            case "build_schematic":
            case "fill":
            case "clear_area":
            case "dig_area":
            case "place_block":
                return true;
            default:
                return false;
        }
    }

    private static boolean isWorker(String name) {
        switch (name) {
            case "chop_tree":
            case "mine_vein":
            case "gather":
            case "harvest_crop":
            case "plant_crop":
            case "till":
            case "bone_meal":
            case "collect_drops":
            case "deliver":
            case "craft":
            case "smelt":
            case "withdraw":
            case "deposit":
            case "sort_chest":
            case "unload":
            case "equip":
            case "collect_eggs":
            case "breed":
            case "feed":
            case "shear":
            case "milk":
            case "cull":
            case "herd_check":
            case "hunt":
            case "capture":
                return true;
            default:
                return false;
        }
    }
}

package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

public final class EnemyCollector {
    public static final double ENEMY_RADIUS = 16.0;

    private EnemyCollector() {
    }

    public static List<EnemyFact> collect(ServerLevel level, List<SpiritEntity> members,
            List<SpiritSnapshot> snapshots) {
        if (level == null) {
            throw new IllegalArgumentException("Враги без уровня не собираются: мобы стоят в мире");
        }
        if (members == null || snapshots == null) {
            throw new IllegalArgumentException("Враги без духов не собираются: угрозы считаются рядом с духами");
        }
        List<SpiritEntity> alive = new ArrayList<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                alive.add(spirit);
            }
        }
        if (alive.isEmpty()) {
            return List.of();
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (SpiritEntity spirit : alive) {
            minX = Math.min(minX, spirit.getX() - ENEMY_RADIUS);
            minY = Math.min(minY, spirit.getY() - ENEMY_RADIUS);
            minZ = Math.min(minZ, spirit.getZ() - ENEMY_RADIUS);
            maxX = Math.max(maxX, spirit.getX() + ENEMY_RADIUS);
            maxY = Math.max(maxY, spirit.getY() + ENEMY_RADIUS);
            maxZ = Math.max(maxZ, spirit.getZ() + ENEMY_RADIUS);
        }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(minX, minY, minZ, maxX, maxY, maxZ), EnemyCollector::hostile);
        List<EnemyFact> facts = new ArrayList<>();
        for (LivingEntity entity : near) {
            if (!entity.isAlive() || entity instanceof SpiritEntity) {
                continue;
            }
            boolean targeted = entity instanceof Mob mob && alive.contains(mob.getTarget());
            double hpFraction = entity.getMaxHealth() <= 0 ? 0
                    : Math.max(0, Math.min(1, entity.getHealth() / entity.getMaxHealth()));
            facts.add(new EnemyFact(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                    entity.getUUID().toString(),
                    new Point(entity.getX(), entity.getY(), entity.getZ()), hpFraction, entity.getArmorValue(),
                    true, targeted));
        }
        facts.sort(Comparator.comparing(EnemyFact::type).thenComparing(EnemyFact::id));
        return List.copyOf(facts);
    }

    private static boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy || entity instanceof Mob;
    }
}

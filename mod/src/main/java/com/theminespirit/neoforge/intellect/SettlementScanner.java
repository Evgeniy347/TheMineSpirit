package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.husbandry.PenBounds;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.intellect.Forage;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdSpecies;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class SettlementScanner {
    public static final double ENEMY_RADIUS = 16.0;
    public static final int FORAGE_WATER_RADIUS = 8;

    public record Scan(List<EnemyFact> enemies, Forage forage) {
        public Scan {
            if (enemies == null || forage == null) {
                throw new IllegalArgumentException(
                        "Скан поселения без врагов или добычи не описывается: пустое задают списком и Forage.NONE");
            }
        }
    }

    private SettlementScanner() {}

    public static Scan scan(
            ServerLevel level,
            List<SpiritEntity> members,
            List<SpiritSnapshot> snapshots,
            Optional<Point> home,
            String containerFood) {
        if (level == null) {
            throw new IllegalArgumentException("Скан поселения без уровня не идёт: мобы стоят в мире");
        }
        if (members == null || snapshots == null || home == null || containerFood == null) {
            throw new IllegalArgumentException(
                    "Скан поселения без духов, снимков, дома или склада не идёт: враги и дичь считаются "
                            + "рядом с духами");
        }
        List<SpiritEntity> alive = new ArrayList<>();
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                alive.add(spirit);
            }
        }
        if (alive.isEmpty()) {
            return new Scan(List.of(), Forage.NONE);
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
        List<LivingEntity> near = level.getEntitiesOfClass(
                LivingEntity.class, new AABB(minX, minY, minZ, maxX, maxY, maxZ), SettlementScanner::visible);
        List<EnemyFact> facts = new ArrayList<>();
        Map<Species, Integer> wild = new EnumMap<>(Species.class);
        PenBounds homePen = homePen(home);
        for (LivingEntity entity : near) {
            if (!entity.isAlive() || entity instanceof SpiritEntity) {
                continue;
            }
            boolean targeted = entity instanceof Mob mob && alive.contains(mob.getTarget());
            if (entity instanceof Enemy || targeted) {
                double hpFraction = entity.getMaxHealth() <= 0
                        ? 0
                        : Math.max(0, Math.min(1, entity.getHealth() / entity.getMaxHealth()));
                facts.add(new EnemyFact(
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        entity.getUUID().toString(),
                        new Point(entity.getX(), entity.getY(), entity.getZ()),
                        hpFraction,
                        entity.getArmorValue(),
                        true,
                        targeted));
            } else if (homePen != null && entity instanceof Animal animal && isWild(animal, homePen)) {
                HerdSpecies.of(animal).ifPresent(species -> wild.merge(species, 1, Integer::sum));
            }
        }
        facts.sort(Comparator.comparing(EnemyFact::type).thenComparing(EnemyFact::id));
        boolean water = false;
        boolean rod = false;
        for (SpiritEntity spirit : alive) {
            rod |= hasRod(spirit);
            water |= waterNear(level, spirit);
        }
        return new Scan(List.copyOf(facts), forage(containerFood, wild, water, rod));
    }

    private static boolean hasRod(SpiritEntity spirit) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).is(Items.FISHING_ROD)) {
                return true;
            }
        }
        return false;
    }

    private static boolean waterNear(ServerLevel level, SpiritEntity spirit) {
        BlockPos center = spirit.blockPosition();
        for (int dx = -FORAGE_WATER_RADIUS; dx <= FORAGE_WATER_RADIUS; dx++) {
            for (int dz = -FORAGE_WATER_RADIUS; dz <= FORAGE_WATER_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getFluidState(center.offset(dx, dy, dz)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static PenBounds homePen(Optional<Point> home) {
        if (home.isEmpty()) {
            return null;
        }
        Point point = home.get();
        return new PenBounds(
                new BlockCoord((int) Math.floor(point.x()), (int) Math.floor(point.y()), (int) Math.floor(point.z())),
                CatalogLimits.HERD_HOME_RADIUS);
    }

    private static boolean isWild(Animal animal, PenBounds homePen) {
        var pos = animal.blockPosition();
        return !homePen.inside(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
    }

    private static Forage forage(String containerFood, Map<Species, Integer> wild, boolean water, boolean rod) {
        Species best = null;
        int count = 0;
        for (Map.Entry<Species, Integer> entry : wild.entrySet()) {
            if (best == null
                    || entry.getValue() > count
                    || (entry.getValue() == count && entry.getKey().name().compareTo(best.name()) < 0)) {
                best = entry.getKey();
                count = entry.getValue();
            }
        }
        if (best == null) {
            return new Forage(containerFood, 0, "", water, rod);
        }
        return new Forage(containerFood, count, best.name().toLowerCase(Locale.ROOT), water, rod);
    }

    private static boolean visible(LivingEntity entity) {
        return entity instanceof Enemy || entity instanceof Mob;
    }
}

package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.base.Camp;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TimeOfDay;
import com.theminespirit.core.tactics.adapter.CellReader;
import com.theminespirit.core.tactics.adapter.EnvFactsBuilder;
import com.theminespirit.core.tactics.adapter.EnvReading;
import com.theminespirit.core.tactics.adapter.EnvScanner;
import com.theminespirit.core.tactics.adapter.LineOfSight;
import com.theminespirit.core.tactics.adapter.OrdersBuilder;
import com.theminespirit.core.tactics.adapter.ReadBudget;
import com.theminespirit.core.tactics.adapter.SituationContext;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.tactics.adapter.Visibility;
import com.theminespirit.neoforge.LoadedTerrain;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WorldFactCollector {
    public static final int ALLY_RADIUS = 16;
    public static final long DAY_LENGTH = 24000;
    public static final long DUSK_START = 12000;
    public static final long NIGHT_START = 13000;
    public static final long NIGHT_END = 23000;

    public record Collected(SituationContext context, Map<String, LivingEntity> entities, EnvReading env, int blockReads,
            int entityQueries, Optional<Point> home, Optional<Point> camp) {
    }

    private final TacticsAdapterSettings settings;
    private final EnvScanner scanner = new EnvScanner();
    
    public WorldFactCollector(TacticsAdapterSettings settings) {
        this.settings = settings;
    }

    public Collected collect(SpiritEntity spirit, TacticsInputs inputs) {
        Level level = spirit.level();
        long now = level.getGameTime();
        Found found = findEntities(spirit);
        Map<String, LivingEntity> byId = new HashMap<>();
        List<EnemyFact> enemies = new ArrayList<>(found.enemies.size());
        ReadBudget total = new ReadBudget(settings.factChunkBudget());
        ReadBudget visibilityBudget = new ReadBudget(settings.factChunkBudget() / 2);
        LoadedTerrain terrain = new LoadedTerrain(level);
        CellReader sight = new CellReader(terrain, scanner.cache(), visibilityBudget, now);
        for (LivingEntity enemy : found.enemies) {
            boolean visible = visible(spirit, enemy, sight);
            String id = enemy.getUUID().toString();
            byId.put(id, enemy);
            enemies.add(enemyFact(spirit, enemy, id, visible));
        }
        inputs.forcedTarget()
                .filter(target -> target.distanceTo(spirit) <= settings.factRadius())
                .filter(target -> !byId.containsKey(target.getUUID().toString()))
                .ifPresent(target -> {
                    String id = target.getUUID().toString();
                    byId.put(id, target);
                    enemies.add(enemyFact(spirit, target, id, visible(spirit, target, sight)));
                    Point self = point(spirit.position());
                    enemies.sort(Comparator.comparingDouble((EnemyFact fact) -> fact.pos().distanceTo(self))
                            .thenComparing(EnemyFact::id));
                });
        total.charge(visibilityBudget.used());
        EnvReading env = enemies.isEmpty() ? EnvReading.NOTHING : scanEnv(spirit, enemies, terrain, total, now);
        Optional<Point> home = home(spirit);
        Optional<Point> camp = camp(spirit);
        EnvFacts envFacts = EnvFactsBuilder.build(env, point(spirit.position()), lightAt(spirit), timeOfDay(level), home);
        Orders orders = orders(spirit, inputs, found.enemies, found.allies);
        SelfFacts self = selfFacts(spirit, found.allies);
        SituationContext context = new SituationContext(self, enemies, envFacts, orders, inputs.ticksSinceHit(),
                inputs.ticksSinceStrike());
        return new Collected(context, byId, env, terrain.reads(), 1, home, camp);
    }

    public void invalidate(BlockPos pos) {
        scanner.cache().invalidate(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
    }

    private record Found(List<LivingEntity> enemies, int allies) {
    }

    private Found findEntities(SpiritEntity spirit) {
        double radius = settings.factRadius();
        List<LivingEntity> near = spirit.level().getEntitiesOfClass(LivingEntity.class,
                spirit.getBoundingBox().inflate(radius),
                entity -> entity != spirit && entity.isAlive() && (entity instanceof Enemy || entity instanceof SpiritEntity));
        List<LivingEntity> enemies = new ArrayList<>();
        int allies = 0;
        for (LivingEntity entity : near) {
            if (entity instanceof SpiritEntity) {
                allies += entity.distanceTo(spirit) <= ALLY_RADIUS ? 1 : 0;
            } else if (entity.distanceTo(spirit) <= radius) {
                enemies.add(entity);
            }
        }
        sortNearestFirst(enemies, spirit);
        return new Found(enemies, allies);
    }

    private static void sortNearestFirst(List<LivingEntity> enemies, SpiritEntity spirit) {
        enemies.sort(Comparator.comparingDouble((LivingEntity entity) -> entity.distanceToSqr(spirit))
                .thenComparing(entity -> entity.getUUID().toString()));
    }

    private EnvReading scanEnv(SpiritEntity spirit, List<EnemyFact> enemies, LoadedTerrain terrain, ReadBudget budget,
            long now) {
        EnemyFact threat = enemies.stream().min(Comparator
                .comparing((EnemyFact enemy) -> !ranged(enemy)).thenComparingDouble(
                        enemy -> enemy.pos().distanceTo(point(spirit.position()))))
                .orElseThrow();
        BlockCoord feet = new BlockCoord(spirit.blockPosition().getX(), spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
        return scanner.scan(point(spirit.position()), feet, Optional.of(threat.pos()), terrain, budget, now);
    }

    private static boolean ranged(EnemyFact enemy) {
        return TacticsConfig.loadDefault().enemies().profile(enemy.type()).ranged();
    }

    private static boolean visible(SpiritEntity spirit, LivingEntity enemy, CellReader sight) {
        Visibility visibility = LineOfSight.trace(point(spirit.getEyePosition()), point(enemy.getEyePosition()), sight);
        return visibility != Visibility.BLOCKED;
    }

    private static EnemyFact enemyFact(SpiritEntity spirit, LivingEntity enemy, String id, boolean visible) {
        boolean targetingSelf = enemy instanceof Mob mob && mob.getTarget() == spirit;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(enemy.getType()).toString();
        double hp = Mth.clamp(enemy.getHealth() / enemy.getMaxHealth(), 0.0F, 1.0F);
        double armor = Mth.clamp(enemy.getArmorValue(), 0, 20);
        return new EnemyFact(type, id, point(enemy.position()), hp, armor, visible, targetingSelf);
    }

    private static SelfFacts selfFacts(SpiritEntity spirit, int allies) {
        SpiritGear gear = spirit.gear();
        double maxHp = Mth.clamp(spirit.getMaxHealth(), 1, 1024);
        return new SelfFacts(point(spirit.position()), Mth.clamp(spirit.getHealth(), 0, (float) maxHp), maxHp,
                Mth.clamp(spirit.getArmorValue(), 0, 20), Math.min(1000, gear.meleeDamagePerSecond()),
                Math.min(2.0, spirit.getAttributeValue(Attributes.MOVEMENT_SPEED)), spirit.hunger().satietyFraction(),
                Math.min(2304, gear.blocks()), Math.min(2304, gear.arrows()), gear.hasWaterBucket(),
                Math.min(64, allies), Math.min(2304, gear.healItems()));
    }

    private static Orders orders(SpiritEntity spirit, TacticsInputs inputs, List<LivingEntity> enemies, int allies) {
        Optional<OrdersBuilder.Ward> ward = inputs.ward().flatMap(id -> wardOf(spirit, id, enemies));
        return OrdersBuilder.build(inputs.mode(), inputs.zone(), ward, allies > 0);
    }

    private static Optional<OrdersBuilder.Ward> wardOf(SpiritEntity spirit, UUID id, List<LivingEntity> enemies) {
        if (!(spirit.level() instanceof ServerLevel server)) {
            return Optional.empty();
        }
        Entity entity = server.getEntity(id);
        if (!(entity instanceof LivingEntity ward) || !ward.isAlive()) {
            return Optional.empty();
        }
        boolean threatened = enemies.stream().anyMatch(enemy -> enemy instanceof Mob mob && mob.getTarget() == ward);
        return Optional.of(new OrdersBuilder.Ward(point(ward.position()), threatened));
    }

    private static Optional<Point> camp(SpiritEntity spirit) {
        return spirit.camp().map(Camp::center);
    }

    private static Optional<Point> home(SpiritEntity spirit) {
        String dimension = spirit.level().dimension().location().toString();
        Point self = point(spirit.position());
        return spirit.memory().recallAll(MemoryType.BASE).stream().filter(p -> p.dimension().equals(dimension))
                .map(WorldFactCollector::center)
                .min(Comparator.comparingDouble(self::distanceTo));
    }

    private static Point center(MemoryPoint memoryPoint) {
        return new Point(memoryPoint.pos().x() + 0.5, memoryPoint.pos().y(), memoryPoint.pos().z() + 0.5);
    }

    private static int lightAt(SpiritEntity spirit) {
        return Mth.clamp(spirit.level().getMaxLocalRawBrightness(spirit.blockPosition()), 0, EnvFacts.MAX_LIGHT);
    }

    static TimeOfDay timeOfDay(Level level) {
        long time = Math.floorMod(level.getDayTime(), DAY_LENGTH);
        if (time >= NIGHT_START && time < NIGHT_END) {
            return TimeOfDay.NIGHT;
        }
        return time >= DUSK_START && time < NIGHT_START || time >= NIGHT_END ? TimeOfDay.DUSK : TimeOfDay.DAY;
    }

    private static Point point(Vec3 vec) {
        return new Point(vec.x, vec.y, vec.z);
    }
}

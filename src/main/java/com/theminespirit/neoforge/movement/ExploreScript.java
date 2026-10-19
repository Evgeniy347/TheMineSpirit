package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.intellect.OutsideGate;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.ExploreRoute;
import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryStore;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ExploreParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ExploreScript implements ScriptInstance {
    private static final int SURVEY_RADIUS = 2;
    private static final int SURVEY_DEPTH = 2;
    private static final int GROUND_SEARCH_DEPTH = 4;
    private static final double PARTY_RADIUS = 32.0;
    private static final long TICKS_PER_DAY = 24_000L;

    private final SpiritEntity spirit;
    private final ExploreParams params;
    private final BlockCoord origin;
    private final List<BlockCoord> route;
    private final Approach approach;
    private final LevelSafetyView safety;
    private int index;
    private int surveyed;
    private int recorded;
    private boolean admitted;
    private boolean walking;
    private boolean goingHome;
    private String failureReason;
    private ScriptResult result;

    public ExploreScript(SpiritEntity spirit, ExploreParams params) {
        this.spirit = spirit;
        this.params = params;
        BlockPos feet = spirit.blockPosition();
        this.origin = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        this.route = ExploreRoute.points(origin, params.radius(), params.budget());
        this.approach = new Approach(spirit);
        this.safety = new LevelSafetyView(spirit.level());
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез во время разведки");
        }
        if (!admitted) {
            String refusal = outsideRefusal();
            if (refusal != null) {
                return fail(refusal);
            }
            admitted = true;
        }
        if (goingHome) {
            return tickReturn();
        }
        if (index >= route.size()) {
            goingHome = true;
            return tickReturn();
        }
        if (!walking) {
            survey(route.get(index));
            if (!beginPoint(route.get(index))) {
                index++;
                return ScriptStatus.RUNNING;
            }
            walking = true;
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                surveyed++;
                index++;
                walking = false;
                yield ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> {
                index++;
                walking = false;
                yield ScriptStatus.RUNNING;
            }
        };
    }

    @Override
    public void cancel() {
        approach.cancel();
        walking = false;
    }

    @Override
    public void suspend() {
        approach.suspend();
        walking = false;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(index, route.size()));
    }

    private ScriptStatus tickReturn() {
        if (!approach.isActive()) {
            approach.begin(
                    new Vec3(origin.x() + 0.5, origin.y(), origin.z() + 0.5), CatalogLimits.NAV_ARRIVAL_TOLERANCE);
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> succeed();
            case STUCK, TIMEOUT ->
                fail("не вернулся к точке старта (" + origin.x() + ", " + origin.y() + ", " + origin.z()
                        + ") после разведки");
        };
    }

    private ScriptStatus succeed() {
        result = new ScriptResult.Data(Map.of(
                "radius", params.radius(), "budget", params.budget(), "surveyed", surveyed, "recorded", recorded));
        return ScriptStatus.SUCCEEDED;
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private boolean beginPoint(BlockCoord point) {
        for (int drop = 0; drop <= GROUND_SEARCH_DEPTH; drop++) {
            BlockCoord stand = new BlockCoord(point.x(), point.y() - drop, point.z());
            if (MiningSafety.canStand(stand, safety) && TravelCheck.refusal(spirit, stand) == null) {
                approach.begin(
                        new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), CatalogLimits.NAV_ARRIVAL_TOLERANCE);
                return true;
            }
        }
        return false;
    }

    private void survey(BlockCoord point) {
        boolean ore = false;
        boolean tree = false;
        boolean water = false;
        boolean lava = false;
        for (int dy = -SURVEY_DEPTH; dy <= SURVEY_DEPTH; dy++) {
            for (int dx = -SURVEY_RADIUS; dx <= SURVEY_RADIUS; dx++) {
                for (int dz = -SURVEY_RADIUS; dz <= SURVEY_RADIUS; dz++) {
                    BlockPos pos = new BlockPos(point.x() + dx, point.y() + dy, point.z() + dz);
                    if (!spirit.level().isLoaded(pos)) {
                        continue;
                    }
                    String kind = kindOf(spirit.level().getBlockState(pos));
                    if (kind == null) {
                        continue;
                    }
                    if (kind.equals("ore") && !ore) {
                        ore = record(MemoryType.RESOURCE, kind, pos);
                    } else if (kind.equals("tree") && !tree) {
                        tree = record(MemoryType.RESOURCE, kind, pos);
                    } else if (kind.equals("water") && !water) {
                        water = record(MemoryType.RESOURCE, kind, pos);
                    } else if (kind.equals("lava") && !lava) {
                        lava = record(MemoryType.DANGER, kind, pos);
                    }
                }
            }
        }
        LivingEntity enemy = nearestEnemy(point);
        if (enemy != null) {
            record(MemoryType.DANGER, "mob", enemy.blockPosition());
        }
    }

    private LivingEntity nearestEnemy(BlockCoord point) {
        Vec3 center = new Vec3(point.x() + 0.5, point.y() + 0.5, point.z() + 0.5);
        AABB box = new AABB(center, center).inflate(SURVEY_RADIUS + 1.0);
        return spirit
                .level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        entity -> entity instanceof Enemy && entity.isAlive() && !entity.isRemoved())
                .stream()
                .min((first, second) -> Double.compare(
                        first.position().distanceTo(center), second.position().distanceTo(center)))
                .orElse(null);
    }

    private boolean record(MemoryType type, String kind, BlockPos pos) {
        BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        String name = nameOf(kind, cell);
        MemoryStore memory = spirit.memory();
        if (memory.recall(name) != null || memory.recallAll(type).size() >= MemoryStore.MAX_PER_TYPE) {
            return true;
        }
        MemoryPoint point = new MemoryPoint(
                name, type, spirit.level().dimension().location().toString(), cell);
        spirit.updateMemory(memory.remember(point));
        recorded++;
        return true;
    }

    private String nameOf(String kind, BlockCoord pos) {
        String dimension = spirit.level().dimension().location().toString();
        String hash = Integer.toUnsignedString(Objects.hash(dimension, kind, pos), 36);
        return kind + "_" + hash;
    }

    private String outsideRefusal() {
        Optional<BlockCoord> home = spirit.home();
        if (home.isEmpty()) {
            return null;
        }
        NightLeash leash = NightLeash.withDefaultRules();
        boolean outside =
                route.stream().anyMatch(point -> !leash.insideSettlement(point, home.get(), leash.settlementRadius()));
        if (!outside) {
            return null;
        }
        OutsideGate gate = OutsideGate.withDefaults();
        OutsideGate.Decision decision = gate.admitExternal(
                partySize(), playerNear(home.get(), gate.escortRadius()), leash.isEvening(dayFraction()));
        return decision instanceof OutsideGate.Refused refused ? refused.reason() : null;
    }

    private int partySize() {
        spirit.countEntityQuery();
        int nearby = spirit.level()
                .getEntitiesOfClass(
                        SpiritEntity.class,
                        spirit.getBoundingBox().inflate(PARTY_RADIUS),
                        other -> other != spirit && other.isAlive())
                .size();
        return 1 + nearby;
    }

    private boolean playerNear(BlockCoord home, double radius) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (home.x() + 0.5);
            double dy = player.getY() - home.y();
            double dz = player.getZ() - (home.z() + 0.5);
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius) {
                return true;
            }
        }
        return false;
    }

    private double dayFraction() {
        return Math.floorMod(spirit.level().getDayTime(), TICKS_PER_DAY) / (double) TICKS_PER_DAY;
    }

    private static String kindOf(BlockState state) {
        if (state.isAir()) {
            return null;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.endsWith("_ore") || id.equals("ancient_debris")) {
            return "ore";
        }
        if (id.endsWith("_log") || id.endsWith("_wood") || id.equals("mushroom_stem")) {
            return "tree";
        }
        if (id.equals("water") || id.equals("bubble_column")) {
            return "water";
        }
        if (id.equals("lava")) {
            return "lava";
        }
        return null;
    }
}

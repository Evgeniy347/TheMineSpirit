package com.theminespirit.neoforge.fortify;

import com.theminespirit.core.building.DismantlePlan;
import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.FortifyDecision;
import com.theminespirit.core.intellect.FortifyMode;
import com.theminespirit.core.intellect.FortifyOrder;
import com.theminespirit.core.intellect.FortifyPlan;
import com.theminespirit.core.intellect.FortifyRoster;
import com.theminespirit.core.intellect.FarmWork;
import com.theminespirit.core.intellect.Forage;
import com.theminespirit.core.intellect.FortifyTuning;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;

import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class FortifyService {
    private static final double ENEMY_RADIUS = 32.0;
    private static final double PASSAGE_SCAN = 3.0;

    private final ServerLevel level;
    private final Map<UUID, FortifyExecutor> builders = new HashMap<>();
    private final Map<UUID, DismantleExecutor> dismantlers = new HashMap<>();
    private final Map<UUID, FortifyOrder> lastOrders = new HashMap<>();
    private final Map<UUID, Long> quietSince = new HashMap<>();
    private long lastDecisionTick = -1;
    private int decisionCalls;
    private int planCalls;

    public FortifyService(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("Служба укрепления без уровня не работает: мир не читается");
        }
        this.level = level;
    }

    public void tick(CoordinatorState state, List<SpiritEntity> members, long tick) {
        if (state == null) {
            throw new IllegalArgumentException("Служба укрепления без снимка координатора не работает");
        }
        if (members == null) {
            throw new IllegalArgumentException("Служба укрепления без духов не работает: пустое — пустой список");
        }
        tickBuilders(tick);
        tickDismantlers(tick);
        if (lastDecisionTick >= 0 && (tick - lastDecisionTick) < CoordinatorTuning.SNAPSHOT_INTERVAL_TICKS) {
            return;
        }
        lastDecisionTick = tick;
        decide(state, members, tick);
    }

    public int decisionCalls() {
        return decisionCalls;
    }

    public int planCalls() {
        return planCalls;
    }

    private void decide(CoordinatorState state, List<SpiritEntity> members, long tick) {
        List<EnemyFact> enemies = enemies(members);
        boolean retreatDifficult = retreatDifficult(enemies, members);
        SpiritEntity builderSpirit = anyBuilderSpirit(members);
        boolean passageNearby = builderSpirit != null && passageNearby(builderSpirit);
        SettlementView view = view(state);
        decisionCalls++;
        FortifyMode mode = FortifyDecision.decide(view, enemies, passageNearby, retreatDifficult);
        Optional<FortifyRoster> roster = FortifyDecision.roster(mode, state.spirits(), enemies);
        planCalls++;
        if (roster.isEmpty() || builderSpirit == null) {
            apply(FortifyPlan.empty(), state, members, tick);
            return;
        }
        FortifyPlan plan = FortifyPlan.plan(roster, feetOf(builderSpirit), passageLeft(builderSpirit),
                passageRight(builderSpirit));
        apply(plan, state, members, tick);
    }

    private void apply(FortifyPlan plan, CoordinatorState state, List<SpiritEntity> members, long tick) {
        if (plan.order().isEmpty()) {
            dismantleIfIdle(members, tick);
            return;
        }
        FortifyOrder order = plan.order().get();
        SpiritEntity builder = member(members, order.builder());
        if (builder == null) {
            return;
        }
        FortifyOrder previous = lastOrders.get(order.builder());
        if (!order.equals(previous)) {
            FortifyExecutor previousExecutor = builders.remove(order.builder());
            if (previousExecutor != null) {
                previousExecutor.releaseAll();
            }
            builders.put(order.builder(), new FortifyExecutor(builder, order));
            lastOrders.put(order.builder(), order);
        }
        for (UUID guard : order.guards()) {
            SpiritEntity guardSpirit = member(members, guard);
            if (guardSpirit == null) {
                continue;
            }
            guardSpirit.tactics().applyDirective(CombatDirective.defend(
                    new TargetSelector.Target(TargetSelector.Kind.SPIRIT, Optional.of(order.builder().toString()))));
        }
        quietSince.clear();
    }

    private void dismantleIfIdle(List<SpiritEntity> members, long tick) {
        for (UUID id : new ArrayList<>(lastOrders.keySet())) {
            SpiritEntity builder = member(members, id);
            if (builder == null) {
                continue;
            }
            long since = quietSince.computeIfAbsent(id, ignored -> tick);
            if (tick - since < FortifyTuning.FORTIFY_DISMANTLE_DELAY_TICKS) {
                continue;
            }
            if (dismantlers.containsKey(id)) {
                continue;
            }
            FortifyOrder order = lastOrders.get(id);
            List<BlockCoord> own = DismantlePlan.dismantle(order.cells(), cell ->
                    PlacedBlocksData.get(level).isSpiritMarked(new BlockPos(cell.x(), cell.y(), cell.z())));
            dismantlers.put(id, new DismantleExecutor(builder, own));
            lastOrders.remove(id);
            FortifyExecutor executor = builders.remove(id);
            if (executor != null) {
                executor.releaseAll();
            }
        }
    }

    private void tickBuilders(long tick) {
        for (UUID id : new ArrayList<>(builders.keySet())) {
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            FortifyExecutor executor = builders.get(id);
            if (spirit == null || spirit.isRemoved() || executor == null) {
                if (executor != null) {
                    executor.releaseAll();
                }
                builders.remove(id);
                continue;
            }
            executor.tick();
            if (executor.done()) {
                builders.remove(id);
            }
        }
    }

    private void tickDismantlers(long tick) {
        for (UUID id : new ArrayList<>(dismantlers.keySet())) {
            SpiritEntity spirit = level.getEntity(id) instanceof SpiritEntity se ? se : null;
            DismantleExecutor executor = dismantlers.get(id);
            if (spirit == null || spirit.isRemoved() || executor == null) {
                if (executor != null) {
                    executor.cancel();
                }
                dismantlers.remove(id);
                continue;
            }
            executor.tick();
            if (executor.done()) {
                dismantlers.remove(id);
                quietSince.remove(id);
            }
        }
    }

    private List<EnemyFact> enemies(List<SpiritEntity> members) {
        if (members.isEmpty()) {
            return List.of();
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (SpiritEntity spirit : members) {
            x += spirit.getX();
            y += spirit.getY();
            z += spirit.getZ();
        }
        Point center = new Point(x / members.size(), y / members.size(), z / members.size());
        AABB box = new AABB(center.x() - ENEMY_RADIUS, center.y() - ENEMY_RADIUS, center.z() - ENEMY_RADIUS,
                center.x() + ENEMY_RADIUS, center.y() + ENEMY_RADIUS, center.z() + ENEMY_RADIUS);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && (entity instanceof Enemy || entity instanceof Mob)
                        && !(entity instanceof SpiritEntity));
        List<EnemyFact> facts = new ArrayList<>();
        for (LivingEntity entity : near) {
            if (facts.size() >= FortifyTuning.FORTIFY_MAX_ENEMIES) {
                break;
            }
            double hp = entity.getMaxHealth() <= 0 ? 1.0 : entity.getHealth() / entity.getMaxHealth();
            facts.add(new EnemyFact(entity.getType().toString(), entity.getUUID().toString(),
                    new Point(entity.getX(), entity.getY(), entity.getZ()), Math.max(0.0, Math.min(1.0, hp)),
                    0.0, true, entity instanceof Mob mob && mob.getTarget() != null
                            && members.stream().anyMatch(m -> m == mob.getTarget())));
        }
        return facts;
    }

    private boolean retreatDifficult(List<EnemyFact> enemies, List<SpiritEntity> members) {
        return enemies.size() >= FortifyTuning.FORTIFY_ENEMY_MIN;
    }

    private SpiritEntity anyBuilderSpirit(List<SpiritEntity> members) {
        for (SpiritEntity spirit : members) {
            if (spirit.gear().blocks() > 0) {
                return spirit;
            }
        }
        return members.isEmpty() ? null : members.get(0);
    }

    private boolean passageNearby(SpiritEntity spirit) {
        BlockPos feet = spirit.blockPosition();
        return leftBank(feet).isPresent() && rightBank(feet).isPresent();
    }

    private Optional<BlockCoord> passageLeft(SpiritEntity spirit) {
        return spirit == null ? Optional.empty() : leftBank(spirit.blockPosition());
    }

    private Optional<BlockCoord> passageRight(SpiritEntity spirit) {
        return spirit == null ? Optional.empty() : rightBank(spirit.blockPosition());
    }

    private Optional<BlockCoord> leftBank(BlockPos feet) {
        for (int distance = 1; distance <= (int) PASSAGE_SCAN; distance++) {
            BlockPos candidate = feet.west(distance);
            if (solid(candidate)) {
                return Optional.of(new BlockCoord(candidate.getX(), candidate.getY(), candidate.getZ()));
            }
        }
        return Optional.empty();
    }

    private Optional<BlockCoord> rightBank(BlockPos feet) {
        for (int distance = 1; distance <= (int) PASSAGE_SCAN; distance++) {
            BlockPos candidate = feet.east(distance);
            if (solid(candidate)) {
                return Optional.of(new BlockCoord(candidate.getX(), candidate.getY(), candidate.getZ()));
            }
        }
        return Optional.empty();
    }

    private boolean solid(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos);
    }

    private static BlockCoord feetOf(SpiritEntity spirit) {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private SettlementView view(CoordinatorState state) {
        List<SpiritSnapshot> spirits = new ArrayList<>(state.spirits());
        long tick = state.tick();
        int population = spirits.size();
        return new SettlementView(population, 0, 0, 0, 0, 0, 0, 0, 0.5, FarmWork.NONE, Forage.NONE, spirits, tick);
    }

    private static SpiritEntity member(List<SpiritEntity> members, UUID id) {
        for (SpiritEntity spirit : members) {
            if (spirit.getUUID().equals(id)) {
                return spirit;
            }
        }
        return null;
    }

    Set<UUID> activeBuilders() {
        return new LinkedHashSet<>(builders.keySet());
    }
}
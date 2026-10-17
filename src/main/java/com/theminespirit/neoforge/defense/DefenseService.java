package com.theminespirit.neoforge.defense;

import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.intellect.defense.CombatStance;
import com.theminespirit.core.intellect.defense.CoverFinder;
import com.theminespirit.core.intellect.defense.DefenderCapability;
import com.theminespirit.core.intellect.defense.DefenderOrder;
import com.theminespirit.core.intellect.defense.DefenseDirectives;
import com.theminespirit.core.intellect.defense.DefenseIssuedOrder;
import com.theminespirit.core.intellect.defense.DefensePlan;
import com.theminespirit.core.intellect.defense.DefensePlanner;
import com.theminespirit.core.intellect.defense.DefenseProfile;
import com.theminespirit.core.intellect.defense.DefenseTuning;
import com.theminespirit.core.intellect.defense.TargetClass;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.intellect.SettlementScanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;

public final class DefenseService {
    private final EnemyProfiles enemies = TacticsConfig.loadDefault().enemies();
    private final BlockerProbe probe = new BlockerProbe();
    private DefensePlan lastPlan = new DefensePlan(Optional.empty(), List.of(), List.of());
    private Map<UUID, Point> refinedCovers = Map.of();
    private int entityQueries;
    private int planCalls;

    public Map<UUID, DefenseIssuedOrder> tick(ServerLevel level, List<SpiritEntity> members,
            CoordinatorState state, long tick) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: враги обороны стоят в мире");
        }
        if (members == null) {
            throw new IllegalArgumentException("members не может быть null: нет духов — пустой список");
        }
        if (state == null) {
            throw new IllegalArgumentException("state не может быть null: точка обороны берётся из состояния");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick = " + tick + ": тик обороны не может быть отрицательным");
        }
        if (state.defendedPoint().isEmpty()) {
            lastPlan = new DefensePlan(Optional.empty(), List.of(), List.of());
            refinedCovers = Map.of();
            return Map.of();
        }
        List<DefenderCapability> defenders = new ArrayList<>();
        for (SpiritEntity spirit : members) {
            if (spirit == null || spirit.isRemoved() || busy(spirit)) {
                continue;
            }
            DefenderReader.read(spirit).ifPresent(defenders::add);
        }
        List<EnemyFact> sighted =
                SettlementScanner.scan(level, members, state.spirits(), state.defendedPoint(), "").enemies();
        entityQueries++;
        planCalls++;
        DefensePlan plan = DefensePlanner.plan(state, defenders, sighted, enemies, profile());
        lastPlan = plan;
        Map<UUID, DefenseIssuedOrder> issued = DefenseDirectives.of(plan, charged(level, plan));
        refinedCovers = refineCovers(level, plan, defenders);
        return issued;
    }

    public DefensePlan lastPlan() {
        return lastPlan;
    }

    public Map<UUID, Point> covers() {
        return refinedCovers;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int planCalls() {
        return planCalls;
    }

    public int blockReads() {
        return probe.blockReads();
    }

    private static boolean busy(SpiritEntity spirit) {
        return spirit.currentTaskScript().isPresent()
                || spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
    }

    private Set<String> charged(ServerLevel level, DefensePlan plan) {
        Set<String> charged = new HashSet<>();
        for (DefenderOrder order : plan.orders()) {
            if (order.target().isEmpty()) {
                continue;
            }
            EnemyFact enemy = order.target().get();
            if (profile().classify(enemy.type()) != TargetClass.CREEPER) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(enemy.id());
            } catch (IllegalArgumentException notUuid) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (entity instanceof Creeper creeper && creeper.isPowered()) {
                charged.add(enemy.id());
            }
        }
        return charged;
    }

    private Map<UUID, Point> refineCovers(ServerLevel level, DefensePlan plan, List<DefenderCapability> defenders) {
        Map<UUID, Point> positions = new HashMap<>();
        for (DefenderCapability defender : defenders) {
            positions.put(defender.id(), defender.pos());
        }
        Map<UUID, Point> covers = new HashMap<>();
        for (DefenderOrder order : plan.orders()) {
            if (order.stance() != CombatStance.COVER_ENGAGE) {
                continue;
            }
            Point self = positions.get(order.defender());
            EnemyFact skeleton = nearestSkeleton(self, plan);
            if (self == null || skeleton == null) {
                continue;
            }
            List<Point> mobs = new ArrayList<>();
            for (DefenderOrder other : plan.orders()) {
                if (other.target().isPresent() && !other.target().get().id().equals(skeleton.id())) {
                    mobs.add(other.target().get().pos());
                }
            }
            List<Point> candidates = probe.probe(level, self, skeleton.pos(), mobs);
            CoverFinder.between(self, skeleton.pos(), candidates, DefenseTuning.COVER_CORRIDOR)
                    .ifPresent(cover -> covers.put(order.defender(), cover));
        }
        return Map.copyOf(covers);
    }

    private EnemyFact nearestSkeleton(Point self, DefensePlan plan) {
        if (self == null) {
            return null;
        }
        EnemyFact nearest = null;
        double nearestDistance = 0;
        for (DefenderOrder other : plan.orders()) {
            if (other.target().isEmpty()
                    || profile().classify(other.target().get().type()) != TargetClass.SKELETON) {
                continue;
            }
            EnemyFact enemy = other.target().get();
            double distance = enemy.pos().distanceTo(self);
            if (distance > DefenseTuning.RUSH_MARGIN) {
                continue;
            }
            if (nearest == null || distance < nearestDistance
                    || (distance == nearestDistance && enemy.id().compareTo(nearest.id()) < 0)) {
                nearest = enemy;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static DefenseProfile profile() {
        return DefenseProfile.loadDefault();
    }
}

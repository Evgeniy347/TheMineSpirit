package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.LifeEvents;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class LifeInputs {
    private static final double RADIUS = 16.0;
    private static final float FALL_RISK = 3.0F;
    private static final double FALLING_SPEED = -0.3;
    private static final float LOW_HEALTH = 0.35F;

    private LifeInputs() {}

    public record Input(List<GraphFacts.Threat> threats, Set<String> events) {}

    public static Input of(ServerLevel level, List<SpiritEntity> spirits, Set<String> baseEvents) {
        List<GraphFacts.Threat> threats = threats(level, spirits);
        List<LifeEvents.Condition> conditions = conditions(spirits);
        return new Input(threats, LifeEvents.events(baseEvents, threats, conditions));
    }

    public static List<GraphFacts.Threat> threats(ServerLevel level, List<SpiritEntity> spirits) {
        if (spirits.isEmpty()) {
            return List.of();
        }
        AABB area = area(spirits);
        List<Monster> found = level.getEntitiesOfClass(Monster.class, area, Monster::isAlive);
        found.sort(Comparator.comparing(monster -> monster.getUUID().toString()));
        List<GraphFacts.Threat> threats = new ArrayList<>(found.size());
        for (Monster monster : found) {
            threats.add(threat(monster, spirits));
        }
        return List.copyOf(threats);
    }

    private static AABB area(List<SpiritEntity> spirits) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (SpiritEntity spirit : spirits) {
            minX = Math.min(minX, spirit.getX() - RADIUS);
            minY = Math.min(minY, spirit.getY() - RADIUS);
            minZ = Math.min(minZ, spirit.getZ() - RADIUS);
            maxX = Math.max(maxX, spirit.getX() + RADIUS);
            maxY = Math.max(maxY, spirit.getY() + RADIUS);
            maxZ = Math.max(maxZ, spirit.getZ() + RADIUS);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static GraphFacts.Threat threat(Monster monster, List<SpiritEntity> spirits) {
        LivingEntity target = target(monster);
        LivingEntity hurtBy = monster.getLastHurtByMob();
        return new GraphFacts.Threat(
                monster.getUUID().toString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType()).toString(),
                monster.getHealth(),
                monster.getMaxHealth(),
                new GraphFacts.Position(
                        monster.blockPosition().getX(),
                        monster.blockPosition().getY(),
                        monster.blockPosition().getZ()),
                target == null ? null : target.getUUID().toString(),
                kind(target),
                target != null && spirits.stream().anyMatch(spirit -> spirit.equals(target)),
                spirits.stream().anyMatch(spirit -> monster.hasLineOfSight(spirit)),
                state(monster),
                distance(monster, spirits),
                hurtBy == null ? null : hurtBy.getUUID().toString(),
                monster.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    private static LivingEntity target(Monster monster) {
        LivingEntity target = monster.getTarget();
        return target != null ? target : monster.getLastHurtByMob();
    }

    static GraphFacts.TargetKind kind(Entity target) {
        if (target == null) {
            return GraphFacts.TargetKind.NONE;
        }
        if (target instanceof SpiritEntity) {
            return GraphFacts.TargetKind.SPIRIT;
        }
        if (target instanceof Player) {
            return GraphFacts.TargetKind.PLAYER;
        }
        if (target instanceof Animal) {
            return GraphFacts.TargetKind.ANIMAL;
        }
        return GraphFacts.TargetKind.OTHER;
    }

    private static int distance(Monster monster, List<SpiritEntity> spirits) {
        LivingEntity target = monster.getTarget();
        double nearest = target instanceof SpiritEntity
                ? monster.distanceToSqr(target)
                : spirits.stream()
                        .mapToDouble(spirit -> monster.distanceToSqr(spirit))
                        .min()
                        .orElse(0);
        return (int) Math.round(Math.sqrt(nearest));
    }

    private static String state(Monster monster) {
        List<String> state = new ArrayList<>();
        if (monster.isOnFire()) {
            state.add("burning");
        }
        if (monster.isInvisible()) {
            state.add("invisible");
        }
        if (monster.isBaby()) {
            state.add("baby");
        }
        if (monster.isInWater()) {
            state.add("in_water");
        }
        if (monster instanceof Creeper creeper) {
            if (creeper.isPowered()) {
                state.add("charged");
            }
            if (creeper.getSwelling(1.0F) > 0.0F) {
                state.add("swelling");
            }
        }
        monster.getActiveEffects().stream().map(LifeInputs::effect).sorted().forEach(state::add);
        ItemStack held = monster.getMainHandItem();
        if (!held.isEmpty()) {
            state.add("held:" + BuiltInRegistries.ITEM.getKey(held.getItem()));
        }
        return state.isEmpty() ? null : String.join(",", state);
    }

    private static String effect(MobEffectInstance effect) {
        Optional<String> id =
                effect.getEffect().unwrapKey().map(key -> key.location().toString());
        return "effect:" + id.orElse("unknown");
    }

    private static List<LifeEvents.Condition> conditions(List<SpiritEntity> spirits) {
        List<LifeEvents.Condition> conditions = new ArrayList<>(spirits.size());
        for (SpiritEntity spirit : spirits) {
            boolean inWater = spirit.isInWater();
            boolean lowAir = spirit.getAirSupply() < spirit.getMaxAirSupply();
            conditions.add(new LifeEvents.Condition(
                    inWater && lowAir,
                    !inWater && lowAir,
                    spirit.fallDistance > FALL_RISK,
                    spirit.fallDistance > 1 && spirit.getDeltaMovement().y < FALLING_SPEED,
                    spirit.isOnFire(),
                    spirit.hasEffect(MobEffects.POISON),
                    GraphFood.hungry(List.of(spirit)),
                    spirit.getMaxHealth() > 0 && spirit.getHealth() / spirit.getMaxHealth() < LOW_HEALTH));
        }
        return List.copyOf(conditions);
    }
}

package com.theminespirit.neoforge;

import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;

public final class ScriptTargetResolver {
    private static final TacticsAdapterSettings SETTINGS = TacticsAdapterSettings.loadDefault();

    private ScriptTargetResolver() {}

    public static Optional<LivingEntity> resolve(SpiritEntity spirit, TargetSelector.Target target) {
        if (spirit == null) {
            throw new NullPointerException("Дух = null: цель разрешается относительно духа, который её ищет");
        }
        if (target == null) {
            throw new NullPointerException("Цель = null: сначала разберите строку через TargetSelector.parse, "
                    + "отсутствие цели — это Optional.empty(), а не null");
        }
        spirit.countEntityQuery();
        return switch (target.kind()) {
            case PLAYER -> playerByName(spirit, target.name().orElseThrow());
            case SPIRIT -> spiritByUuid(spirit, target.name().orElseThrow());
            case HOSTILE -> nearest(spirit, entity -> entity instanceof Enemy);
            case ANIMAL -> nearest(spirit, entity -> entity instanceof Animal);
            case NEAREST, ANY -> nearest(spirit, entity -> true);
        };
    }

    private static Optional<LivingEntity> playerByName(SpiritEntity spirit, String name) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        for (ServerPlayer player : level.players()) {
            if (player.getName().getString().equals(name)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    private static Optional<LivingEntity> spiritByUuid(SpiritEntity spirit, String uuid) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        try {
            Entity entity = level.getEntity(UUID.fromString(uuid));
            return entity instanceof SpiritEntity other && other.isAlive() ? Optional.of(other) : Optional.empty();
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    private static Optional<LivingEntity> nearest(SpiritEntity spirit, Predicate<LivingEntity> fits) {
        double radius = SETTINGS.factRadius();
        return spirit
                .level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        spirit.getBoundingBox().inflate(radius),
                        entity -> !entity.equals(spirit)
                                && entity.isAlive()
                                && fits.test(entity)
                                && entity.distanceTo(spirit) <= radius)
                .stream()
                .min(Comparator.comparingDouble((LivingEntity entity) -> entity.distanceToSqr(spirit.position()))
                        .thenComparing(entity -> entity.getUUID().toString()));
    }
}

package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.HerdCap;
import com.theminespirit.core.husbandry.PetKind;
import com.theminespirit.core.husbandry.PetRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdActions;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

final class PetWork {
    private static final int RADIUS = 32;
    private static final double REACH_SQR = 9.0;
    private static final int FEEDS_TO_TRUST = 3;
    private static final int BREED_COOLDOWN = 1200;
    private static final double APPROACH_SPEED = 0.6;

    private final Map<UUID, Integer> feeds = new HashMap<>();
    private final Map<PetKind, Long> nextBreed = new EnumMap<>(PetKind.class);

    boolean tame(ServerLevel level, List<SpiritEntity> spirits, PetKind kind) {
        return tameStep(level, kind, wild(level, spirits, kind), spirits);
    }

    boolean breed(ServerLevel level, List<SpiritEntity> spirits, PetKind kind, long now) {
        return breedStep(level, kind, own(level, spirits, kind), spirits, now);
    }

    private static List<PetRules.PetWay> petWays() {
        return DataTables.INSTANCE.graph().current().ways().stream()
                .map(way -> new PetRules.PetWay(
                        way.id(),
                        way.boardCase(),
                        way.makes(),
                        List.copyOf(way.consumes().keySet())))
                .toList();
    }

    private static Map<String, HerdCap> herdCaps() {
        return DataTables.INSTANCE.graph().current().herdCaps();
    }

    private static List<Animal> wild(ServerLevel level, List<SpiritEntity> spirits, PetKind kind) {
        return animals(level, homeOf(spirits)).stream()
                .filter(animal -> kindOf(animal).filter(kind::equals).isPresent())
                .filter(animal -> !owned(kind, animal) && !animal.isBaby())
                .toList();
    }

    private static List<Animal> own(ServerLevel level, List<SpiritEntity> spirits, PetKind kind) {
        return animals(level, homeOf(spirits)).stream()
                .filter(animal -> kindOf(animal).filter(kind::equals).isPresent())
                .filter(animal -> owned(kind, animal))
                .toList();
    }

    private boolean tameStep(ServerLevel level, PetKind kind, List<Animal> wild, List<SpiritEntity> spirits) {
        if (wild.isEmpty()) {
            return false;
        }
        List<String> foods = PetRules.tameFoods(kind, petWays());
        Animal target = wild.get(0);
        SpiritEntity worker = workerWith(spirits, foods);
        if (worker == null) {
            return false;
        }
        if (worker.distanceToSqr(target) > REACH_SQR) {
            worker.getNavigation().moveTo(target, APPROACH_SPEED);
            return true;
        }
        if (!consume(worker, foods)) {
            return false;
        }
        int done = feeds.merge(target.getUUID(), 1, Integer::sum);
        if (done < FEEDS_TO_TRUST) {
            heart(level, target);
            return true;
        }
        feeds.remove(target.getUUID());
        trust(kind, target, worker);
        heart(level, target);
        return true;
    }

    private boolean breedStep(ServerLevel level, PetKind kind, List<Animal> own, List<SpiritEntity> spirits, long now) {
        List<Animal> parents = own.stream().filter(animal -> !animal.isBaby()).toList();
        if (parents.size() < 2
                || parents.size() >= PetRules.cap(kind, spirits.size(), herdCaps())
                || now < nextBreed.getOrDefault(kind, 0L)) {
            return false;
        }
        List<String> foods = PetRules.breedFoods(kind, petWays());
        Animal first = parents.get(0);
        Animal second = parents.get(1);
        SpiritEntity worker = workerWith(spirits, foods);
        if (worker == null) {
            return false;
        }
        if (worker.distanceToSqr(first) > REACH_SQR) {
            worker.getNavigation().moveTo(first, APPROACH_SPEED);
            return true;
        }
        if (count(worker, foods) < 2) {
            return false;
        }
        consume(worker, foods);
        consume(worker, foods);
        first.setInLove(null);
        second.setInLove(null);
        first.spawnChildFromBreeding(level, second);
        nextBreed.put(kind, now + BREED_COOLDOWN);
        heart(level, first);
        heart(level, second);
        return true;
    }

    private static void trust(PetKind kind, Animal target, SpiritEntity worker) {
        switch (kind) {
            case WOLF -> {
                Wolf wolf = (Wolf) target;
                wolf.setTame(true, false);
                wolf.setOwnerUUID(worker.getUUID());
            }
            case CAT -> {
                Cat cat = (Cat) target;
                cat.setTame(true, false);
                cat.setOwnerUUID(worker.getUUID());
            }
            case OCELOT -> ((Ocelot) target).setTrusting(true);
        }
    }

    private static boolean owned(PetKind kind, Animal animal) {
        return switch (kind) {
            case WOLF -> animal instanceof Wolf wolf && wolf.isTame();
            case CAT -> animal instanceof Cat cat && cat.isTame();
            case OCELOT -> animal instanceof Ocelot ocelot && ocelot.isTrusting();
        };
    }

    static Optional<PetKind> kindOf(Animal animal) {
        if (animal instanceof Wolf) {
            return Optional.of(PetKind.WOLF);
        }
        if (animal instanceof Cat) {
            return Optional.of(PetKind.CAT);
        }
        if (animal instanceof Ocelot) {
            return Optional.of(PetKind.OCELOT);
        }
        return Optional.empty();
    }

    private static SpiritEntity workerWith(List<SpiritEntity> spirits, List<String> foods) {
        for (SpiritEntity spirit : spirits) {
            if (!spirit.isRemoved() && count(spirit, foods) > 0) {
                return spirit;
            }
        }
        return null;
    }

    private static int count(SpiritEntity spirit, List<String> foods) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (isFood(stack, foods)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean consume(SpiritEntity spirit, List<String> foods) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (isFood(stack, foods)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean isFood(ItemStack stack, List<String> foods) {
        for (String food : foods) {
            Item item = HerdActions.registryItem(food.contains(":") ? food : "minecraft:" + food);
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static List<Animal> animals(ServerLevel level, BlockCoord home) {
        AABB box = new AABB(
                home.x() - RADIUS,
                home.y() - RADIUS,
                home.z() - RADIUS,
                home.x() + RADIUS,
                home.y() + RADIUS,
                home.z() + RADIUS);
        return new ArrayList<>(level.getEntitiesOfClass(Animal.class, box));
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                return home.orElseThrow();
            }
        }
        BlockPos pos = spirits.get(0).blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void heart(ServerLevel level, Animal animal) {
        level.sendParticles(
                ParticleTypes.HEART, animal.getX(), animal.getY() + 0.8, animal.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
    }
}

package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.PenBounds;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Отлов приманкой (упрощение 44.4, согласовано владельцем 18.10): кольца из блоков нет. Дух с кормом вида в
 * инвентаре подходит к ближайшему животному вне загона и ведёт его в загон у дома — животное идёт к духу, как
 * за игроком с кормом. Успех — животное внутри границы загона.
 */
public final class CaptureSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private static final double PICKUP_DISTANCE = 3.0;
    private static final double FOLLOW_DISTANCE = 5.0;
    private static final double LURE_SPEED = 1.0;
    private static final int LURE_INSET = 2;
    private static final int STEER_EVERY_TICKS = 10;

    private final SpiritEntity spirit;
    private final Species species;
    private final int searchRadius;
    private final Approach approach;

    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private Animal target;
    private UUID targetId;
    private PenBounds pen;
    private int ticks;
    private boolean leadingToAnimal;

    public CaptureSession(SpiritEntity spirit, Species species, int radius) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому ловить");
        this.species = Objects.requireNonNull(species, "Вид не может быть null: без него неизвестно, кого ловить");
        if (radius < CatalogLimits.CAPTURE_RADIUS_MIN || radius > CatalogLimits.CAPTURE_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус отлова " + radius + " вне диапазона ["
                    + CatalogLimits.CAPTURE_RADIUS_MIN + ", " + CatalogLimits.CAPTURE_RADIUS_MAX
                    + "]: дальше животное за кормом не пойдёт");
        }
        this.searchRadius = radius;
        this.approach = new Approach(spirit);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время отлова");
        }
        if (++ticks > CatalogLimits.CAPTURE_LEAD_TICKS) {
            return fail("не довёл " + speciesId() + " до загона за " + CatalogLimits.CAPTURE_LEAD_TICKS + " тиков");
        }
        if (pen == null) {
            BlockCoord home = spirit.home().orElse(null);
            if (home == null) {
                return fail("у духа нет дома: загон стоит у дома, вести животное некуда (/spirit home)");
            }
            pen = new PenBounds(home, CatalogLimits.CAPTURE_PEN_RADIUS);
        }
        if (!hasFeed()) {
            return fail("нет корма для приманки " + speciesId() + ": нужен один из " + FeedTable.feedFor(species));
        }
        if (target == null || !target.isAlive() || target.isRemoved()) {
            target = findTarget();
            if (target == null) {
                return fail("рядом нет " + speciesId() + " вне загона в радиусе " + searchRadius);
            }
            targetId = target.getUUID();
        }
        if (pen.inside(coord(target.blockPosition()))) {
            target.getNavigation().stop();
            approach.cancel();
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        lead();
        return outcome;
    }

    private void lead() {
        boolean towardsAnimal = spirit.distanceTo(target) > FOLLOW_DISTANCE;
        if (towardsAnimal != leadingToAnimal || !approach.isActive()) {
            approach.cancel();
            leadingToAnimal = towardsAnimal;
            if (towardsAnimal) {
                approach.begin(target, PICKUP_DISTANCE);
            } else {
                BlockCoord inside = pen.lureTarget(coord(target.blockPosition()), LURE_INSET);
                approach.begin(new Vec3(inside.x() + 0.5, inside.y(), inside.z() + 0.5), 1.0);
            }
        }
        if (approach.tick() != Approach.State.MOVING) {
            approach.cancel();
        }
        if (ticks % STEER_EVERY_TICKS == 0 && spirit.distanceTo(target) > 1.5) {
            target.getNavigation().moveTo(spirit, LURE_SPEED);
            target.getLookControl().setLookAt(spirit);
        }
    }

    private Animal findTarget() {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return null;
        }
        if (targetId != null && level.getEntity(targetId) instanceof Animal known && known.isAlive()) {
            return known;
        }
        AABB box = spirit.getBoundingBox().inflate(searchRadius);
        List<Animal> found = level.getEntitiesOfClass(
                Animal.class,
                box,
                animal -> animal.isAlive()
                        && speciesId()
                                .equals(BuiltInRegistries.ENTITY_TYPE
                                        .getKey(animal.getType())
                                        .toString())
                        && !pen.inside(coord(animal.blockPosition()))
                        && !animal.isLeashed());
        return found.stream()
                .min(Comparator.comparingDouble((Entity animal) -> animal.distanceToSqr(spirit))
                        .thenComparing(Entity::getStringUUID))
                .map(Animal.class::cast)
                .orElse(null);
    }

    private boolean hasFeed() {
        for (String id : FeedTable.feedFor(species)) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (spirit.getInventory().countItem(item) > 0) {
                return true;
            }
        }
        return false;
    }

    private String speciesId() {
        return "minecraft:" + species.name().toLowerCase(Locale.ROOT);
    }

    private Outcome fail(String reason) {
        cancel();
        failureReason = reason;
        outcome = Outcome.FAILED;
        return outcome;
    }

    public void suspend() {
        approach.suspend();
    }

    public void cancel() {
        approach.cancel();
        if (target != null) {
            target.getNavigation().stop();
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(outcome == Outcome.SUCCEEDED ? 1 : 0, 1);
    }

    public Map<String, Object> resultData() {
        Map<String, Object> data = new HashMap<>();
        data.put("caught", species.name().toLowerCase(Locale.ROOT));
        if (target != null) {
            BlockPos at = target.blockPosition();
            data.put("at", List.of(at.getX(), at.getY(), at.getZ()));
        }
        return Map.copyOf(data);
    }

    public Map<String, Object> saveState() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("v", 2);
        if (targetId != null) {
            saved.put("target", targetId.toString());
        }
        saved.put("ticks", ticks);
        return Map.copyOf(saved);
    }

    public void restoreState(Map<String, Object> saved) {
        Objects.requireNonNull(saved, "Состояние не может быть null: пустое задают пустой картой");
        if (saved.get("target") instanceof String id && !id.isBlank()) {
            try {
                targetId = UUID.fromString(id);
            } catch (IllegalArgumentException broken) {
                throw new IllegalArgumentException(
                        "Состояние capture: target '" + id + "' — не UUID животного: снимок задачи повреждён", broken);
            }
        }
        if (saved.get("ticks") instanceof Number number) {
            ticks = Math.max(ticks, number.intValue());
        }
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}

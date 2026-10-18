package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.fishing.BucketCatch;
import com.theminespirit.core.fishing.FishBuckets;
import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.script.BucketFishParams;
import com.theminespirit.neoforge.crafting.ItemIds;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ловля рыбы ведром: дух находит рыбу в воде рядом, подходит и сажает её в пустое ведро.
 * Заход ограничен {@link FishingData#bucketCount()} — числом из fishing.json. Ведёрная рыба
 * идёт на будущие пруды, поэтому её берут отдельно от удочки и не больше заданного предела.
 */
public final class BucketFishScript implements ScriptInstance {
    private static final double ARRIVAL = 1.6;
    private static final double REACH = 3.0;
    private static final int TIMEOUT_PER_BLOCK = 40;
    private static final int TIMEOUT_RESERVE = 200;

    private enum Phase {
        FIND,
        APPROACH,
        CATCH
    }

    private final SpiritEntity spirit;
    private final BucketFishParams params;
    private final Approach approach;
    private final int limit;
    private final int timeoutTicks;
    private Phase phase = Phase.FIND;
    private Entity target;
    private int caught;
    private int elapsed;
    private String failureReason;

    public BucketFishScript(SpiritEntity spirit, BucketFishParams params) {
        this.spirit = spirit;
        this.params = params;
        this.limit = BucketCatch.limit(params.count(), FishingData.loadDefault());
        this.approach = new Approach(spirit);
        this.timeoutTicks = params.radius() * TIMEOUT_PER_BLOCK + TIMEOUT_RESERVE;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время ловли ведром");
        }
        if (caught >= limit) {
            return ScriptStatus.SUCCEEDED;
        }
        if (elapsed++ >= timeoutTicks) {
            return caught > 0 ? ScriptStatus.SUCCEEDED
                    : fail("за " + timeoutTicks + " тиков ведром не поймано ни одной рыбы");
        }
        return switch (phase) {
            case FIND -> find();
            case APPROACH -> approachStep();
            case CATCH -> catchStep();
        };
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(Math.min(caught, limit), limit));
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Data(Map.of("caught", caught, "limit", limit)));
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of("caught", caught, "ticks", elapsed);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get("caught") instanceof Number number && number.intValue() >= 0) {
            caught = number.intValue();
        }
        if (state.get("ticks") instanceof Number number && number.intValue() >= 0) {
            elapsed = number.intValue();
        }
    }

    private ScriptStatus find() {
        if (emptyBucketSlot() < 0) {
            return fail("ловля ведром без пустого ведра невозможна: в инвентаре духа нет пустого ведра");
        }
        target = nearestFish();
        if (target == null) {
            return fail("рядом нет рыбы для ведра в радиусе " + params.radius());
        }
        approach.begin(target, ARRIVAL);
        phase = Phase.APPROACH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approachStep() {
        if (target == null || target.isRemoved()) {
            target = null;
            phase = Phase.FIND;
            return ScriptStatus.RUNNING;
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                phase = Phase.CATCH;
                yield catchStep();
            }
            case STUCK, TIMEOUT -> fail("не подошёл к рыбе для ведра");
        };
    }

    private ScriptStatus catchStep() {
        if (target == null || target.isRemoved() || !target.isAlive()) {
            target = null;
            phase = Phase.FIND;
            return ScriptStatus.RUNNING;
        }
        if (spirit.position().distanceTo(target.position()) > REACH) {
            approach.begin(target, ARRIVAL);
            phase = Phase.APPROACH;
            return ScriptStatus.RUNNING;
        }
        String bucketId = FishBuckets.bucketItem(entityTypeId()).orElse(null);
        if (bucketId == null) {
            return fail("рыба '" + entityTypeId() + "' в ведро не садится");
        }
        int slot = emptyBucketSlot();
        if (slot < 0) {
            return fail("пустое ведро кончилось: поймано " + caught + " из " + limit);
        }
        Optional<Item> bucket = ItemIds.item(bucketId);
        if (bucket.isEmpty()) {
            return fail("предмет ведра '" + bucketId + "' отсутствует в реестре");
        }
        spirit.getInventory().removeItem(slot, 1);
        target.discard();
        ItemStack filled = new ItemStack(bucket.get());
        if (!spirit.getInventory().addItem(filled).isEmpty()) {
            return fail("инвентарь полон: ведро с рыбой не помещается");
        }
        caught++;
        target = null;
        phase = Phase.FIND;
        return caught >= limit ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
    }

    private Entity nearestFish() {
        Vec3 center = spirit.position();
        double radius = params.radius();
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : spirit.level().getEntities(spirit, box,
                candidate -> !candidate.isRemoved() && FishBuckets.isBucketable(entityTypeId(candidate)))) {
            double distance = spirit.distanceToSqr(entity);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private String entityTypeId() {
        return entityTypeId(target);
    }

    private static String entityTypeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private int emptyBucketSlot() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (spirit.getInventory().getItem(slot).is(Items.BUCKET)) {
                return slot;
            }
        }
        return -1;
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

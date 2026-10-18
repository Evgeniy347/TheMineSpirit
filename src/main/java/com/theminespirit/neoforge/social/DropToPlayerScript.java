package com.theminespirit.neoforge.social;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.DropToPlayerParams;
import com.theminespirit.core.social.SocialTuning;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DropToPlayerScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final DropToPlayerParams params;
    private final Approach approach;
    private Item item;
    private LivingEntity target;
    private int dropped;
    private boolean started;
    private String failureReason;
    private ScriptResult result;

    public DropToPlayerScript(SpiritEntity spirit, DropToPlayerParams params) {
        this.spirit = spirit;
        this.params = params;
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез по дороге с грузом '" + params.item() + "'");
        }
        if (!started) {
            Optional<Item> found = BuiltInRegistries.ITEM
                    .getOptional(ResourceLocation.parse(
                            params.item().contains(":") ? params.item() : "minecraft:" + params.item()));
            if (found.isEmpty()) {
                return fail("предмет '" + params.item() + "' не зарегистрирован: бросать нечего");
            }
            item = found.get();
            Optional<LivingEntity> addressee = ScriptTargetResolver.resolve(spirit, TargetSelector.parse(params.to()));
            if (addressee.isEmpty()) {
                return fail("адресат '" + params.to() + "' не найден: бросать некому");
            }
            target = addressee.get();
            int available = countInInventory();
            if (available < params.count()) {
                return fail("не хватает предмета '" + params.item() + "': нужно " + params.count()
                        + ", в инвентаре " + available);
            }
            approach.begin(target, SocialTuning.DELIVER_ARRIVAL);
            started = true;
        }
        if (target.isRemoved() || !target.isAlive()) {
            approach.cancel();
            return fail("адресат '" + params.to() + "' ушёл по дороге: бросок сорван");
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> dropToTarget();
            case TIMEOUT -> fail("не подошёл к адресату '" + params.to() + "' за " + approach.timeoutTicks()
                    + " тиков");
            case STUCK -> fail("застрял по дороге к адресату '" + params.to() + "' в (x=" + spirit.getX()
                    + ", y=" + spirit.getY() + ", z=" + spirit.getZ() + ")");
        };
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
        return Optional.ofNullable(result);
    }

    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("dropped", dropped);
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        Object saved = state.get("dropped");
        if (saved instanceof Number number) {
            dropped = number.intValue();
        }
    }

    private ScriptStatus dropToTarget() {
        int taken = takeFromInventory(params.count());
        if (taken == 0) {
            return fail("не удалось взять '" + params.item() + "': инвентарь изменился по дороге");
        }
        ItemEntity drop = new ItemEntity(spirit.level(), target.getX(), target.getY() + 0.5, target.getZ(),
                new ItemStack(item, taken));
        drop.setDeltaMovement(0.0, 0.0, 0.0);
        drop.setPickUpDelay(40);
        spirit.level().addFreshEntity(drop);
        dropped += taken;
        approach.cancel();
        result = new ScriptResult.Data(Map.of(
                "item", params.item(),
                "count", params.count(),
                "dropped", dropped,
                "to", params.to()));
        return ScriptStatus.SUCCEEDED;
    }

    private int countInInventory() {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int takeFromInventory(int wanted) {
        int remaining = wanted;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return wanted - remaining;
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

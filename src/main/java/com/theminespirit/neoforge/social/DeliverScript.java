package com.theminespirit.neoforge.social;

import com.theminespirit.core.board.DeliverRetry;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.DeliverParams;
import com.theminespirit.core.social.SocialTuning;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.DropCollector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DeliverScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final DeliverParams params;
    private final Approach approach;
    private Item item;
    private LivingEntity target;

    private int delivered;
    private int restarts;
    private boolean started;
    private String failureReason;
    private ScriptResult result;

    public DeliverScript(SpiritEntity spirit, DeliverParams params) {
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
        if (delivered >= params.count()) {
            return succeed();
        }
        if (!started) {
            Optional<Item> found = BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse(params.item().contains(":") ? params.item() : "minecraft:" + params.item()));
            if (found.isEmpty()) {
                return fail("предмет '" + params.item() + "' не зарегистрирован: доставлять нечего");
            }
            item = found.get();
            Optional<LivingEntity> addressee = ScriptTargetResolver.resolve(spirit, TargetSelector.parse(params.to()));
            if (addressee.isEmpty()) {
                return fail("адресат '" + params.to() + "' не найден: доставлять некому");
            }
            target = addressee.get();
            int remaining = params.count() - delivered;
            int available = countInInventory();
            if (available < remaining) {
                return fail("не хватает предмета '" + params.item() + "': нужно " + remaining + ", в инвентаре "
                        + available + ", передано " + delivered);
            }
            approach.begin(target, SocialTuning.DELIVER_ARRIVAL);
            started = true;
        }

        if (target.isRemoved() || !target.isAlive()) {
            approach.cancel();
            return fail("адресат '" + params.to() + "' ушёл по дороге: доставка сорвана");
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> transfer();
            case TIMEOUT ->
                retryOrFail("не подошёл к адресату '" + params.to() + "' за " + approach.timeoutTicks() + " тиков");
            case STUCK ->
                retryOrFail("застрял по дороге к адресату '" + params.to() + "' в (x=" + spirit.getX() + ", y="
                        + spirit.getY() + ", z=" + spirit.getZ() + ")");
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
        state.put("delivered", delivered);
        state.put("restarts", restarts);
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        Object saved = state.get("delivered");
        if (saved instanceof Number number) {
            delivered = number.intValue();
        }
        Object retried = state.get("restarts");
        if (retried instanceof Number number) {
            restarts = number.intValue();
        }
    }

    private ScriptStatus transfer() {
        int taken = takeFromInventory(params.count() - delivered);
        ItemStack handoff = new ItemStack(item, taken);
        if (target instanceof ServerPlayer player) {
            ItemStack leftover = addToInventory(player, handoff);
            if (!leftover.isEmpty()) {
                DropCollector.drop(spirit, leftover);
            }
        } else if (target instanceof SpiritEntity addressee) {
            ItemStack leftover = addToContainer(addressee.getInventory(), handoff);
            if (!leftover.isEmpty()) {
                DropCollector.drop(addressee, leftover);
            }
        } else {
            DropCollector.drop(spirit, handoff);
        }
        delivered += taken;
        return succeed();
    }

    private static ItemStack addToInventory(ServerPlayer player, ItemStack stack) {
        return addToContainer(player.getInventory(), stack);
    }

    private static ItemStack addToContainer(Container inventory, ItemStack stack) {
        ItemStack leftover = stack.copy();
        for (int slot = 0; slot < inventory.getContainerSize() && !leftover.isEmpty(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                inventory.setItem(slot, leftover.copy());
                leftover = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(existing, leftover)
                    && existing.getCount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int move = Math.min(space, leftover.getCount());
                existing.grow(move);
                leftover.shrink(move);
            }
        }
        return leftover;
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

    private ScriptStatus retryOrFail(String reason) {
        if (target == null || target.isRemoved() || !target.isAlive() || !DeliverRetry.canRetry(restarts)) {
            return fail(reason + " (попыток подхода: " + (restarts + 1) + ")");
        }
        restarts++;
        Optional<LivingEntity> again = ScriptTargetResolver.resolve(spirit, TargetSelector.parse(params.to()));
        if (again.isEmpty()) {
            return fail("адресат '" + params.to() + "' ушёл по дороге: доставка сорвана");
        }
        target = again.get();
        approach.begin(target, SocialTuning.DELIVER_ARRIVAL);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus succeed() {
        approach.cancel();
        result = new ScriptResult.Data(Map.of(
                "item", params.item(),
                "count", params.count(),
                "delivered", delivered,
                "to", params.to()));
        return ScriptStatus.SUCCEEDED;
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

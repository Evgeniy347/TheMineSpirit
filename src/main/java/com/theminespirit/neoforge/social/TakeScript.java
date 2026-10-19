package com.theminespirit.neoforge.social;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.TakeParams;
import com.theminespirit.core.social.SocialTuning;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.DropCollector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class TakeScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final TakeParams params;
    private final Approach approach;
    private Item item;
    private LivingEntity source;
    private int taken;
    private boolean started;
    private String failureReason;
    private ScriptResult result;

    public TakeScript(SpiritEntity spirit, TakeParams params) {
        this.spirit = spirit;
        this.params = params;
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез по дороге за предметом '" + params.item() + "'");
        }
        if (!started) {
            Optional<Item> found = BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse(params.item().contains(":") ? params.item() : "minecraft:" + params.item()));
            if (found.isEmpty()) {
                return fail("предмет '" + params.item() + "' не зарегистрирован: брать нечего");
            }
            item = found.get();
            Optional<LivingEntity> giver = ScriptTargetResolver.resolve(spirit, TargetSelector.parse(params.from()));
            if (giver.isEmpty() || !(giver.get() instanceof ServerPlayer || giver.get() instanceof SpiritEntity)) {
                return fail("источник '" + params.from() + "' не найден: брать не у кого");
            }
            source = giver.get();
            int available = countIn(source);
            if (available < params.count()) {
                return fail("не хватает предмета '" + params.item() + "': нужно " + params.count() + ", у источника "
                        + available);
            }
            if (!fitsInSpirit(params.count())) {
                return fail("нет места в инвентаре духа для " + params.count() + " шт '" + params.item()
                        + "': брать некуда");
            }
            approach.begin(source, SocialTuning.DELIVER_ARRIVAL);
            started = true;
        }
        if (source.isRemoved() || !source.isAlive()) {
            approach.cancel();
            return fail("источник '" + params.from() + "' ушёл по дороге: брать не у кого");
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> takeFromSource();
            case TIMEOUT ->
                fail("не подошёл к источнику '" + params.from() + "' за " + approach.timeoutTicks() + " тиков");
            case STUCK ->
                fail("застрял по дороге к источнику '" + params.from() + "' в (x=" + spirit.getX() + ", y="
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
        state.put("taken", taken);
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        Object saved = state.get("taken");
        if (saved instanceof Number number) {
            taken = number.intValue();
        }
    }

    private ScriptStatus takeFromSource() {
        int available = countIn(source);
        if (available < params.count()) {
            return fail("у источника осталось " + available + " шт '" + params.item() + "', нужно " + params.count()
                    + ": предмет ушёл по дороге");
        }
        int removed = removeFromSource(params.count());
        for (ItemStack part : split(new ItemStack(item, removed))) {
            ItemStack leftover = spirit.getInventory().addItem(part);
            if (!leftover.isEmpty()) {
                DropCollector.drop(spirit, leftover);
            }
        }
        taken += removed;
        approach.cancel();
        result = new ScriptResult.Data(Map.of(
                "item", params.item(),
                "count", params.count(),
                "taken", taken,
                "from", params.from()));
        return ScriptStatus.SUCCEEDED;
    }

    private boolean fitsInSpirit(int count) {
        return spirit.getInventory().canAddAll(split(new ItemStack(item, count)));
    }

    private int countIn(LivingEntity owner) {
        int total = 0;
        Container container = containerOf(owner);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int removeFromSource(int wanted) {
        Container container = containerOf(source);
        int remaining = wanted;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return wanted - remaining;
    }

    private static Container containerOf(LivingEntity owner) {
        if (owner instanceof ServerPlayer player) {
            return player.getInventory();
        }
        if (owner instanceof SpiritEntity other) {
            return other.getInventory();
        }
        throw new IllegalArgumentException(
                "У сущности " + owner.getType() + " нет инвентаря: " + "take работает только с игроком и духом");
    }

    private static List<ItemStack> split(ItemStack stack) {
        List<ItemStack> parts = new ArrayList<>();
        int remaining = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (remaining > 0) {
            int chunk = Math.min(remaining, max);
            parts.add(stack.copyWithCount(chunk));
            remaining -= chunk;
        }
        return parts;
    }

    private ScriptStatus fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.mining.ClimbReach;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.script.ClearAreaParams;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import org.jspecify.annotations.Nullable;

final class ClearAreaScript implements ScriptInstance {
    private static final int MAX_RESTOCKS = 2;
    private final ClearAreaSession session;
    private final @Nullable SpiritEntity spirit;
    private @Nullable TaskRunner restock;
    private boolean restockChecked;
    private int restockAttempts;
    private @Nullable TaskRunner climb;
    private boolean climbChecked;
    private @Nullable TaskRunner descend;
    private boolean climbed;
    private boolean descendTried;

    ClearAreaScript(SpiritEntity spirit, ClearAreaParams params) {
        this.spirit = spirit;
        this.session = new ClearAreaSession(
                spirit,
                BuildBox.of(params.x1(), params.y1(), params.z1(), params.x2(), params.y2(), params.z2()),
                params.demolish(),
                params.keepGround());
    }

    ClearAreaScript(ClearAreaSession session) {
        this.session = session;
        this.spirit = null;
    }

    @Override
    public ScriptStatus tick() {
        if (restock == null && restockAttempts < MAX_RESTOCKS && (!restockChecked || session.picking())) {
            restockChecked = true;
            restock = shovelRestock();
            if (restock != null) {
                restockAttempts++;
            }
        }
        if (restock != null) {
            if (restock.isSuspended()) {
                restock.resume();
            }
            if (restock.tick() == RunStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            restock = null;
        }
        if (climb == null && !climbChecked && spirit != null && session.picking()) {
            climbChecked = true;
            int rise = ClimbReach.rise(spirit.getBlockY(), session.box.minY());
            if (ClimbReach.needed(rise)) {
                climb = climbRunner(rise);
                if (climb == null) {
                    return ScriptStatus.FAILED;
                }
            }
        }
        if (climb != null) {
            if (climb.isSuspended()) {
                climb.resume();
            }
            if (climb.tick() == RunStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            if (climb.status() != RunStatus.SUCCEEDED) {
                session.fail("высокая клетка: столб не построен — "
                        + climb.failureReason().orElse("причина неизвестна"));
                return ScriptStatus.FAILED;
            }
            climbed = true;
            climb = null;
        }
        if (descend != null) {
            return tickDescend();
        }
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case FAILED -> ScriptStatus.FAILED;
            case SUCCEEDED -> {
                if (!climbed || descendTried) {
                    yield ScriptStatus.SUCCEEDED;
                }
                descendTried = true;
                descend = descendRunner();
                yield descend == null ? ScriptStatus.SUCCEEDED : tickDescend();
            }
        };
    }

    private ScriptStatus tickDescend() {
        TaskRunner runner = descend;
        if (runner == null) {
            return ScriptStatus.SUCCEEDED;
        }
        if (runner.isSuspended()) {
            runner.resume();
        }
        if (runner.tick() == RunStatus.RUNNING) {
            return ScriptStatus.RUNNING;
        }
        descend = null;
        return ScriptStatus.SUCCEEDED;
    }

    private @Nullable TaskRunner descendRunner() {
        if (spirit == null || !PillarDownScript.ownBelow(spirit)) {
            return null;
        }
        return TaskRunner.start(
                new ScriptCall("pillar_down", Map.of()),
                SpiritScripts.registryFor(spirit),
                ConditionRegistry.of(Map.of()));
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(session.progress());
    }

    @Override
    public Optional<ScriptResult> result() {
        return session.outcome() == ClearAreaSession.Outcome.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(session.resultData()))
                : Optional.empty();
    }

    private @Nullable TaskRunner climbRunner(int rise) {
        if (spirit == null) {
            return null;
        }
        return TaskRunner.start(
                new SequenceNode(List.of(
                        new ScriptCall("acquire", Map.of("item", "minecraft:dirt", "count", rise)),
                        new ScriptCall("pillar_up", Map.of("height", rise, "block", "minecraft:dirt", "down", false)))),
                SpiritScripts.registryFor(spirit),
                ConditionRegistry.of(Map.of()));
    }

    private @Nullable TaskRunner shovelRestock() {
        if (spirit == null || usableShovel(spirit)) {
            return null;
        }
        boolean cobble = spirit.getInventory().countItem(Items.COBBLESTONE) > 0;
        Item shovel = cobble ? Items.STONE_SHOVEL : Items.WOODEN_SHOVEL;
        int held = spirit.getInventory().countItem(shovel);
        return TaskRunner.start(
                new ScriptCall(
                        "acquire",
                        Map.of("item", BuiltInRegistries.ITEM.getKey(shovel).toString(), "count", held + 1)),
                SpiritScripts.registryFor(spirit),
                ConditionRegistry.of(Map.of()));
    }

    private static boolean usableShovel(SpiritEntity spirit) {
        ToolReserve reserve = MiningTables.toolReserve();
        List<String> repairTiers = MiningTables.repairTiers();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.getItem() instanceof ShovelItem
                    && !reserve.spent(
                            stack.getMaxDamage() - stack.getDamageValue(),
                            stack.getMaxDamage(),
                            stack.isEnchanted(),
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            repairTiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cancel() {
        if (restock != null) {
            restock.cancel();
            restock = null;
        }
        if (climb != null) {
            climb.cancel();
            climb = null;
        }
        if (descend != null) {
            descend.cancel();
            descend = null;
        }
        session.cancel();
    }

    @Override
    public void suspend() {
        if (restock != null) {
            restock.suspend();
        }
        session.suspend();
    }

    @Override
    public String failureReason() {
        return session.failureReason();
    }

    @Override
    public Map<String, Object> saveState() {
        return session.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        session.restoreState(state);
    }
}

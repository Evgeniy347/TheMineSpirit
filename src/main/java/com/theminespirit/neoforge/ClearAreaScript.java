package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.ClearAreaParams;
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
        // Лопата проверяется в начале ящика и между клетками: в большом ящике (сотни блоков) она кончалась на середине,
        // и остаток копался рукой (сцена 56). Не вышло — больше в этом ящике не пробует.
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
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> ScriptStatus.FAILED;
        };
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

    // Лопата износилась — новая до расчистки, как сделал бы игрок: срезка участка — сотни блоков земли, каменной
    // лопаты хватает на 131, дальше дух копал рукой втрое дольше (сцена 56: трава 19 тиков вместо 5). Каменная — при
    // булыжнике в инвентаре, иначе деревянная (доски из своих брёвен); не вышло — расчистка идёт чем есть.
    private @Nullable TaskRunner shovelRestock() {
        if (spirit == null || usableShovel(spirit)) {
            return null;
        }
        boolean cobble = spirit.getInventory().countItem(Items.COBBLESTONE) > 0;
        Item shovel = cobble ? Items.STONE_SHOVEL : Items.WOODEN_SHOVEL;
        // acquire добирает «до N»: изношенная лопата того же вида в инвентаре засчиталась бы и новой не было бы.
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
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.getItem() instanceof ShovelItem
                    && !reserve.spent(
                            stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage(), stack.isEnchanted())) {
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

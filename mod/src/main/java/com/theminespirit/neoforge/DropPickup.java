package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.OverflowPolicy;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.neoforge.inventory.UnloadScript;
import net.minecraft.world.item.ItemStack;

final class DropPickup {
    private final DropCollector owner;

    DropPickup(DropCollector owner) {
        this.owner = owner;
    }

    DropCollector.State pickUp() {
        if (owner.target.hasPickUpDelay()) {
            return ++owner.delayWaited > owner.tuning.maxDelayWaitTicks()
                    ? owner.pursuit.giveUpOnTarget()
                    : DropCollector.State.RUNNING;
        }
        ItemStack stack = owner.target.getItem();
        ItemStack rest = owner.spirit.getInventory().addItem(stack.copy());
        int taken = stack.getCount() - rest.getCount();
        if (taken == 0 && owner.overflow == OverflowMode.BY_VALUE) {
            return overflowPickUp(stack);
        }
        if (taken == 0 && owner.overflow == OverflowMode.RETURN_TO_BASE) {
            return startRelief(stack);
        }
        if (taken == 0) {
            return owner.fail("инвентарь полон: не помещается " + DropCollector.itemId(stack));
        }
        owner.reliefTried = false;
        owner.spirit.take(owner.target, taken);
        owner.collected += taken;
        if (rest.isEmpty()) {
            owner.target.discard();
        } else {
            owner.target.setItem(rest);
        }
        owner.pursuit.releaseHeld();
        owner.target = null;
        return DropCollector.State.RUNNING;
    }

    boolean pickable(ItemStack stack) {
        if (owner.spirit.getInventory().canAddItem(stack)) {
            return true;
        }
        if (owner.overflow == OverflowMode.RETURN_TO_BASE) {
            return true;
        }
        return owner.overflow == OverflowMode.BY_VALUE
                && owner.decider.decide(stack).action() != OverflowPolicy.Action.REFUSE;
    }

    private DropCollector.State startRelief(ItemStack stack) {
        if (owner.reliefTried) {
            return owner.fail("инвентарь полон: не помещается " + DropCollector.itemId(stack));
        }
        owner.reliefTried = true;
        owner.relief = UnloadScript.relief(owner.spirit, -1);
        return DropCollector.State.RUNNING;
    }

    DropCollector.State reliefTick() {
        ScriptStatus status = owner.relief.tick();
        if (status == ScriptStatus.RUNNING) {
            return DropCollector.State.RUNNING;
        }
        int moved = owner.relief.movedTotal();
        String reason = owner.relief.failureReason();
        owner.relief = null;
        if (moved > 0) {
            owner.reliefTried = false;
            return DropCollector.State.RUNNING;
        }
        owner.reliefTried = false;
        return owner.fail("инвентарь полон: на базе нечего сдать"
                + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
    }

    private DropCollector.State overflowPickUp(ItemStack stack) {
        OverflowPolicy.Decision decision = owner.decider.decide(stack);
        return switch (decision.action()) {
            case TAKE_INTO_FREE_SLOT -> owner.fail("инвентарь полон: не помещается " + DropCollector.itemId(stack));
            case DROP -> dropAndTake(stack, decision.slot());
            case REFUSE -> owner.fail(decision.reason());
        };
    }

    private DropCollector.State dropAndTake(ItemStack stack, int slot) {
        ItemStack exile = owner.spirit.getInventory().removeItemNoUpdate(slot);
        DropCollector.drop(owner.spirit, exile);
        owner.reliefTried = false;
        owner.spirit.getInventory().setItem(slot, stack.copy());
        owner.spirit.take(owner.target, stack.getCount());
        owner.collected += stack.getCount();
        owner.target.discard();
        owner.pursuit.releaseHeld();
        owner.target = null;
        return DropCollector.State.RUNNING;
    }
}

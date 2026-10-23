package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.intellect.AcquireOrder;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class ActivityTaskFactory {
    private static final String RADIUS = "radius";

    private ActivityTaskFactory() {}

    public static Optional<TaskNode> taskFor(ActivityIntent intent, SpiritEntity spirit) {
        if (intent == null) {
            throw new IllegalArgumentException(
                    "Задача без намерения не строится: скрипт и параметры берутся из order деятельности");
        }
        if (intent.script().isEmpty()) {
            return Optional.empty();
        }
        String script = intent.script().get();
        Map<String, Object> params = switch (script) {
            case "chop_tree" -> Map.of("species", "any", "count", 4, RADIUS, 16, "replant", true);
            case "harvest_crop" -> Map.of("crop", "any", RADIUS, 8, "replant", true);
            case "mine_vein" -> Map.of("ore", "any", "count", 8, RADIUS, 16);
            case "unload" -> Map.of("container", "auto", RADIUS, 8, "force", false);
            case "wait" -> Map.of("ticks", 200);
            case ActivityIntent.ACQUIRE -> acquireParams(intent, spirit);
            default -> intent.params();
        };
        return Optional.of(new ScriptCall(script, params));
    }

    private static Map<String, Object> acquireParams(ActivityIntent intent, SpiritEntity spirit) {
        if (spirit == null) {
            throw new IllegalArgumentException("acquire без духа не строится: цель считается по его инвентарю");
        }
        if (!(intent.params().get("item") instanceof String item)
                || !(intent.params().get("count") instanceof Integer more)) {
            throw new IllegalArgumentException(
                    "Намерение acquire " + intent.id() + " без item/count: " + intent.params());
        }
        Map<String, Integer> held = new HashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                held.merge(StackMapper.itemId(stack), stack.getCount(), Integer::sum);
            }
        }
        AcquireOrder order = AcquireOrder.of(item, more, held);
        return Map.of("item", order.item(), "count", order.count());
    }
}

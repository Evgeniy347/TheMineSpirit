package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.intellect.AcquireOrder;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.activity.SmeltActivity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        if ("smelt".equals(script) && intent.params().get(SmeltActivity.CHARCOAL_LOG) instanceof String log) {
            return Optional.of(charcoal(intent, log, spirit));
        }
        if ("smelt".equals(script)) {
            return Optional.of(new SequenceNode(withHome(spirit, new ScriptCall(script, intent.params()))));
        }
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

    private static TaskNode charcoal(ActivityIntent intent, String log, SpiritEntity spirit) {
        Map<String, Object> smelt = new HashMap<>(intent.params());
        smelt.remove(SmeltActivity.CHARCOAL_LOG);
        Map<String, Object> logs = acquireParams(
                new ActivityIntent(
                        intent.id(),
                        Optional.of(ActivityIntent.ACQUIRE),
                        Map.of("item", log, "count", smelt.get("count")),
                        Optional.empty()),
                spirit);
        List<TaskNode> steps = new ArrayList<>();
        steps.add(new ScriptCall(ActivityIntent.ACQUIRE, logs));
        steps.addAll(withHome(spirit, new ScriptCall("smelt", Map.copyOf(smelt))));
        return new SequenceNode(steps);
    }

    // Плавка — у дома: печь стоит во дворе станций, а дух с расчистки угла участка или из шахты искал её в 16 вокруг
    // себя и падал «нужна печь: рядом (16) нет, в инвентаре нет» (приёмка «джунгли» 24.10).
    private static List<TaskNode> withHome(SpiritEntity spirit, TaskNode smelt) {
        if (spirit == null || spirit.home().isEmpty()) {
            return List.of(smelt);
        }
        BlockCoord home = spirit.home().orElseThrow();
        return List.of(new ScriptCall("goto", Map.of("x", home.x(), "y", home.y(), "z", home.z())), smelt);
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

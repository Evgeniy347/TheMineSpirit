package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.board.BoardTuning;
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
    private static final int MIN_OWN_LOGS = 5;
    private static final int FUEL_SHARE_NUM = 2;
    private static final int FUEL_SHARE_DEN = 3;
    private static final String RADIUS = "radius";
    private static final String COUNT = "count";

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
            case "chop_tree" -> chopParams();
            case "harvest_crop" -> Map.of("crop", "any", RADIUS, 8, "replant", true);
            case "mine_vein" -> Map.of("ore", "any", COUNT, 8, RADIUS, 16);
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
        List<TaskNode> steps = new ArrayList<>();
        int held = heldLogs(spirit, log);
        int count = smelt.get(COUNT) instanceof Integer wanted ? wanted : 1;
        if (held >= MIN_OWN_LOGS) {
            smelt.put(COUNT, Math.min(count, held * FUEL_SHARE_DEN / (FUEL_SHARE_DEN + FUEL_SHARE_NUM)));
        } else {
            steps.add(new ScriptCall(
                    ActivityIntent.ACQUIRE,
                    acquireParams(
                            new ActivityIntent(
                                    intent.id(),
                                    Optional.of(ActivityIntent.ACQUIRE),
                                    Map.of("item", log, COUNT, count),
                                    Optional.empty()),
                            spirit)));
        }
        steps.addAll(withHome(spirit, new ScriptCall("smelt", Map.copyOf(smelt))));
        return new SequenceNode(steps);
    }

    private static int heldLogs(SpiritEntity spirit, String log) {
        if (spirit == null) {
            return 0;
        }
        String id = log.startsWith("minecraft:") ? log : "minecraft:" + log;
        int count = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty() && StackMapper.itemId(stack).equals(id)) {
                count += stack.getCount();
            }
        }
        return count;
    }

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
                || !(intent.params().get(COUNT) instanceof Integer more)) {
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
        return Map.of("item", order.item(), COUNT, order.count());
    }

    private static Map<String, Object> chopParams() {
        return Map.of("species", "any", COUNT, 4, RADIUS, BoardTuning.chopRadius(), "replant", true);
    }
}

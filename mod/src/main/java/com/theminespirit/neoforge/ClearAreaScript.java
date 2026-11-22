package com.theminespirit.neoforge;

import com.theminespirit.core.building.api.BuildBox;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.ClearAreaParams;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ScriptArgs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

final class ClearAreaScript implements ScriptInstance {
    private static final int MAX_RESTOCKS = 2;
    private static final String BOXES = "boxes";

    private final ClearAreaSession session;
    private final SpiritEntity spirit;
    private @Nullable TaskRunner restock;
    private String restockKind;
    private int restockAttempts;

    ClearAreaScript(SpiritEntity spirit, Map<String, Object> params) {
        this.spirit = spirit;
        this.session = new ClearAreaSession(
                spirit,
                boxes(params),
                ScriptArgs.optionalBoolean(params, "demolish", false),
                ScriptArgs.optionalBoolean(params, "keep_ground", false));
    }

    @Override
    public ScriptStatus tick() {
        if (restock != null) {
            if (restock.isSuspended()) {
                restock.resume();
            }
            if (restock.tick() == RunStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            boolean ok = restock.status() == RunStatus.SUCCEEDED;
            String reason = restock.failureReason().orElse("причина неизвестна");
            restock = null;
            if (!ok && session.neededTool != null) {
                session.fail("инструмент '" + restockKind + "' не добыт: " + reason, FailureFact.NO_TOOL);
                return ScriptStatus.FAILED;
            }
        }
        String needed = session.neededTool;
        if (needed != null && !hasTool(spirit, needed) && restockAttempts < MAX_RESTOCKS) {
            restock = acquire(needed);
            restockKind = needed;
            restockAttempts++;
            if (restock != null) {
                return ScriptStatus.RUNNING;
            }
        }
        if (needed != null && hasTool(spirit, needed)) {
            session.neededTool = null;
            restockAttempts = 0;
        }
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case FAILED -> ScriptStatus.FAILED;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
        };
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(session.progress());
    }

    @Override
    public Optional<ScriptResult> result() {
        return session.outcome() == ClearAreaSession.Outcome.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(session.tally.toData()))
                : Optional.empty();
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
    public Optional<FailureFact> failureFact() {
        return session.failureFact == null ? Optional.empty() : Optional.of(session.failureFact);
    }

    @Override
    public Map<String, Object> saveState() {
        return ClearProgress.save(session);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        ClearProgress.restore(session, state);
    }

    private @Nullable TaskRunner acquire(String kind) {
        Supplier<Item> item = toolFor(spirit, kind);
        if (item == null) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(item.get()).toString();
        int held = spirit.getInventory().countItem(item.get());
        return TaskRunner.start(
                new ScriptCall("acquire", Map.of("item", id, "count", held + 1)),
                SpiritScripts.registryFor(spirit),
                ConditionRegistry.of(Map.of()));
    }

    private static @Nullable Supplier<Item> toolFor(SpiritEntity spirit, String kind) {
        boolean stone = spirit.getInventory().countItem(Items.COBBLESTONE) > 0;
        return switch (kind) {
            case "pickaxe" -> () -> stone ? Items.STONE_PICKAXE : Items.WOODEN_PICKAXE;
            case "axe" -> () -> stone ? Items.STONE_AXE : Items.WOODEN_AXE;
            case "shovel" -> () -> stone ? Items.STONE_SHOVEL : Items.WOODEN_SHOVEL;
            default -> null;
        };
    }

    private static boolean hasTool(SpiritEntity spirit, String kind) {
        List<Item> items = switch (kind) {
            case "pickaxe" ->
                List.of(
                        Items.WOODEN_PICKAXE,
                        Items.STONE_PICKAXE,
                        Items.IRON_PICKAXE,
                        Items.GOLDEN_PICKAXE,
                        Items.DIAMOND_PICKAXE,
                        Items.NETHERITE_PICKAXE);
            case "axe" ->
                List.of(
                        Items.WOODEN_AXE,
                        Items.STONE_AXE,
                        Items.IRON_AXE,
                        Items.GOLDEN_AXE,
                        Items.DIAMOND_AXE,
                        Items.NETHERITE_AXE);
            case "shovel" ->
                List.of(
                        Items.WOODEN_SHOVEL,
                        Items.STONE_SHOVEL,
                        Items.IRON_SHOVEL,
                        Items.GOLDEN_SHOVEL,
                        Items.DIAMOND_SHOVEL,
                        Items.NETHERITE_SHOVEL);
            default -> List.of();
        };
        return items.stream().anyMatch(item -> spirit.getInventory().countItem(item) > 0);
    }

    private static List<BuildBox> boxes(Map<String, Object> params) {
        Optional<List<String>> listed = ScriptArgs.optionalStringList(params, BOXES);
        if (listed.isPresent()) {
            List<BuildBox> boxes = new ArrayList<>();
            for (String text : listed.orElseThrow()) {
                boxes.add(parseBox(text));
            }
            if (boxes.isEmpty()) {
                throw new IllegalArgumentException("Список ящиков расчистки пуст: чистить нечего");
            }
            return boxes;
        }
        ClearAreaParams single = ClearAreaParams.from(params);
        return List.of(BuildBox.of(single.x1(), single.y1(), single.z1(), single.x2(), single.y2(), single.z2()));
    }

    private static BuildBox parseBox(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Ящик расчистки '" + text + "': нужно шесть чисел x1,y1,z1,x2,y2,z2");
        }
        try {
            BuildBox box = BuildBox.of(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim()),
                    Integer.parseInt(parts[4].trim()),
                    Integer.parseInt(parts[5].trim()));
            checkVolume(box);
            return box;
        } catch (NumberFormatException wrong) {
            throw new IllegalArgumentException("Ящик расчистки '" + text + "': числа не читаются", wrong);
        }
    }

    private static void checkVolume(BuildBox box) {
        if (box.exceeds(CatalogLimits.BUILD_VOLUME_MAX)) {
            throw new IllegalArgumentException("Объём clear_area " + box.volume() + " больше предела "
                    + CatalogLimits.BUILD_VOLUME_MAX + ": разбейте область на части");
        }
    }
}

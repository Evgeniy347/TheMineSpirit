package com.theminespirit.neoforge.social;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ReportParams;
import com.theminespirit.core.social.SayText;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ReportScript implements ScriptInstance {
    private static final int INVENTORY_KINDS = 5;

    private final SpiritEntity spirit;
    private final ReportParams params;
    private String failureReason;

    public ReportScript(SpiritEntity spirit, ReportParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока готовил доклад");
        }
        String text;
        try {
            text = SayText.normalize(reportText(), CatalogLimits.SAY_LENGTH_MAX);
        } catch (IllegalArgumentException empty) {
            return fail(empty.getMessage());
        }
        if (!(spirit.level() instanceof ServerLevel level)) {
            return fail("доклад возможен только на сервере: у клиентского мира нет чата игроков");
        }
        if (params.to().isPresent()) {
            Optional<?> found = ScriptTargetResolver.resolve(
                    spirit, TargetSelector.parse(params.to().orElseThrow()));
            if (found.isEmpty() || !(found.get() instanceof ServerPlayer player)) {
                return fail("адресат доклада '" + params.to().orElseThrow() + "' не найден: докладывать некому");
            }
            player.sendSystemMessage(Component.literal(text));
        } else {
            Component message = Component.literal(text);
            for (ServerPlayer player : level.players()) {
                if (player.distanceTo(spirit) <= CatalogLimits.SAY_HEARING_RADIUS) {
                    player.sendSystemMessage(message);
                }
            }
        }
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public void cancel() {}

    @Override
    public void suspend() {}

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Data(Map.of(
                "task", spirit.currentTaskScript().orElse("none"),
                "health", (int) Math.ceil(spirit.getHealth()),
                "food", spirit.hunger().foodLevel(),
                "where", spirit.blockPosition().toShortString())));
    }

    private String reportText() {
        String task = spirit.currentTaskScript()
                .map(script -> script
                        + spirit.taskStatus().map(status -> " (" + status + ")").orElse(""))
                .orElse("нет");
        return "Доклад: задача " + task
                + "; здоровье " + (int) Math.ceil(spirit.getHealth()) + "/" + (int) Math.ceil(spirit.getMaxHealth())
                + "; сытость " + spirit.hunger().foodLevel()
                + "; инвентарь " + inventorySummary()
                + "; где " + spirit.blockPosition().toShortString()
                + " " + spirit.level().dimension().location();
    }

    private String inventorySummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            return "пуст";
        }
        StringBuilder summary = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown == INVENTORY_KINDS) {
                summary.append(", ещё ").append(counts.size() - INVENTORY_KINDS).append(" вида");
                break;
            }
            if (shown > 0) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append(" x").append(entry.getValue());
            shown++;
        }
        return summary.toString();
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

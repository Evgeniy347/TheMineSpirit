package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.BatchPlanException;
import com.theminespirit.core.planner.BatchPlanner;
import com.theminespirit.core.planner.FallbackPlanner;
import com.theminespirit.core.planner.Goal;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

final class SpiritTaskCommands {
    private static final String SPIRITS = "spirits";

    private SpiritTaskCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("goal")
                .then(argument(SPIRITS, EntityArgument.entities())
                        .then(argument("goal", StringArgumentType.greedyString())
                                .executes(SpiritTaskCommands::goal))));
        spirit.then(literal("status")
                .then(argument(SPIRITS, EntityArgument.entities()).executes(SpiritTaskCommands::status)));
        spirit.then(literal("cancel")
                .then(argument(SPIRITS, EntityArgument.entities()).executes(SpiritTaskCommands::cancel)));
    }

    private static int goal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> selected = EntityArgument.getEntities(ctx, SPIRITS);
        String text = StringArgumentType.getString(ctx, "goal");
        List<SpiritEntity> spirits = filterSpirits(selected);
        if (spirits.isEmpty()) {
            source.sendFailure(
                    Component.literal("Цель '" + text + "' некому поставить: среди выбранных сущностей нет духа"));
            return 0;
        }
        ServerPlayer speaker = source.getPlayer();
        if (speaker == null && FallbackPlanner.requiresPlayerSource(text)) {
            source.sendFailure(Component.literal("Цель '" + text + "' требует игрока-источника: выполните её "
                    + "от имени игрока или через '/execute as <игрок> run spirit goal ...'"));
            return 0;
        }
        Map<String, Object> goalParams = speakerParams(speaker);
        List<ScriptCatalog> catalogs = new ArrayList<>(spirits.size());
        List<WorldObservation> observations = new ArrayList<>(spirits.size());
        for (SpiritEntity spirit : spirits) {
            catalogs.add(SpiritScripts.catalogFor(spirit));
            observations.add(goalObservation(spirit));
        }
        List<TaskNode> plans;
        try {
            plans = BatchPlanner.planAll(catalogs, new Goal(text, goalParams), observations);
        } catch (BatchPlanException e) {
            SpiritEntity failed = spirits.get(e.index());
            source.sendFailure(Component.literal("Цель '" + text + "' не поставлена ни одному духу: дух '"
                    + failed.getStringUUID() + "' не смог её принять: " + e.getMessage()));
            return 0;
        } catch (IllegalArgumentException e) {
            source.sendFailure(
                    Component.literal("Цель '" + text + "' не поставлена ни одному духу: " + e.getMessage()));
            return 0;
        }
        List<String> assigned = new ArrayList<>(spirits.size());
        for (int i = 0; i < spirits.size(); i++) {
            spirits.get(i).startTask(plans.get(i));
            assigned.add(spirits.get(i).getStringUUID());
        }
        source.sendSuccess(
                () -> Component.literal("Цель '" + text + "' поставлена духам: " + String.join(", ", assigned)
                        + ". Состояние — /spirit status"),
                true);
        return spirits.size();
    }

    private static WorldObservation goalObservation(SpiritEntity spirit) {
        BlockPos pos = spirit.blockPosition();
        return new WorldObservation(
                "spirit_" + spirit.getStringUUID(),
                spirit.level().getGameTime(),
                new BlockCoord(pos.getX(), pos.getY(), pos.getZ()),
                inventoryEntries(spirit),
                List.of(),
                List.of(),
                List.of(),
                ScanRadius.DEFAULT.value());
    }

    private static List<ItemCount> inventoryEntries(SpiritEntity spirit) {
        List<ItemCount> entries = new ArrayList<>();
        SimpleContainer inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                entries.add(new ItemCount(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
            }
        }
        return entries;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> selected = EntityArgument.getEntities(ctx, SPIRITS);
        List<SpiritEntity> spirits = filterSpirits(selected);
        if (spirits.isEmpty()) {
            source.sendFailure(Component.literal("Среди выбранных сущностей нет духа: статус смотреть нечего"));
            return 0;
        }
        source.sendSuccess(
                () -> {
                    StringBuilder line = new StringBuilder();
                    for (SpiritEntity spirit : spirits) {
                        if (!line.isEmpty()) {
                            line.append('\n');
                        }
                        line.append(describe(spirit));
                    }
                    return Component.literal(line.toString());
                },
                false);
        return spirits.size();
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> selected = EntityArgument.getEntities(ctx, SPIRITS);
        List<SpiritEntity> spirits = filterSpirits(selected);
        if (spirits.isEmpty()) {
            source.sendFailure(Component.literal("Среди выбранных сущностей нет духа: отменять нечего"));
            return 0;
        }
        List<String> cancelled = new ArrayList<>();
        List<String> idle = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            boolean wasRunning =
                    spirit.taskStatus().filter(s -> s == RunStatus.RUNNING).isPresent();
            spirit.cancelTask();
            if (wasRunning) {
                cancelled.add(spirit.getStringUUID());
            } else {
                idle.add(spirit.getStringUUID());
            }
        }
        if (cancelled.isEmpty()) {
            source.sendFailure(Component.literal("У духов не было выполняемой задачи: " + String.join(", ", idle)));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("Задача духов отменена: " + String.join(", ", cancelled)
                        + (idle.isEmpty() ? "" : ". Без выполняемой задачи: " + String.join(", ", idle))),
                true);
        return cancelled.size();
    }

    private static Map<String, Object> speakerParams(ServerPlayer speaker) {
        return speaker == null ? Map.of() : Map.of("speaker", speaker.getName().getString());
    }

    private static List<SpiritEntity> filterSpirits(Collection<? extends Entity> selected) {
        List<SpiritEntity> spirits = new ArrayList<>();
        for (Entity entity : selected) {
            if (entity instanceof SpiritEntity spirit) {
                spirits.add(spirit);
            }
        }
        return spirits;
    }

    private static String describe(SpiritEntity spirit) {
        Optional<RunStatus> status = spirit.taskStatus();
        String id = "Дух '" + spirit.getStringUUID() + "':";
        if (status.isEmpty()) {
            return id + " задач ещё не было";
        }
        RunStatus value = status.get();
        if (value == RunStatus.RUNNING) {
            String script =
                    spirit.currentTaskScript().map(s -> ", скрипт '" + s + "'").orElse("");
            return id + " задача выполняется" + script;
        }
        String reason = spirit.taskFailureReason().map(r -> ", причина: " + r).orElse("");
        return id + " задача " + statusText(value) + reason;
    }

    private static String statusText(RunStatus status) {
        return switch (status) {
            case RUNNING -> "выполняется";
            case SUCCEEDED -> "завершена успешно";
            case FAILED -> "провалена";
            case CANCELLED -> "отменена";
        };
    }
}

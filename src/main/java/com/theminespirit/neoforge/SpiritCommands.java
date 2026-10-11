package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.FallbackPlanner;
import com.theminespirit.core.planner.Goal;
import com.theminespirit.core.planner.UnplannableGoalException;
import com.theminespirit.core.script.ConditionCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SpiritCommands {
    private SpiritCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spirit =
                literal("spirit").requires(source -> source.hasPermission(2));
        spirit.then(literal("spawn")
                .executes(context -> spawnNear(context.getSource()))
                .then(argument("pos", Vec3Argument.vec3())
                        .executes(context -> spawnAt(context.getSource(),
                                Vec3Argument.getVec3(context, "pos")))));
        spirit.then(literal("observe")
                .executes(context -> observe(context.getSource(), ScanRadius.DEFAULT.value(), false))
                .then(argument("radius", IntegerArgumentType.integer(ScanRadius.MIN, ScanRadius.MAX))
                        .executes(context -> observe(context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius"), false))
                        .then(literal("json")
                                .executes(context -> observe(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius"), true)))));
        spirit.then(literal("goal")
                .then(argument("spirits", EntityArgument.entities())
                        .then(argument("goal", StringArgumentType.greedyString())
                                .executes(SpiritCommands::goal))));
        spirit.then(literal("status")
                .then(argument("spirits", EntityArgument.entities())
                        .executes(SpiritCommands::status)));
        spirit.then(literal("cancel")
                .then(argument("spirits", EntityArgument.entities())
                        .executes(SpiritCommands::cancel)));
        dispatcher.register(spirit);
    }

    private static int spawnNear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return spawnAt(source, player.position());
    }

    private static int spawnAt(CommandSourceStack source, Vec3 pos) {
        ServerLevel level = source.getLevel();
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(level);
        if (spirit == null) {
            source.sendFailure(Component.literal("Дух не создан: тип сущности "
                    + ModEntities.SPIRIT.getId() + " не зарегистрирован"));
            return 0;
        }
        spirit.moveTo(pos);
        level.addFreshEntity(spirit);
        source.sendSuccess(() -> Component.literal(
                "Дух призван в (" + pos.x + ", " + pos.y + ", " + pos.z + ")"), true);
        return 1;
    }

    private static int observe(CommandSourceStack source, int radius, boolean asJson) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WorldObservation observation = WorldScanner.observe(
                source.getLevel(),
                "player_" + player.getScoreboardName(),
                new com.theminespirit.core.observation.BlockCoord(
                        player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ()),
                ScanRadius.of(radius),
                player.getInventory()).observation();
        if (asJson) {
            source.sendSuccess(() -> Component.literal(observation.toJson()), false);
        } else {
            source.sendSuccess(() -> Component.literal("hash " + observation.hash()
                    + ", блоков " + observation.blocks().size()
                    + ", сущностей " + observation.entities().size()
                    + ", контейнеров " + observation.containers().size()), false);
        }
        return 1;
    }

    private static int goal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> selected = EntityArgument.getEntities(ctx, "spirits");
        String text = StringArgumentType.getString(ctx, "goal");
        List<SpiritEntity> spirits = filterSpirits(selected);
        if (spirits.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Цель '" + text + "' некому поставить: среди выбранных сущностей нет духа"));
            return 0;
        }
        List<String> assigned = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            try {
                TaskNode plan = new FallbackPlanner(SpiritScripts.catalogFor(spirit), ConditionCatalog.empty())
                        .plan(new Goal(text, Map.of()), emptyObservation(spirit));
                spirit.startTask(plan);
                assigned.add(spirit.getStringUUID());
            } catch (UnplannableGoalException | PlanValidationException e) {
                source.sendFailure(Component.literal("Духу '" + spirit.getStringUUID() + "' цель '" + text
                        + "' не поставлена: " + e.getMessage()));
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal("Цель '" + text + "' поставлена духам: "
                + String.join(", ", assigned) + ". Состояние — /spirit status"), true);
        return spirits.size();
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> selected = EntityArgument.getEntities(ctx, "spirits");
        List<SpiritEntity> spirits = filterSpirits(selected);
        if (spirits.isEmpty()) {
            source.sendFailure(Component.literal("Среди выбранных сущностей нет духа: статус смотреть нечего"));
            return 0;
        }
        source.sendSuccess(() -> {
            StringBuilder line = new StringBuilder();
            for (SpiritEntity spirit : spirits) {
                if (!line.isEmpty()) {
                    line.append('\n');
                }
                line.append(describe(spirit));
            }
            return Component.literal(line.toString());
        }, false);
        return spirits.size();
    }

    private static int cancel(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> selected = EntityArgument.getEntities(ctx, "spirits");
        List<SpiritEntity> spirits = filterSpirits(selected);
        if (spirits.isEmpty()) {
            source.sendFailure(Component.literal("Среди выбранных сущностей нет духа: отменять нечего"));
            return 0;
        }
        List<String> cancelled = new ArrayList<>();
        List<String> idle = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            boolean wasRunning = spirit.taskStatus().filter(s -> s == RunStatus.RUNNING).isPresent();
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
        source.sendSuccess(() -> Component.literal("Задача духов отменена: " + String.join(", ", cancelled)
                + (idle.isEmpty() ? "" : ". Без выполняемой задачи: " + String.join(", ", idle))), true);
        return cancelled.size();
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

    private static WorldObservation emptyObservation(SpiritEntity spirit) {
        BlockPos pos = spirit.blockPosition();
        return new WorldObservation("spirit_" + spirit.getStringUUID(), spirit.level().getGameTime(),
                new BlockCoord(pos.getX(), pos.getY(), pos.getZ()),
                List.of(), List.of(), List.of(), List.of(), ScanRadius.DEFAULT.value());
    }

    private static String describe(SpiritEntity spirit) {
        Optional<RunStatus> status = spirit.taskStatus();
        String id = "Дух '" + spirit.getStringUUID() + "':";
        if (status.isEmpty()) {
            return id + " задач ещё не было";
        }
        RunStatus value = status.get();
        if (value == RunStatus.RUNNING) {
            String script = spirit.currentTaskScript().map(s -> ", скрипт '" + s + "'").orElse("");
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
package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.BatchPlanException;
import com.theminespirit.core.planner.BatchPlanner;
import com.theminespirit.core.planner.FallbackPlanner;
import com.theminespirit.core.planner.Goal;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

final class SpiritTaskCommands {
    private static final Set<UUID> EVIDENCE_SCANS = new HashSet<>();

    private SpiritTaskCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("goal")
                .then(argument("spirits", EntityArgument.entities())
                        .then(argument("goal", StringArgumentType.greedyString())
                                .executes(SpiritTaskCommands::goal))));
        spirit.then(literal("status")
                .then(argument("spirits", EntityArgument.entities())
                        .executes(SpiritTaskCommands::status)));
        spirit.then(literal("cancel")
                .then(argument("spirits", EntityArgument.entities())
                        .executes(SpiritTaskCommands::cancel)));
    }

    private static int goal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> selected = EntityArgument.getEntities(ctx, "spirits");
        String text = StringArgumentType.getString(ctx, "goal");
        List<SpiritEntity> spirits = filterSpirits(selected);
        if (spirits.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Цель '" + text + "' некому поставить: среди выбранных сущностей нет духа"));
            return 0;
        }
        ServerPlayer speaker = source.getPlayer();
        if (speaker == null && FallbackPlanner.requiresPlayerSource(text)) {
            source.sendFailure(Component.literal("Цель '" + text + "' требует игрока-источника: выполните её "
                    + "от имени игрока или через '/execute as <игрок> run spirit goal ...'"));
            return 0;
        }
        Map<String, Object> goalParams = speakerParams(speaker);
        Map<SpiritEntity, WorldObservation> observations = new LinkedHashMap<>();
        for (SpiritEntity spirit : spirits) {
            observations.put(spirit, storeMinimalObservation(spirit));
        }
        Map<SpiritEntity, String> refusals = refuseWithoutEvidence(spirits, text);
        List<SpiritEntity> candidates = new ArrayList<>();
        List<ScriptCatalog> catalogs = new ArrayList<>();
        List<WorldObservation> candidateObservations = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            if (!refusals.containsKey(spirit)) {
                candidates.add(spirit);
                catalogs.add(SpiritScripts.catalogFor(spirit));
                candidateObservations.add(observations.get(spirit));
            }
        }
        if (candidates.isEmpty()) {
            source.sendFailure(Component.literal("Цель '" + text + "' не поставлена ни одному духу: "
                    + describeRefusals(refusals)));
            return 0;
        }
        List<TaskNode> plans;
        try {
            plans = BatchPlanner.planAll(catalogs, new Goal(text, goalParams), candidateObservations);
        } catch (BatchPlanException e) {
            SpiritEntity failed = candidates.get(e.index());
            source.sendFailure(Component.literal("Цель '" + text + "' не поставлена ни одному духу: дух '"
                    + failed.getStringUUID() + "' не смог её принять: " + e.getMessage()
                    + refusalsSuffix(refusals)));
            return 0;
        }
        List<String> assigned = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).startTask(plans.get(i));
            assigned.add(candidates.get(i).getStringUUID());
        }
        source.sendSuccess(() -> Component.literal("Цель '" + text + "' поставлена духам: "
                + String.join(", ", assigned) + ". Состояние — /spirit status" + refusalsSuffix(refusals)), true);
        return candidates.size();
    }

    private static WorldObservation storeMinimalObservation(SpiritEntity spirit) {
        WorldObservation observation = emptyObservation(spirit);
        spirit.updateObservation(observation);
        return observation;
    }

    private static Map<SpiritEntity, String> refuseWithoutEvidence(List<SpiritEntity> spirits, String text) {
        Map<SpiritEntity, String> refusals = new LinkedHashMap<>();
        for (SpiritEntity spirit : spirits) {
            checkEvidence(spirit, text).ifPresent(reason -> refusals.put(spirit, reason));
        }
        return refusals;
    }

    private static Optional<String> checkEvidence(SpiritEntity spirit, String text) {
        List<String> filter;
        String missing;
        if (FallbackPlanner.requiresLogEvidence(text)) {
            filter = List.of("*_log");
            missing = "рядом в радиусе " + ScanRadius.DEFAULT.value() + " нет подходящих брёвен: рубить нечего";
        } else if (FallbackPlanner.requiresOreEvidence(text)) {
            filter = List.of("*_ore");
            missing = "рядом в радиусе " + ScanRadius.DEFAULT.value() + " нет подходящей руды: добывать нечего";
        } else {
            return Optional.empty();
        }
        if (!EVIDENCE_SCANS.add(spirit.getUUID())) {
            return Optional.of("дух уже сканируется для планирования: дождитесь завершения текущей команды");
        }
        try {
            WorldObservation evidence = scanEvidence(spirit, filter);
            return evidence.blocks().isEmpty() ? Optional.of(missing) : Optional.empty();
        } finally {
            EVIDENCE_SCANS.remove(spirit.getUUID());
        }
    }

    private static WorldObservation scanEvidence(SpiritEntity spirit, List<String> filter) {
        BlockPos pos = spirit.blockPosition();
        WorldScanner.Session session = new WorldScanner.Session(spirit.level(), "spirit_" + spirit.getStringUUID(),
                new BlockCoord(pos.getX(), pos.getY(), pos.getZ()), ScanRadius.DEFAULT, IdFilter.of(filter),
                spirit.getInventory());
        while (!session.done()) {
            session.step(ScanTuning.SLICES_PER_TICK);
        }
        return session.result().observation();
    }

    private static String describeRefusals(Map<SpiritEntity, String> refusals) {
        List<String> parts = new ArrayList<>(refusals.size());
        for (Map.Entry<SpiritEntity, String> refusal : refusals.entrySet()) {
            parts.add("дух '" + refusal.getKey().getStringUUID() + "' не смог её принять: " + refusal.getValue());
        }
        return String.join("; ", parts);
    }

    private static String refusalsSuffix(Map<SpiritEntity, String> refusals) {
        if (refusals.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>(refusals.size());
        for (Map.Entry<SpiritEntity, String> refusal : refusals.entrySet()) {
            parts.add("дух '" + refusal.getKey().getStringUUID() + "' (" + refusal.getValue() + ")");
        }
        return ". Без задачи остались: " + String.join("; ", parts);
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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

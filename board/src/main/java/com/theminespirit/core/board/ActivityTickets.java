package com.theminespirit.core.board;

import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ActivityTickets {
    public static final String SCRIPT = "script=";
    public static final String TARGET = "target=";
    public static final String TEAM = "team=";
    public static final String LEADER = "leader=";
    public static final String PLANNED_TICKS = "plannedTicks=";

    public record Identity(String id, BoardCase activity, String cause) {}

    public record Crew(Optional<UUID> leader, List<UUID> team) {}

    public record Plan(String script, String target, int plannedTicks, TaskNode task) {}

    public record Request(Identity identity, Crew crew, int priority, long createdAt, Plan plan) {}

    private ActivityTickets() {}

    public static BoardTicket ticket(Request request) {
        Objects.requireNonNull(request, "Запрос занятия не может быть null: без него нечего ставить");
        Identity identity = Objects.requireNonNull(request.identity(), "Личность занятия не может быть null");
        Crew members = Objects.requireNonNull(request.crew(), "Команда занятия не может быть null");
        Plan plan = Objects.requireNonNull(request.plan(), "План занятия не может быть null");
        BoardCase activity = identity.activity();
        Objects.requireNonNull(activity, "Кейс занятия не может быть null: без него дух не знает, чем занят");
        if (!BoardActivities.isActivity(activity)) {
            throw new IllegalArgumentException(
                    "Кейс " + activity + " — не занятие: ACTIVITY_* ставят через эту фабрику");
        }
        List<UUID> crew = List.copyOf(Objects.requireNonNull(members.team(), "Команда занятия не может быть null"));
        if (!crew.isEmpty() && members.leader().isEmpty()) {
            throw new IllegalArgumentException("У занятия '" + identity.id() + "' есть команда без лидера: "
                    + "assignee называет ведущего, команда — участников");
        }
        return new BoardTicket(
                identity.id(),
                activity,
                members.leader(),
                Math.min(BoardTuning.MAX_PRIORITY, Math.max(0, request.priority())),
                request.createdAt(),
                1,
                identity.cause(),
                List.of(),
                Map.of(),
                details(plan.script(), plan.target(), members.leader(), crew, plan.plannedTicks()),
                crew);
    }

    private static List<String> details(
            String script, String target, Optional<UUID> leader, List<UUID> crew, int plannedTicks) {
        List<String> details = new ArrayList<>();
        if (script != null && !script.isBlank()) {
            details.add(SCRIPT + script);
        }
        if (target != null && !target.isBlank()) {
            details.add(TARGET + target);
        }
        if (plannedTicks > 0) {
            details.add(PLANNED_TICKS + plannedTicks);
        }
        if (!crew.isEmpty()) {
            details.add(
                    TEAM + String.join(",", crew.stream().map(UUID::toString).toList()));
            leader.ifPresent(owner -> details.add(LEADER + owner));
        }
        return List.copyOf(details);
    }
}

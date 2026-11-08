package com.theminespirit.core.board;

import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.movement.SelfEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class RescuePlans {
    public static final String GOTO = "goto";
    public static final String CLEAR_AREA = "clear_area";
    public static final String WAIT = "wait";
    public static final String NODE = "graph:rescue_spirit";
    private static final String ID_PREFIX = "rescue:";

    public enum Self {
        NONE,
        PIT,
        WALK,
        DIG
    }

    public record Situation(boolean pit, boolean steps, boolean tool, boolean open, boolean noPath) {}

    public enum Mode {
        SELF,
        HELPER,
        IMPOSSIBLE
    }

    public record Assignment(Mode mode, String cause, Optional<UUID> assignee, String why) {
        public Assignment {
            Objects.requireNonNull(mode, "Способ спасения не может быть null: доска ставит билет или отказывает");
            Objects.requireNonNull(cause, "Причина спасения не может быть null: по ней доска отличает эпизоды");
            Objects.requireNonNull(assignee, "Исполнитель спасения не может быть null: помощника задают пустым");
            Objects.requireNonNull(why, "Объяснение спасения не может быть null: без него лог не читается");
        }
    }

    private RescuePlans() {}

    public static Assignment assign(UUID stuck, long since, @Nullable String selfRefusal, Optional<UUID> helper) {
        Objects.requireNonNull(stuck, "Застрявший дух не может быть null: спасение адресное");
        Objects.requireNonNull(helper, "Помощник не может быть null: его отсутствие задаётся пустым");
        String cause = cause(since);
        if (selfRefusal == null) {
            return new Assignment(Mode.SELF, cause, Optional.of(stuck), "выходит сам: план выхода есть");
        }
        if (helper.isPresent()) {
            return new Assignment(
                    Mode.HELPER, cause, helper, "сам не выйдет (" + selfRefusal + "): помощник с достижимым путём");
        }
        return new Assignment(
                Mode.IMPOSSIBLE,
                cause,
                Optional.empty(),
                "невозможно: " + selfRefusal + ", помощника с достижимым путём нет");
    }

    public static String cause(long since) {
        if (since < 0) {
            throw new IllegalArgumentException("Тик эпизода " + since
                    + " отрицателен: причина спасения отличает эпизоды по тику метки, а он идёт вперёд");
        }
        return NODE + "@" + since;
    }

    public static String claimNote(UUID helper, UUID rescuee, String helperName, String rescueeName) {
        Objects.requireNonNull(helper, "Помощник не может быть null: задание выдаётся конкретному духу");
        Objects.requireNonNull(rescuee, "Спасаемый не может быть null: по нему отличают самоспасение");
        Objects.requireNonNull(helperName, "Имя помощника не может быть null: без него журнал нем");
        Objects.requireNonNull(rescueeName, "Имя спасаемого не может быть null: без него журнал нем");
        return helper.equals(rescuee)
                ? rescueeName + " спасает себя"
                : helperName + " назначен помощником " + rescueeName;
    }

    public static Optional<UUID> rescuee(String ticketId) {
        Objects.requireNonNull(ticketId, "id задания спасения не может быть null: по нему находят спасённого");
        if (!ticketId.startsWith(ID_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(ticketId.substring(ID_PREFIX.length())));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    public static void complete(StuckSpirits stuck, BoardTicket ticket, @Nullable UUID holder, long now) {
        Objects.requireNonNull(stuck, "Метки застревания не могут быть null: спасение снимает одну из них");
        Objects.requireNonNull(ticket, "Задание спасения не может быть null: по нему находят спасённого");
        if (ticket.boardCase() == BoardCase.RESCUE_SPIRIT) {
            Optional<UUID> rescuee = rescuee(ticket.id()).or(() -> Optional.ofNullable(holder));
            if (holder != null
                    && rescuee.filter(spirit -> !holder.equals(spirit)).isPresent()) {
                stuck.release(holder, now, "помощник дошёл до цели: задание " + ticket.id());
            }
            return;
        }
        if (holder != null) {
            stuck.release(holder, now, "дошёл до цели: задание " + ticket.id());
        }
    }

    public static Self self(Situation situation) {
        if (situation.pit()) {
            return situation.steps() || situation.tool() ? Self.PIT : Self.NONE;
        }
        if (situation.open() && !situation.noPath()) {
            return Self.WALK;
        }
        return situation.tool() ? Self.DIG : Self.NONE;
    }

    public static Optional<TaskNode> plan(BoardCase boardCase, TicketTarget target) {
        Objects.requireNonNull(boardCase, "Кейс спасения не может быть null: план строится по кейсу");
        Objects.requireNonNull(target, "Цель спасения не может быть null: без координат духа не выручить");
        if (boardCase != BoardCase.RESCUE_SPIRIT) {
            return Optional.empty();
        }
        SelfEscape.Plan rescue = target.rescue();
        BlockCoord goal = target.to();
        if (rescue != null) {
            BlockCoord point = goal == null ? rescue.target() : goal;
            if (rescue.method() == SelfEscape.Method.GOTO) {
                return Optional.of(gotoPoint(point));
            }
            if (rescue.method() == SelfEscape.Method.DESCEND) {
                return Optional.of(new SequenceNode(List.of(digOut(point), gotoPoint(point))));
            }
            return Optional.of(withGoal(selfPlan(rescue), goal));
        }
        BlockCoord trapped = target.target();
        return Optional.of(helper(trapped, goal));
    }

    public static TaskNode helper(BlockCoord trapped, @Nullable BlockCoord goal) {
        return helper(trapped, goal, null);
    }

    public static TaskNode helper(BlockCoord trapped, @Nullable BlockCoord goal, PitExitPlanner.@Nullable Plan escape) {
        Objects.requireNonNull(trapped, "Цель спасения не может быть null: помощнику нужно к кому идти");
        BlockCoord from = new BlockCoord(trapped.x() - 1, trapped.y(), trapped.z() - 1);
        BlockCoord to = new BlockCoord(trapped.x() + 1, trapped.y() + 1, trapped.z() + 1);
        List<TaskNode> steps = new ArrayList<>(List.of(
                new FallbackNode(List.of(
                        gotoPoint(trapped), new ScriptCall(WAIT, Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN)))),
                new ScriptCall(
                        CLEAR_AREA,
                        Map.of(
                                "x1", from.x(),
                                "y1", from.y(),
                                "z1", from.z(),
                                "x2", to.x(),
                                "y2", to.y(),
                                "z2", to.z()))));
        addEscape(steps, escape);
        if (goal != null) {
            steps.add(gotoPoint(goal));
        }
        return new SequenceNode(List.copyOf(steps));
    }

    private static void addEscape(List<TaskNode> steps, PitExitPlanner.@Nullable Plan escape) {
        if (escape == null) {
            return;
        }
        switch (escape.method()) {
            case PILLAR -> {
                steps.add(new ScriptCall("pillar_up", Map.of("height", escape.rise())));
                steps.add(gotoPoint(escape.exit()));
            }
            case STAIRS -> {
                steps.add(digOut(escape.exit()));
                steps.add(gotoPoint(escape.exit()));
            }
            case WALK, IMPOSSIBLE -> {}
        }
    }

    private static TaskNode withGoal(TaskNode escape, @Nullable BlockCoord goal) {
        if (goal == null) {
            return escape;
        }
        return new SequenceNode(List.of(escape, digOut(goal), gotoPoint(goal)));
    }

    private static ScriptCall digOut(BlockCoord point) {
        return new ScriptCall("dig_out", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static TaskNode selfPlan(SelfEscape.Plan rescue) {
        if (rescue.method() == SelfEscape.Method.DESCEND) {
            return digOut(rescue.target());
        }
        if (rescue.method() == SelfEscape.Method.GOTO) {
            return gotoPoint(rescue.target());
        }
        if (rescue.method() == SelfEscape.Method.STEP) {
            return new SequenceNode(
                    List.of(new ScriptCall("pillar_up", Map.of("height", rescue.rise())), gotoPoint(rescue.target())));
        }
        if (rescue.rise() > 0) {
            return new ScriptCall(
                    "dig_out",
                    Map.of(
                            "x", rescue.target().x(),
                            "y", rescue.target().y(),
                            "z", rescue.target().z()));
        }
        List<TaskNode> steps = new ArrayList<>();
        for (BlockCoord block : rescue.blocks()) {
            steps.add(new ScriptCall("dig_area", Map.of("x", block.x(), "y", block.y(), "z", block.z(), "size", 1)));
        }
        steps.add(gotoPoint(rescue.target()));
        return new SequenceNode(steps);
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall(GOTO, Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }
}

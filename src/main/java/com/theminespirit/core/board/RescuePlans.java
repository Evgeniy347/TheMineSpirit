package com.theminespirit.core.board;

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

public final class RescuePlans {
    public static final String GOTO = "goto";
    public static final String CLEAR_AREA = "clear_area";
    public static final String WAIT = "wait";

    private RescuePlans() {}

    public static Optional<TaskNode> plan(BoardCase boardCase, TicketTarget target) {
        Objects.requireNonNull(boardCase, "Кейс спасения не может быть null: план строится по кейсу");
        Objects.requireNonNull(target, "Цель спасения не может быть null: без координат духа не выручить");
        if (boardCase != BoardCase.RESCUE_SPIRIT) {
            return Optional.empty();
        }
        SelfEscape.Plan rescue = target.rescue();
        if (rescue != null) {
            return Optional.of(selfPlan(rescue));
        }
        BlockCoord trapped = target.target();
        BlockCoord from = new BlockCoord(trapped.x() - 1, trapped.y(), trapped.z() - 1);
        BlockCoord to = new BlockCoord(trapped.x() + 1, trapped.y() + 1, trapped.z() + 1);
        return Optional.of(new SequenceNode(List.of(
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
                                "z2", to.z())),
                gotoPoint(trapped))));
    }

    private static TaskNode selfPlan(SelfEscape.Plan rescue) {
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

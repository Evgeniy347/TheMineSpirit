package com.theminespirit.core.board;

import com.theminespirit.core.ErrorText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TicketTargets {
    private final Map<String, TicketTarget> targets;
    private final List<Broken> broken;

    private TicketTargets(Map<String, TicketTarget> targets, List<Broken> broken) {
        this.targets = targets;
        this.broken = broken;
    }

    public static TicketTargets decode(Map<String, Map<String, Object>> raw) {
        Objects.requireNonNull(raw, "Записи целей не могут быть null: пустую доску задают пустой картой");
        Map<String, TicketTarget> targets = new LinkedHashMap<>();
        List<Broken> broken = new ArrayList<>();
        raw.forEach((id, fields) -> {
            try {
                targets.put(id, TicketTargetCodec.decode(fields));
            } catch (IllegalArgumentException unreadable) {
                broken.add(new Broken(id, ErrorText.of(unreadable)));
            }
        });
        return new TicketTargets(Map.copyOf(targets), List.copyOf(broken));
    }

    public Map<String, TicketTarget> targets() {
        return targets;
    }

    public List<Broken> broken() {
        return broken;
    }

    public static List<String> dropBroken(Board board, List<String> broken) {
        Objects.requireNonNull(board, "Доска не может быть null: снимать нечитаемые задания неоткуда");
        Objects.requireNonNull(broken, "Список нечитаемых целей не может быть null: пустой задают пустым списком");
        List<String> cancelled = new ArrayList<>();
        for (String id : broken) {
            if (board.cancel(id)) {
                cancelled.add(id);
            }
        }
        return List.copyOf(cancelled);
    }

    public record Broken(String id, String message) {}
}

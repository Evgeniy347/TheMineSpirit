package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class DispatchRound {
    public record Candidate(
            UUID id, String name, @Nullable String busyWith, List<String> items, EatNeed eat, SpiritView view) {
        public Candidate {
            Objects.requireNonNull(id, "Id духа выдачи не может быть null: задание выдаётся конкретному духу");
            Objects.requireNonNull(name, "Имя духа выдачи не может быть null: оно пишется в причину ожидания");
            items = List.copyOf(items);
            Objects.requireNonNull(eat, "Сытость духа выдачи не может быть null: по ней решают еду");
            Objects.requireNonNull(view, "Состояние духа выдачи не может быть null: по нему решают применимость");
        }
    }

    private final Board board;
    private final long now;
    private final Map<UUID, Integer> held;
    private final Map<UUID, String> awaiting = new HashMap<>();

    public DispatchRound(Board board, long now) {
        this.board = Objects.requireNonNull(board, "Доска раунда выдачи не может быть null: задания берутся с неё");
        this.now = now;
        this.held = new HashMap<>(board.assignedCounts());
    }

    public static List<BoardTicket> order(List<BoardTicket> claimable) {
        List<BoardTicket> own = new ArrayList<>();
        List<BoardTicket> open = new ArrayList<>();
        for (BoardTicket ticket : claimable) {
            (ticket.assignee().isPresent() ? own : open).add(ticket);
        }
        own.addAll(open);
        return List.copyOf(own);
    }

    public Optional<String> refusal(BoardTicket ticket, Candidate spirit) {
        return refusal(ticket, spirit, null);
    }

    public Optional<String> refusal(BoardTicket ticket, Candidate spirit, @Nullable String supplyItem) {
        if (spirit.busyWith() != null) {
            return Optional.of("занят: " + spirit.busyWith());
        }
        String waits = awaiting.get(spirit.id());
        if (waits != null) {
            return Optional.of(waits);
        }
        if (held.getOrDefault(spirit.id(), 0) >= BoardTuning.maxActivePerSpirit()) {
            return Optional.of("держит " + heldIds(spirit.id()));
        }
        if (ticket.assignee().filter(owner -> !owner.equals(spirit.id())).isPresent()) {
            return Optional.of("задание другого духа");
        }
        if (!board.availableTo(spirit.id(), ticket.boardCase(), now)) {
            return Optional.of("на паузе кейса " + ticket.boardCase() + " после "
                    + board.consecutiveFailures(spirit.id(), ticket.boardCase()) + " провалов");
        }
        if (!ToolGate.allows(ticket.boardCase(), spirit.items(), supplyItem)) {
            return Optional.of("нет инструмента "
                    + ToolGate.required(ticket.boardCase(), supplyItem).orElse("?"));
        }
        if (!EvergreenRules.issuedTo(ticket, spirit.id(), spirit.eat(), spirit.view())) {
            return Optional.of("кейс " + ticket.boardCase() + " ему сейчас не к месту");
        }
        return Optional.empty();
    }

    public boolean fits(BoardTicket ticket, Candidate spirit) {
        return fits(ticket, spirit, null);
    }

    public boolean fits(BoardTicket ticket, Candidate spirit, @Nullable String supplyItem) {
        return refusal(ticket, spirit, supplyItem).isEmpty();
    }

    public Optional<Candidate> choose(BoardTicket ticket, List<Candidate> preference) {
        return choose(ticket, preference, null);
    }

    public Optional<Candidate> choose(BoardTicket ticket, List<Candidate> preference, @Nullable String supplyItem) {
        for (Candidate spirit : preference) {
            if (fits(ticket, spirit, supplyItem)) {
                return Optional.of(spirit);
            }
        }
        return Optional.empty();
    }

    public void take(UUID spirit) {
        held.merge(spirit, 1, Integer::sum);
    }

    public void await(UUID spirit, String why) {
        awaiting.putIfAbsent(spirit, why);
    }

    public void release(UUID spirit) {
        awaiting.remove(spirit);
    }

    public boolean awaited(UUID spirit) {
        return awaiting.containsKey(spirit);
    }

    public String why(BoardTicket ticket, List<Candidate> spirits) {
        return why(ticket, spirits, null);
    }

    public String why(BoardTicket ticket, List<Candidate> spirits, @Nullable String supplyItem) {
        List<String> parts = new ArrayList<>();
        for (Candidate spirit : spirits) {
            if (ticket.assignee().filter(owner -> !owner.equals(spirit.id())).isPresent()) {
                continue;
            }
            parts.add(
                    spirit.name() + " — " + refusal(ticket, spirit, supplyItem).orElse("свободен, но выдача отложена"));
        }
        if (parts.isEmpty()) {
            return ticket.assignee().isPresent() ? "своего духа нет в мире" : "нет духов";
        }
        return String.join("; ", parts);
    }

    private String heldIds(UUID spirit) {
        List<String> ids = new ArrayList<>();
        for (BoardTicket ticket : board.assignedTo(spirit)) {
            ids.add(ticket.id());
        }
        return ids.isEmpty() ? "задание этого раунда" : String.join(", ", ids);
    }
}

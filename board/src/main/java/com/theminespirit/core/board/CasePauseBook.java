package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class CasePauseBook {
    private static final String SUPPLY_GATHER_PREFIX = "supply_gather:";
    private static final String GRAPH_MAKE_PREFIX = "graph:make:";

    private final Map<SpiritCase, CaseFailures> failures = new LinkedHashMap<>();

    boolean available(UUID spirit, BoardCase boardCase, long now) {
        Objects.requireNonNull(spirit, "spirit не может быть null: пауза считается на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: пауза считается на конкретный кейс");
        CaseFailures known = failures.get(new SpiritCase(spirit, boardCase));
        if (known == null) {
            return true;
        }
        if (known.detached) {
            return released(known, now);
        }
        return now >= known.pausedUntil;
    }

    boolean available(UUID spirit, BoardTicket ticket, long now) {
        Objects.requireNonNull(spirit, "spirit не может быть null: пауза считается на конкретного духа");
        Objects.requireNonNull(ticket, "задание не может быть null: пауза добычи считается по его предмету");
        CaseFailures known = failures.get(new SpiritCase(spirit, ticket.boardCase()));
        if (known == null) {
            return true;
        }
        Optional<String> token = pauseToken(ticket);
        if (changed(known, token)) {
            release(known);
            return true;
        }
        if (known.detached) {
            return released(known, now);
        }
        return now >= known.pausedUntil;
    }

    private static boolean released(CaseFailures known, long now) {
        if (now < known.pausedUntil) {
            return false;
        }
        release(known);
        return true;
    }

    private static boolean changed(CaseFailures known, Optional<String> token) {
        return known.token.isPresent() && token.isPresent() && !known.token.equals(token);
    }

    private static void release(CaseFailures known) {
        known.consecutive = 0;
        known.detached = false;
        known.pausedUntil = 0;
    }

    boolean detached(UUID spirit, BoardCase boardCase) {
        Objects.requireNonNull(spirit, "spirit не может быть null: снятие считается на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: снятие считается на конкретный кейс");
        CaseFailures known = failures.get(new SpiritCase(spirit, boardCase));
        return known != null && known.detached;
    }

    int consecutive(UUID spirit, BoardCase boardCase) {
        Objects.requireNonNull(spirit, "spirit не может быть null: счёт провалов идёт на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: счёт провалов идёт на кейс");
        CaseFailures known = failures.get(new SpiritCase(spirit, boardCase));
        return known == null ? 0 : known.consecutive;
    }

    long pauseTicks(UUID spirit, BoardCase boardCase) {
        Objects.requireNonNull(spirit, "spirit не может быть null: пауза считается на конкретного духа");
        Objects.requireNonNull(boardCase, "boardCase не может быть null: пауза считается на конкретный кейс");
        CaseFailures known = failures.get(new SpiritCase(spirit, boardCase));
        return known == null ? 0 : BoardBackoff.pauseTicks(known.consecutive);
    }

    boolean noteFailure(UUID spirit, BoardTicket ticket, long now) {
        SpiritCase key = new SpiritCase(spirit, ticket.boardCase());
        CaseFailures known = failures.computeIfAbsent(key, ignored -> new CaseFailures());
        Optional<String> token = pauseToken(ticket);
        if (changed(known, token)) {
            known.consecutive = 0;
            known.detached = false;
        }
        known.token = token;
        known.consecutive++;
        known.pausedUntil = now + BoardBackoff.pauseTicks(known.consecutive);
        if (!known.detached
                && known.consecutive >= BoardTuning.caseFailLimit()
                && ticket.boardCase() != BoardCase.RESCUE_SPIRIT) {
            known.detached = true;
            known.pausedUntil = now + BoardTuning.caseFailPauseCapTicks();
            return true;
        }
        return false;
    }

    int releaseDetached() {
        int released = 0;
        for (CaseFailures known : failures.values()) {
            if (known.detached) {
                release(known);
                released++;
            }
        }
        return released;
    }

    void reset(UUID spirit, BoardCase boardCase) {
        failures.remove(new SpiritCase(spirit, boardCase));
    }

    void clear() {
        failures.clear();
    }

    List<BoardState.Pause> snapshot() {
        List<BoardState.Pause> states = new ArrayList<>();
        for (Map.Entry<SpiritCase, CaseFailures> entry : failures.entrySet()) {
            CaseFailures known = entry.getValue();
            states.add(new BoardState.Pause(
                    entry.getKey().spirit(),
                    entry.getKey().boardCase(),
                    known.consecutive,
                    known.pausedUntil,
                    known.detached,
                    known.token.orElse(null)));
        }
        return List.copyOf(states);
    }

    void restore(List<BoardState.Pause> states) {
        failures.clear();
        for (BoardState.Pause state : states) {
            CaseFailures known = new CaseFailures();
            known.consecutive = state.consecutive();
            known.pausedUntil = state.pausedUntil();
            known.detached = state.detached();
            known.token = Optional.ofNullable(state.token());
            failures.put(new SpiritCase(state.spirit(), state.boardCase()), known);
        }
    }

    private static Optional<String> pauseToken(BoardTicket ticket) {
        if (ticket.boardCase() != BoardCase.GATHER_MATERIALS) {
            return Optional.empty();
        }
        String id = ticket.id();
        String prefix;
        if (id.startsWith(SUPPLY_GATHER_PREFIX)) {
            prefix = SUPPLY_GATHER_PREFIX;
        } else if (id.startsWith(GRAPH_MAKE_PREFIX)) {
            prefix = GRAPH_MAKE_PREFIX;
        } else {
            return Optional.empty();
        }
        String rest = id.substring(prefix.length());
        int part = rest.indexOf(':');
        return Optional.of(part < 0 ? rest : rest.substring(0, part));
    }

    private record SpiritCase(UUID spirit, BoardCase boardCase) {}

    private static final class CaseFailures {
        private int consecutive;
        private long pausedUntil;
        private boolean detached;
        private Optional<String> token = Optional.empty();
    }
}

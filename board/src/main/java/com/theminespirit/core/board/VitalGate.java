package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.VitalNeeds;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class VitalGate {
    private static final Set<BoardCase> EXEMPT = Set.of(
            BoardCase.ACTIVITY_DEFEND, BoardCase.ACTIVITY_RETREAT, BoardCase.ACTIVITY_ESCAPE, BoardCase.RESCUE_SPIRIT);

    private VitalGate() {}

    public static Optional<String> refusal(BoardTicket ticket, Map<String, String> live, List<VitalNeeds.Need> needs) {
        Objects.requireNonNull(ticket, "Билет выдачи не может быть null: гейт проверяет конкретную работу");
        Objects.requireNonNull(live, "Живые нужды не могут быть null: у сытого и здорового — пустая карта");
        Objects.requireNonNull(needs, "Таблица нужд не может быть null: по ней находят путь удовлетворения");
        if (live.isEmpty() || exempt(ticket)) {
            return Optional.empty();
        }
        for (VitalNeeds.Need need : needs) {
            if (need.gate() && live.containsKey(need.id()) && need.allows(ticket.id())) {
                return Optional.empty();
            }
        }
        String reason = needs.stream()
                .filter(need -> need.gate() && live.containsKey(need.id()))
                .map(need -> live.get(need.id()))
                .findFirst()
                .orElse("жизненная нужда");
        return Optional.of(reason + " — обычная работа не выдаётся, пока нужда жива");
    }

    public static boolean satisfies(String ticketId, Map<String, String> live) {
        Objects.requireNonNull(ticketId, "id билета не может быть null: нужда ищет путь по нему");
        Objects.requireNonNull(live, "Живые нужды не могут быть null: пустые — пустая карта");
        if (live.isEmpty()) {
            return false;
        }
        for (VitalNeeds.Need need : VitalNeeds.needs()) {
            if (need.gate() && live.containsKey(need.id()) && need.allows(ticketId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean exempt(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "Билет не может быть null: исключения проверяются по кейсу");
        return EXEMPT.contains(ticket.boardCase()) || ticket.id().startsWith("graph:event:life_");
    }

    public static boolean blocksWork(BoardTicket ticket, Map<String, String> live) {
        return !exempt(ticket) && !satisfies(ticket.id(), live);
    }
}

package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class DispatchWhy {
    private DispatchWhy() {}

    static String text(
            DispatchRound round,
            BoardTicket ticket,
            List<DispatchRound.Candidate> spirits,
            @Nullable String supplyItem) {
        List<String> free = new ArrayList<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        List<String> busy = new ArrayList<>();
        int eligible = 0;
        for (DispatchRound.Candidate spirit : spirits) {
            if (ticket.assignee().filter(owner -> !owner.equals(spirit.id())).isPresent()) {
                continue;
            }
            eligible++;
            String reason = round.refusal(ticket, spirit, supplyItem).orElse(null);
            if (reason == null) {
                free.add(spirit.name());
            } else if (reason.startsWith("занят: ")) {
                busy.add(reason.substring("занят: ".length()));
            } else {
                groups.computeIfAbsent(reason, ignored -> new ArrayList<>()).add(spirit.name());
            }
        }
        if (eligible == 0) {
            return ticket.assignee().isPresent() ? "своего духа нет в мире" : "нет духов";
        }
        if (groups.isEmpty()) {
            return "ждёт свободного духа: заняты " + eligible + " (" + distinct(busy, 4) + ")";
        }
        List<String> blockerLines = new ArrayList<>();
        groups.forEach((reason, names) -> blockerLines.add(
                names.size() == 1 ? names.get(0) + " — " + reason : reason + ": " + distinct(names, 4)));
        for (String name : free) {
            blockerLines.add("свободен, но выдача отложена: " + name);
        }
        String text = "ждёт духа: " + String.join("; ", blockerLines);
        if (!busy.isEmpty()) {
            text += "; ещё занято " + busy.size();
        }
        return text;
    }

    private static String distinct(List<String> values, int limit) {
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(values));
        if (unique.size() <= limit) {
            return String.join(", ", unique);
        }
        return String.join(", ", unique.subList(0, limit)) + ", …";
    }
}

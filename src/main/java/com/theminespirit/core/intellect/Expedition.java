package com.theminespirit.core.intellect;

import com.theminespirit.core.tactics.Point;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record Expedition(List<UUID> team, Point target, long startedAt) {
    public Expedition {
        if (team == null) {
            throw new IllegalArgumentException("Экспедиция без команды не идёт: в одиночку в шахту не ходят");
        }
        if (team.size() < 2 || team.size() > 3) {
            throw new IllegalArgumentException("Команда экспедиции = " + team.size()
                    + ": ходят командой 2–3 духа, иначе либо опасно, либо толпа");
        }
        if (new HashSet<>(team).size() != team.size() || team.contains(null)) {
            throw new IllegalArgumentException(
                    "Команда с повторами или null-духом не идёт: каждый дух — конкретный участник");
        }
        if (target == null) {
            throw new IllegalArgumentException("Экспедиция без цели не идёт: шахта обязана быть известна");
        }
        if (startedAt < 0) {
            throw new IllegalArgumentException(
                    "Экспедиция со стартом " + startedAt + ": время не может быть отрицательным");
        }
        team = List.copyOf(team);
    }
}

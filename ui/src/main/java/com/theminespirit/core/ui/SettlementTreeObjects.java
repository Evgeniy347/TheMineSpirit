package com.theminespirit.core.ui;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageRules;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SettlementTreeObjects {
    record Ref(
            String id,
            String title,
            String icon,
            int level,
            boolean optional,
            boolean blocking,
            @Nullable String planId,
            @Nullable BoardCase boardCase) {
        Ref {
            Objects.requireNonNull(id, "id объекта этапа не может быть null");
            Objects.requireNonNull(title, "Название объекта не может быть null");
            Objects.requireNonNull(icon, "Иконка объекта не может быть null");
            if ((planId == null) == (boardCase == null)) {
                throw new IllegalArgumentException("Объект '" + id
                        + "' обязан назвать либо постройку плана, либо кейс доски: иначе неизвестно, что проверять");
            }
        }
    }

    private SettlementTreeObjects() {}

    static List<Ref> of(StageRules.Stage stage, SettlementTreeIcons icons) {
        Objects.requireNonNull(stage, "Этап не может быть null: по нему строится вкладка древа");
        Objects.requireNonNull(icons, "Таблица иконок не может быть null: без неё рамки пустые");
        Set<String> named = new LinkedHashSet<>();
        List<Ref> refs = new ArrayList<>();
        stage.requires().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> add(refs, named, plan(entry.getKey(), entry.getValue(), false, true, icons)));
        stage.optional().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> add(refs, named, plan(entry.getKey(), entry.getValue(), true, false, icons)));
        stage.buildings().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> add(refs, named, board(entry.getKey(), entry.getValue(), icons)));
        for (String planId : stage.plan()) {
            if (named.contains(planId) || hiddenByBuildings(planId, stage, icons)) {
                continue;
            }
            add(refs, named, plan(planId, 1, false, false, icons));
        }
        return List.copyOf(refs);
    }

    static int maxLevel(Ref ref) {
        if (ref.planId() == null) {
            return ref.level();
        }
        int catalog = StageCatalog.maxLevel(ref.planId());
        return Math.max(ref.level(), catalog);
    }

    private static void add(List<Ref> refs, Set<String> named, Ref ref) {
        if (named.add(ref.id())) {
            refs.add(ref);
        }
    }

    private static Ref plan(String planId, int level, boolean optional, boolean blocking, SettlementTreeIcons icons) {
        SettlementTreeIcons.Entry entry = icons.planEntry(planId);
        return new Ref(planId, entry.title(), entry.icon(), level, optional, blocking, planId, null);
    }

    private static Ref board(BoardCase boardCase, int level, SettlementTreeIcons icons) {
        SettlementTreeIcons.Entry entry = icons.caseEntry(boardCase);
        return new Ref(boardCase.name(), entry.title(), entry.icon(), level, false, true, null, boardCase);
    }

    private static boolean hiddenByBuildings(String planId, StageRules.Stage stage, SettlementTreeIcons icons) {
        return icons.alias(planId)
                .map(caseValue -> stage.buildings().keySet().stream().anyMatch(caseValue::equals))
                .orElse(false);
    }
}

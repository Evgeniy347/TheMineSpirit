package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GatherConsolidation {
    public record Result(List<GatherDemand.Need> submit, Set<String> suppressed) {
        public Result {
            Objects.requireNonNull(submit, "Что ставить на доску не может быть null: пустое задают пустым списком");
            Objects.requireNonNull(suppressed, "Снятые заявки не могут быть null: пустой набор задают пустым");
            submit = List.copyOf(submit);
            suppressed = Set.copyOf(suppressed);
        }
    }

    private GatherConsolidation() {}

    public static Result of(List<GatherDemand.Need> standing, List<GatherDemand.Need> fresh) {
        Objects.requireNonNull(standing, "Стоящие заявки не могут быть null: пустые задают пустым списком");
        Objects.requireNonNull(fresh, "Новый спрос не может быть null: пустой задают пустым списком");
        List<GatherDemand.Need> mergedFresh = GatherDemand.merge(fresh);
        List<GatherDemand.Need> all = new ArrayList<>(standing);
        all.addAll(fresh);
        List<GatherDemand.Need> mergedAll = GatherDemand.merge(all);
        List<GatherDemand.Need> chosen = GatherDemand.onePerWood(mergedAll);
        Set<String> keep = new HashSet<>();
        chosen.forEach(need -> keep.add(need.id()));
        Set<String> freshIds = new HashSet<>();
        mergedFresh.forEach(need -> freshIds.add(need.id()));
        List<GatherDemand.Need> submit =
                chosen.stream().filter(need -> freshIds.contains(need.id())).toList();
        Set<String> suppressed = new HashSet<>();
        for (GatherDemand.Need need : mergedAll) {
            if (GatherDemand.wood(need.item()) == null || keep.contains(need.id())) {
                continue;
            }
            suppressed.add(need.id());
            suppressed.add(need.cause());
        }
        return new Result(submit, suppressed);
    }
}

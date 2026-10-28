package com.theminespirit.core.husbandry;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.HerdCap;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.reload.DataTables;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class PetRules {
    private static final String TAME = "tame_";
    private static final String TRUST = "trust_";

    private PetRules() {}

    public static int cap(PetKind kind, int spirits) {
        Objects.requireNonNull(kind, "Вид питомца не может быть null: без него неизвестен лимит");
        HerdCap cap = DataTables.INSTANCE.graph().current().herdCaps().get(kind.id());
        if (cap == null) {
            throw new IllegalStateException("У питомца '" + kind.id() + "' нет лимита в herdCaps графа: "
                    + "без предела поголовье росло бы бесконтрольно, добавьте вид в board/graph.json");
        }
        return cap.limit(spirits);
    }

    public static List<String> tameFoods(PetKind kind) {
        return foods(kind, PetRules::taming);
    }

    public static List<String> breedFoods(PetKind kind) {
        return foods(kind, way -> way.boardCase() == BoardCase.BREED_ANIMALS);
    }

    private static List<String> foods(PetKind kind, Predicate<SupplyGraph.Way> accept) {
        Objects.requireNonNull(kind, "Вид питомца не может быть null: без него неизвестен корм");
        Set<String> foods = new LinkedHashSet<>();
        for (SupplyGraph.Way way : DataTables.INSTANCE.graph().current().ways()) {
            if (way.makes().equals(kind.id()) && accept.test(way)) {
                foods.addAll(way.consumes().keySet());
            }
        }
        if (foods.isEmpty()) {
            throw new IllegalStateException("У питомца '" + kind.id() + "' нет корма в путях графа: "
                    + "приручать или разводить его нечем, добавьте путь в board/graph.json");
        }
        return List.copyOf(foods);
    }

    private static boolean taming(SupplyGraph.Way way) {
        return way.id().startsWith(TAME) || way.id().startsWith(TRUST);
    }
}

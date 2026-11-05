package com.theminespirit.core.husbandry;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.HerdCap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class PetRules {
    private static final String TAME = "tame_";
    private static final String TRUST = "trust_";

    private PetRules() {}

    public record PetWay(String id, BoardCase boardCase, String makes, List<String> consumes) {
        public PetWay {
            Objects.requireNonNull(id, "id пути не может быть null: по нему отличают приручение от разведения");
            Objects.requireNonNull(boardCase, "работа пути не может быть null: по ней выбирают корм для стада");
            Objects.requireNonNull(makes, "вид питомца пути не может быть null: по нему ищут корм");
            consumes = List.copyOf(Objects.requireNonNull(consumes, "Корм пути не может быть null"));
        }
    }

    public static int cap(PetKind kind, int spirits, Map<String, HerdCap> caps) {
        Objects.requireNonNull(kind, "Вид питомца не может быть null: без него неизвестен лимит");
        Objects.requireNonNull(caps, "Лимиты стад не могут быть null: иначе предел неоткуда взять");
        HerdCap cap = caps.get(kind.id());
        if (cap == null) {
            throw new IllegalStateException("У питомца '" + kind.id() + "' нет лимита в herdCaps графа: "
                    + "без предела поголовье росло бы бесконтрольно, добавьте вид в board/graph.json");
        }
        return cap.limit(spirits);
    }

    public static List<String> tameFoods(PetKind kind, List<PetWay> ways) {
        return foods(kind, ways, PetRules::taming);
    }

    public static List<String> breedFoods(PetKind kind, List<PetWay> ways) {
        return foods(kind, ways, way -> way.boardCase() == BoardCase.BREED_ANIMALS);
    }

    private static List<String> foods(PetKind kind, List<PetWay> ways, Predicate<PetWay> accept) {
        Objects.requireNonNull(kind, "Вид питомца не может быть null: без него неизвестен корм");
        Objects.requireNonNull(ways, "Пути графа не могут быть null: из них берут корм");
        Set<String> foods = new LinkedHashSet<>();
        for (PetWay way : ways) {
            if (way.makes().equals(kind.id()) && accept.test(way)) {
                foods.addAll(way.consumes());
            }
        }
        if (foods.isEmpty()) {
            throw new IllegalStateException("У питомца '" + kind.id() + "' нет корма в путях графа: "
                    + "приручать или разводить его нечем, добавьте путь в board/graph.json");
        }
        return List.copyOf(foods);
    }

    private static boolean taming(PetWay way) {
        return way.id().startsWith(TAME) || way.id().startsWith(TRUST);
    }
}

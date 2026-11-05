package com.theminespirit.core.husbandry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BreedPlanner {
    private BreedPlanner() {}

    public static Outcome next(
            List<AnimalRef> herd,
            BlockCoord spiritPos,
            Species species,
            Map<String, Integer> inventory,
            boolean keepBabies) {
        Objects.requireNonNull(herd, "Снимок стада не может быть null: пустое стадо задают пустым списком");
        Objects.requireNonNull(spiritPos, "Точка духа не может быть null: по ней выбирается ближайшая пара");
        Objects.requireNonNull(species, "Вид не может быть null: пару сводят внутри одного вида");
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: пустой задают пустой картой");
        List<AnimalRef> ready = new ArrayList<>();
        boolean hasAdults = false;
        boolean hasBabies = false;
        for (AnimalRef animal : herd) {
            Objects.requireNonNull(animal, "Запись о животном не может быть null: снимок собирается только из живых");
            if (animal.species() != species) {
                continue;
            }
            if (animal.baby()) {
                hasBabies = true;
                continue;
            }
            hasAdults = true;
            if (animal.breedReady()) {
                ready.add(animal);
            }
        }
        if (!hasAdults && !hasBabies) {
            return new NothingToDo("в стаде нет " + species + ": сводить некого");
        }
        if (!hasAdults) {
            return new NothingToDo("все " + species + " ещё детёныши: пару сводят только из взрослых");
        }
        if (keepBabies && hasBabies) {
            return new NothingToDo("в стаде есть детёныши " + species + ": новых пар не сводят, пока держат молодняк");
        }
        ready.sort(Comparator.comparingLong((AnimalRef animal) -> distanceSquared(spiritPos, animal.pos()))
                .thenComparing(AnimalRef::id));
        if (ready.size() < 2) {
            return new NothingToDo("готово к разведению " + ready.size() + " из 2 " + species
                    + ": пару сводят только из двух готовых взрослых");
        }
        String feed = FeedTable.feedFor(species).get(0);
        int have = Math.max(0, inventory.getOrDefault(feed, 0));
        if (have < 2) {
            return new NeedsMoreFeed(feed, 2 - have);
        }
        return new Feed(ready.get(0).id(), ready.get(1).id());
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public sealed interface Outcome permits Feed, NeedsMoreFeed, NothingToDo {}

    public record Feed(String targetId, String partnerId) implements Outcome {
        public Feed {
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("id цели не может быть пустым: без него неизвестно, кого кормить");
            }
            if (partnerId == null || partnerId.isBlank()) {
                throw new IllegalArgumentException("id партнёра не может быть пустым: пару сводят из двух голов");
            }
            if (targetId.equals(partnerId)) {
                throw new IllegalArgumentException("Цель и партнёр совпадают: пару сводят из двух разных голов");
            }
        }
    }

    public record NeedsMoreFeed(String feedId, int missing) implements Outcome {
        public NeedsMoreFeed {
            if (feedId == null || feedId.isBlank()) {
                throw new IllegalArgumentException("id корма не может быть пустым: без него неизвестно, что нести");
            }
            if (missing <= 0) {
                throw new IllegalArgumentException("Нехватка обязана быть положительной: " + missing);
            }
        }
    }

    public record NothingToDo(String reason) implements Outcome {
        public NothingToDo {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Причина простоя не может быть пустой: стратег по ней решает, что делать дальше");
            }
        }
    }
}

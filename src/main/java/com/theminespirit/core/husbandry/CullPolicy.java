package com.theminespirit.core.husbandry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CullPolicy {
    public static final int MIN_KEEP = 2;

    private CullPolicy() {
    }

    public static Outcome pick(List<AnimalRef> herd, BlockCoord penCenter, Species species, int keep,
            SwordTier sword) {
        Objects.requireNonNull(herd, "Снимок стада не может быть null: пустое стадо задают пустым списком");
        Objects.requireNonNull(penCenter, "Центр загона не может быть null: по нему выбираются дальние головы");
        Objects.requireNonNull(species, "Вид не может быть null: норму держат внутри одного вида");
        Objects.requireNonNull(sword, "Тир меча не может быть null: пустую руку задают SwordTier.HAND");
        if (keep < MIN_KEEP) {
            throw new IllegalArgumentException("Держать меньше " + MIN_KEEP + " голов нельзя: пара нужна для "
                    + "разведения, стадо выродится до нуля");
        }
        if (!sword.atLeast(SwordTier.IRON)) {
            return new Refused("забивать можно железным мечом и выше, сейчас: " + sword);
        }
        List<AnimalRef> adults = new ArrayList<>();
        for (AnimalRef animal : herd) {
            Objects.requireNonNull(animal, "Запись о животном не может быть null: снимок собирается только из живых");
            if (animal.species() == species && !animal.baby()) {
                adults.add(animal);
            }
        }
        adults.sort(Comparator.comparingLong((AnimalRef animal) -> distanceSquared(penCenter, animal.pos()))
                .reversed().thenComparing(AnimalRef::id));
        int extra = adults.size() - keep;
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < extra; i++) {
            picked.add(adults.get(i).id());
        }
        return new Picked(List.copyOf(picked));
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public sealed interface Outcome permits Picked, Refused {
    }

    public record Picked(List<String> ids) implements Outcome {
        public Picked {
            Objects.requireNonNull(ids, "Список на забой не может быть null: пустой набор задают пустым списком");
            ids = List.copyOf(ids);
        }
    }

    public record Refused(String reason) implements Outcome {
        public Refused {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Причина отказа не может быть пустой: стратег по ней решает, какой меч нести");
            }
        }
    }
}

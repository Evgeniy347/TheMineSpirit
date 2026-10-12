package com.theminespirit.core.husbandry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HerdCensus {
    private HerdCensus() {
    }

    public static Census summarize(List<AnimalRef> herd) {
        Objects.requireNonNull(herd, "Снимок стада не может быть null: пустое стадо задают пустым списком");
        Map<Species, Counts> bySpecies = new EnumMap<>(Species.class);
        for (AnimalRef animal : herd) {
            Objects.requireNonNull(animal, "Запись о животном не может быть null: снимок собирается только из живых");
            Counts current = bySpecies.getOrDefault(animal.species(), new Counts(0, 0, 0));
            int adults = current.adults() + (animal.baby() ? 0 : 1);
            int babies = current.babies() + (animal.baby() ? 1 : 0);
            int outOfPen = current.outOfPen() + (animal.inPen() ? 0 : 1);
            bySpecies.put(animal.species(), new Counts(adults, babies, outOfPen));
        }
        return new Census(Map.copyOf(bySpecies), herd.size());
    }

    public record Counts(int adults, int babies, int outOfPen) {
        public Counts {
            if (adults < 0 || babies < 0 || outOfPen < 0) {
                throw new IllegalArgumentException("Счётчики стада не могут быть отрицательными: adults=" + adults
                        + ", babies=" + babies + ", outOfPen=" + outOfPen);
            }
        }

        public int total() {
            return adults + babies;
        }
    }

    public record Census(Map<Species, Counts> bySpecies, int total) {
        public Census {
            Objects.requireNonNull(bySpecies, "Сводка по видам не может быть null: пустое стадо задают пустой картой");
            bySpecies = Map.copyOf(bySpecies);
            if (total < 0) {
                throw new IllegalArgumentException("Размер стада не может быть отрицательным: " + total);
            }
        }

        public Counts of(Species species) {
            Objects.requireNonNull(species, "Вид не может быть null: сводка читается по виду");
            return bySpecies.getOrDefault(species, new Counts(0, 0, 0));
        }
    }
}

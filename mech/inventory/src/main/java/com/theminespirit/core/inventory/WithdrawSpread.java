package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class WithdrawSpread {
    public record Step(int index, int count) {
        public Step {
            if (index < 0) {
                throw new IllegalArgumentException("Индекс контейнера " + index
                        + " отрицательный: контейнеры нумеруются с нуля, отрицательного не существует");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Шаг забора из контейнера " + index + " несёт " + count
                        + " предметов: пустой шаг не двигает духа, его не должно быть в плане");
            }
        }
    }

    public record Plan(List<Step> steps, int left) {
        public Plan {
            Objects.requireNonNull(steps, "Шаги плана не могут быть null: пустой план задают пустым списком");
            steps = List.copyOf(steps);
            if (left < 0) {
                throw new IllegalArgumentException(
                        "Осталось " + left + " предметов: отрицательный остаток не бывает, ошибка в подсчёте плана");
            }
        }

        public boolean complete() {
            return left == 0;
        }
    }

    private WithdrawSpread() {}

    public static Plan plan(List<ContainerSnapshot> containers, BlockCoord origin, String item, int requested) {
        Objects.requireNonNull(containers, "Список контейнеров не может быть null: пустой задают пустым списком");
        Objects.requireNonNull(origin, "Позиция духа не может быть null: без неё не выбрать ближайший контейнер");
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Предмет не назван: без него непонятно, что забирать из контейнеров");
        }
        if (requested <= 0) {
            throw new IllegalArgumentException("Запрошено " + requested
                    + " предметов: перенос начинается хотя бы с одного, иначе дух идёт к сундуку зря");
        }
        IdFilter filter = IdFilter.of(List.of(item));
        List<Candidate> candidates = new ArrayList<>(containers.size());
        for (int index = 0; index < containers.size(); index++) {
            ContainerSnapshot snapshot = containers.get(index);
            candidates.add(new Candidate(index, snapshot, distanceSqr(origin, snapshot.pos())));
        }
        candidates.sort(Comparator.comparingLong(Candidate::distance));
        List<Step> steps = new ArrayList<>();
        int left = requested;
        for (Candidate candidate : candidates) {
            if (left <= 0) {
                break;
            }
            int count = count(candidate.snapshot(), filter);
            if (count <= 0) {
                continue;
            }
            int take = Math.min(left, count);
            steps.add(new Step(candidate.index(), take));
            left -= take;
        }
        return new Plan(steps, left);
    }

    public static int count(ContainerSnapshot snapshot, IdFilter filter) {
        int total = 0;
        for (Stack stack : snapshot.slots()) {
            if (stack != null && filter.matches(stack.item())) {
                total += stack.count();
            }
        }
        return total;
    }

    private static long distanceSqr(BlockCoord from, BlockCoord to) {
        long dx = (long) from.x() - to.x();
        long dy = (long) from.y() - to.y();
        long dz = (long) from.z() - to.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private record Candidate(int index, ContainerSnapshot snapshot, long distance) {}
}

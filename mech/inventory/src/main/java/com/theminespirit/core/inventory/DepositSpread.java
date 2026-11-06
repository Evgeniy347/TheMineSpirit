package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class DepositSpread {
    public record Step(int index, int count) {
        public Step {
            if (index < 0) {
                throw new IllegalArgumentException("Индекс контейнера " + index
                        + " отрицательный: контейнеры нумеруются с нуля, отрицательного не существует");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Шаг переноса в контейнер " + index + " несёт " + count
                        + " предметов: пустой шаг не двигает духа, его не должно быть в плане");
            }
        }
    }

    public record Plan(List<Step> steps, int left, @Nullable String refusal) {
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

    private DepositSpread() {}

    public static Plan plan(List<ContainerSnapshot> containers, BlockCoord origin, Stack incoming, int requested) {
        return plan(containers, origin, incoming, requested, 0);
    }

    public static Plan plan(
            List<ContainerSnapshot> containers, BlockCoord origin, Stack incoming, int requested, int minFreePercent) {
        Objects.requireNonNull(containers, "Список контейнеров не может быть null: пустой задают пустым списком");
        Objects.requireNonNull(origin, "Позиция духа не может быть null: без неё не выбрать ближайший контейнер");
        Objects.requireNonNull(incoming, "Предмет не может быть null: без него непонятно, что сдавать");
        if (requested <= 0) {
            throw new IllegalArgumentException("Запрошено " + requested
                    + " предметов: перенос начинается хотя бы с одного, иначе дух идёт к сундуку зря");
        }
        if (minFreePercent < 0 || minFreePercent > 100) {
            throw new IllegalArgumentException("Минимум свободного места " + minFreePercent
                    + "% вне диапазона [0, 100]: доля больше 100% недостижима");
        }
        List<Candidate> pool = new ArrayList<>();
        for (int index = 0; index < containers.size(); index++) {
            pool.add(new Candidate(index, containers.get(index)));
        }
        List<Step> steps = new ArrayList<>();
        int left = requested;
        String refusal = null;
        while (left > 0 && !pool.isEmpty()) {
            List<ContainerSnapshot> snapshots = new ArrayList<>(pool.size());
            for (Candidate candidate : pool) {
                snapshots.add(candidate.snapshot());
            }
            ContainerChoice choice = ContainerPicker.choose(snapshots, origin, incoming, minFreePercent);
            if (choice instanceof ContainerChoice.Refused refused) {
                refusal = refused.reason();
                break;
            }
            int index = ((ContainerChoice.Chosen) choice).index();
            Candidate candidate = pool.get(index);
            int room = capacity(candidate.snapshot(), incoming);
            if (room <= 0) {
                pool.remove(index);
                continue;
            }
            int take = Math.min(left, room);
            steps.add(new Step(candidate.index(), take));
            left -= take;
            pool.remove(index);
        }
        return new Plan(steps, left, refusal);
    }

    private static int capacity(ContainerSnapshot snapshot, Stack incoming) {
        int room = 0;
        for (Stack stack : snapshot.slots()) {
            if (stack == null) {
                room += incoming.maxStack();
            } else if (stack.sameKindAs(incoming)) {
                room += stack.freeSpace();
            }
        }
        return room;
    }

    private record Candidate(int index, ContainerSnapshot snapshot) {}
}

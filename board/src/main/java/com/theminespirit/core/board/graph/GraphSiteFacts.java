package com.theminespirit.core.board.graph;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public interface GraphSiteFacts {
    record Lease(
            String id,
            String purpose,
            @Nullable String owner,
            GraphFacts.Position position,
            int height,
            boolean inside,
            boolean street,
            int cells,
            int distance,
            int depth,
            String key) {
        public Lease {
            Objects.requireNonNull(id, "id аренды не может быть null: по нему граф ставит откат");
            Objects.requireNonNull(purpose, "Назначение аренды не может быть null: по нему понятно, что убираем");
            Objects.requireNonNull(position, "Позиция аренды не может быть null: без неё не дойти до правки");
            Objects.requireNonNull(key, "Ключ отката не может быть null: по нему билет видит смену аренды");
            if (height < 0 || cells < 1 || distance < 0 || depth < 0) {
                throw new IllegalArgumentException("Аренда " + id + " с высотой " + height + ", клеток " + cells
                        + ", расстоянием " + distance + ", глубиной " + depth
                        + ": высота, расстояние и глубина не отрицательны, клетка одна и больше");
            }
        }

        public Lease(
                String id,
                String purpose,
                @Nullable String owner,
                GraphFacts.Position position,
                int height,
                boolean inside,
                boolean street,
                int cells,
                int distance,
                int depth) {
            this(
                    id,
                    purpose,
                    owner,
                    position,
                    height,
                    inside,
                    street,
                    cells,
                    distance,
                    depth,
                    position.x() + "," + position.y() + "," + position.z() + ":" + cells);
        }

        public Lease(
                String id,
                String purpose,
                @Nullable String owner,
                GraphFacts.Position position,
                int height,
                boolean inside,
                boolean street,
                int cells,
                int distance) {
            this(id, purpose, owner, position, height, inside, street, cells, distance, 0);
        }
    }

    record Stray(
            String id,
            String type,
            GraphFacts.Position position,
            @Nullable String builder,
            Map<String, Integer> blocks) {
        public Stray {
            Objects.requireNonNull(id, "id лишней постройки не может быть null: по нему её сносят");
            Objects.requireNonNull(type, "тип лишней постройки не может быть null: без типа не выбрать снос");
            Objects.requireNonNull(position, "позиция лишней постройки не может быть null: без неё не дойти");
            blocks = Map.copyOf(Objects.requireNonNull(blocks, "Состав лишней постройки не может быть null"));
        }

        public Stray(String id, String type, GraphFacts.Position position, @Nullable String builder) {
            this(id, type, position, builder, Map.of(type, 1));
        }
    }

    public record Terrain(
            int pits,
            int hanging,
            int heightDrop,
            int deepestPit,
            int traces,
            Map<String, Integer> cut,
            Map<String, Integer> fill,
            Map<String, Integer> floating,
            boolean unknown,
            int natural) {
        public Terrain {
            if (pits < 0 || hanging < 0 || heightDrop < 0 || deepestPit < 0 || traces < 0 || natural < 0) {
                throw new IllegalArgumentException("Ямы, висящее, перепад, глубина, следы и природное не бывают "
                        + "отрицательными: отсутствие — ноль");
            }
            cut = Map.copyOf(Objects.requireNonNull(cut, "Срезка лота не может быть null"));
            fill = Map.copyOf(Objects.requireNonNull(fill, "Подсыпка лота не может быть null"));
            floating = Map.copyOf(Objects.requireNonNull(floating, "Состав висящего не может быть null"));
        }

        public Terrain(
                int pits,
                int hanging,
                int heightDrop,
                int deepestPit,
                int traces,
                Map<String, Integer> cut,
                Map<String, Integer> fill,
                Map<String, Integer> floating,
                boolean unknown) {
            this(pits, hanging, heightDrop, deepestPit, traces, cut, fill, floating, unknown, 0);
        }

        public Terrain(int pits, int hanging, int heightDrop, int deepestPit, int traces) {
            this(pits, hanging, heightDrop, deepestPit, traces, Map.of(), Map.of(), Map.of(), false, 0);
        }

        public Terrain(
                int pits,
                int hanging,
                int heightDrop,
                int deepestPit,
                int traces,
                Map<String, Integer> cut,
                Map<String, Integer> fill,
                Map<String, Integer> floating) {
            this(pits, hanging, heightDrop, deepestPit, traces, cut, fill, floating, false);
        }

        public static Terrain empty() {
            return new Terrain(0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), false);
        }

        public boolean flat() {
            return pits == 0 && hanging == 0 && heightDrop == 0 && traces == 0;
        }
    }

    public record StageReqs(Map<String, Integer> minimum, Map<String, Integer> actual) {
        public StageReqs {
            minimum = Map.copyOf(
                    Objects.requireNonNull(minimum, "Минимум этапа не может быть null: без требований — пустая карта"));
            actual = Map.copyOf(
                    Objects.requireNonNull(actual, "Факт этапа не может быть null: без построек — пустая карта"));
        }
    }
}

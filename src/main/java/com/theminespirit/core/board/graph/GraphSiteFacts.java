package com.theminespirit.core.board.graph;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public interface GraphSiteFacts {
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
            Map<String, Integer> floating) {
        public Terrain {
            if (pits < 0 || hanging < 0 || heightDrop < 0 || deepestPit < 0 || traces < 0) {
                throw new IllegalArgumentException(
                        "Ямы, висящее, перепад, глубина и следы не бывают отрицательными: " + "отсутствие — ноль");
            }
            cut = Map.copyOf(Objects.requireNonNull(cut, "Срезка лота не может быть null"));
            fill = Map.copyOf(Objects.requireNonNull(fill, "Подсыпка лота не может быть null"));
            floating = Map.copyOf(Objects.requireNonNull(floating, "Состав висящего не может быть null"));
        }

        public Terrain(int pits, int hanging, int heightDrop, int deepestPit, int traces) {
            this(pits, hanging, heightDrop, deepestPit, traces, Map.of(), Map.of(), Map.of());
        }

        public static Terrain empty() {
            return new Terrain(0, 0, 0, 0, 0);
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

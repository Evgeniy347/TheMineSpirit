package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public sealed interface MemoryCall {
    String script();

    MemoryOutcome apply(MemoryStore store, String dimension);

    String describe(Finding finding);

    static Sighting sightingOf(MemoryPoint point) {
        return new Sighting(point.type().id(), point.name(), point.pos(), point.dimension());
    }

    static String line(Sighting sighting) {
        BlockCoord pos = sighting.pos();
        return sighting.label() + ": " + sighting.kind() + " (" + pos.x() + ", " + pos.y() + ", " + pos.z() + ") в "
                + sighting.detail();
    }

    record Remember(String name, MemoryType type, BlockCoord pos) implements MemoryCall {
        public Remember {
            Objects.requireNonNull(name, "name не может быть null: точку запоминают под именем");
            Objects.requireNonNull(type, "type не может быть null: точка запоминается с типом");
            Objects.requireNonNull(pos, "pos не может быть null: точка запоминается с позицией");
        }

        @Override
        public String script() {
            return "remember";
        }

        @Override
        public MemoryOutcome apply(MemoryStore store, String dimension) {
            MemoryPoint point = new MemoryPoint(name, type, dimension, pos);
            return new MemoryOutcome(store.remember(point), new Finding(script(), List.of(sightingOf(point))));
        }

        @Override
        public String describe(Finding finding) {
            return "Запомнена точка " + line(finding.sightings().get(0));
        }
    }

    record Recall(String name) implements MemoryCall {
        public Recall {
            Objects.requireNonNull(name, "name не может быть null: точку вспоминают по имени");
        }

        @Override
        public String script() {
            return "recall";
        }

        @Override
        public MemoryOutcome apply(MemoryStore store, String dimension) {
            MemoryPoint point = store.recall(name);
            List<Sighting> sightings = point == null ? List.of() : List.of(sightingOf(point));
            return new MemoryOutcome(store, new Finding(script(), sightings));
        }

        @Override
        public String describe(Finding finding) {
            return finding.isEmpty()
                    ? "Точки '" + name + "' в памяти нет"
                    : line(finding.sightings().get(0));
        }
    }

    record RecallAll(MemoryType type) implements MemoryCall {
        public RecallAll {
            Objects.requireNonNull(type, "type не может быть null: точки вспоминают по типу");
        }

        @Override
        public String script() {
            return "recall_all";
        }

        @Override
        public MemoryOutcome apply(MemoryStore store, String dimension) {
            List<Sighting> sightings =
                    store.recallAll(type).stream().map(MemoryCall::sightingOf).toList();
            return new MemoryOutcome(store, new Finding(script(), sightings));
        }

        @Override
        public String describe(Finding finding) {
            if (finding.isEmpty()) {
                return "Точек типа " + type.id() + " в памяти нет";
            }
            StringBuilder text = new StringBuilder(
                    "Точек типа " + type.id() + ": " + finding.sightings().size());
            finding.sightings().forEach(sighting -> text.append('\n').append(line(sighting)));
            return text.toString();
        }
    }

    record Forget(String name) implements MemoryCall {
        public Forget {
            Objects.requireNonNull(name, "name не может быть null: точку забывают по имени");
        }

        @Override
        public String script() {
            return "forget";
        }

        @Override
        public MemoryOutcome apply(MemoryStore store, String dimension) {
            MemoryPoint forgotten = store.recall(name);
            List<Sighting> sightings = forgotten == null ? List.of() : List.of(sightingOf(forgotten));
            return new MemoryOutcome(store.forget(name), new Finding(script(), sightings));
        }

        @Override
        public String describe(Finding finding) {
            return finding.isEmpty()
                    ? "Точки '" + name + "' в памяти нет, забывать нечего"
                    : "Забыта точка " + line(finding.sightings().get(0));
        }
    }
}

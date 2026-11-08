package com.theminespirit.core.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record SettlementTree(
        int stage, int stages, int currentStage, List<Node> nodes, List<Edge> edges, int width, int height) {
    public enum Kind {
        ROOT,
        BUILDING,
        UPGRADE,
        GATE
    }

    public enum State {
        NOT_STARTED,
        IN_PROGRESS,
        READY,
        LOCKED,
        UNKNOWN
    }

    public record Position(int x, int y, int z) {}

    public record Point(int x, int y) {}

    public record Node(
            String id,
            Kind kind,
            State state,
            String title,
            String icon,
            int level,
            int percent,
            boolean optional,
            boolean blocking,
            int x,
            int y,
            @Nullable Position at,
            Map<String, Integer> missing,
            List<String> requirements) {
        public Node {
            Objects.requireNonNull(id, "id узла древа не может быть null: по нему рисуют связи");
            Objects.requireNonNull(kind, "вид узла древа не может быть null: от него зависит цвет рамки");
            Objects.requireNonNull(state, "состояние узла не может быть null: неизвестное — State.UNKNOWN");
            Objects.requireNonNull(title, "название узла не может быть null: его показывает подсказка");
            Objects.requireNonNull(icon, "иконка узла не может быть null: без неё рамка пустая");
            if (id.isBlank() || title.isBlank() || icon.isBlank()) {
                throw new IllegalArgumentException(
                        "Узел древа '" + id + "' пуст: id, название и иконка задаются непусто");
            }
            if (level < 0) {
                throw new IllegalArgumentException(
                        "Уровень узла '" + id + "' = " + level + ": уровни начинаются с первого, у корня ноль");
            }
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException(
                        "Готовность узла '" + id + "' = " + percent + "%: процент постройки лежит от 0 до 100");
            }
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException(
                        "Место узла '" + id + "' = " + x + "," + y + ": раскладка древа не бывает отрицательной");
            }
            missing = Map.copyOf(
                    Objects.requireNonNull(missing, "Недостача узла не может быть null: пустую задают пустой картой"));
            requirements = List.copyOf(Objects.requireNonNull(
                    requirements, "Требования узла не могут быть null: без них задают пустой список"));
        }
    }

    public record Edge(String from, String to, List<Point> points) {
        public Edge {
            Objects.requireNonNull(from, "Начало связи не может быть null: без него линия повиснет");
            Objects.requireNonNull(to, "Конец связи не может быть null: без него линия повиснет");
            points = List.copyOf(
                    Objects.requireNonNull(points, "Точки связи не могут быть null: прямую задают двумя точками"));
            if (points.size() < 2) {
                throw new IllegalArgumentException("Связь " + from + " → " + to + " несёт " + points.size()
                        + " точек: линия рисуется хотя бы по двум");
            }
        }
    }

    public SettlementTree {
        if (stages < 1) {
            throw new IllegalArgumentException("Этапов " + stages + ": поселение начинается с первого");
        }
        if (stage < 1 || stage > stages) {
            throw new IllegalArgumentException(
                    "Вкладка этапа " + stage + " вне 1.." + stages + ": у каждого этапа своя вкладка");
        }
        if (currentStage < 1 || currentStage > stages + 1) {
            throw new IllegalArgumentException("Текущий этап " + currentStage + " вне 1.." + (stages + 1)
                    + ": «все закрыты» — это " + (stages + 1));
        }
        nodes = List.copyOf(
                Objects.requireNonNull(nodes, "Узлы древа не могут быть null: пустое древо — пустой список"));
        edges = List.copyOf(
                Objects.requireNonNull(edges, "Связи древа не могут быть null: пустое древо — пустой список"));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Древо без корня: вкладка этапа обязана начинаться с корневой рамки");
        }
        Set<String> ids = new HashSet<>();
        for (Node node : nodes) {
            if (!ids.add(node.id())) {
                throw new IllegalArgumentException(
                        "Узел '" + node.id() + "' объявлен дважды: связи и подсказки берут его наугад");
            }
        }
        for (Edge edge : edges) {
            if (!ids.contains(edge.from()) || !ids.contains(edge.to())) {
                throw new IllegalArgumentException("Связь " + edge.from() + " → " + edge.to()
                        + " ведёт в несуществующий узел: линии рисуются только между рамками древа");
            }
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Размер древа " + width + "×" + height + ": стороны неотрицательны");
        }
    }

    public Optional<Node> node(String id) {
        Objects.requireNonNull(id, "id узла не может быть null: по нему ищут рамку");
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst();
    }
}

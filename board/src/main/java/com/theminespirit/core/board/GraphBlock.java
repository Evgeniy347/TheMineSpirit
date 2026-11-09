package com.theminespirit.core.board;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;

public record GraphBlock(String node, String target, Need need) {
    public GraphBlock {
        Objects.requireNonNull(node, "Узел блокировки не может быть null: блокировка адресная");
        Objects.requireNonNull(target, "Цель блокировки не может быть null: неизвестную задают пустой строкой");
        Objects.requireNonNull(need, "Нужда блокировки не может быть null: без неё блокировка не снимется");
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Item.class, name = "item"),
        @JsonSubTypes.Type(value = Tool.class, name = "tool"),
        @JsonSubTypes.Type(value = Building.class, name = "building"),
        @JsonSubTypes.Type(value = Path.class, name = "path"),
        @JsonSubTypes.Type(value = Holder.class, name = "holder"),
        @JsonSubTypes.Type(value = Impossible.class, name = "impossible")
    })
    public sealed interface Need permits Item, Tool, Building, Path, Holder, Impossible {}

    public record Item(String item, int count) implements Need {
        public Item {
            Objects.requireNonNull(item, "Предмет нужды не может быть null: нехватка адресная");
            if (count < 1) {
                throw new IllegalArgumentException("Нужда в предмете '" + item + "' равна " + count
                        + ": блокировка ставится на недостающее количество, а не на ноль");
            }
        }
    }

    public record Tool(String kind, int rank, String spirit) implements Need {
        public Tool {
            Objects.requireNonNull(kind, "Вид инструмента не может быть null: нужда адресная");
            Objects.requireNonNull(spirit, "Дух нужды не может быть null: нужен любому — пустая строка");
            if (rank < -1) {
                throw new IllegalArgumentException(
                        "Тир инструмента " + kind + " " + rank + " меньше -1: отсутствие инструмента задают -1");
            }
        }
    }

    public record Building(String id) implements Need {
        public Building {
            Objects.requireNonNull(id, "Постройка нужды не может быть null: нужда адресная");
        }
    }

    public record Path(String point) implements Need {
        public Path {
            Objects.requireNonNull(point, "Точка нужды не может быть null: нужда адресная");
        }
    }

    public record Holder(String spirit, boolean stuck) implements Need {
        public Holder {
            Objects.requireNonNull(spirit, "Держатель нужды не может быть null: нужда адресная");
        }
    }

    public record Impossible(String node) implements Need {
        public Impossible {
            Objects.requireNonNull(node, "Невозможный узел не может быть null: notice называет его");
        }
    }
}

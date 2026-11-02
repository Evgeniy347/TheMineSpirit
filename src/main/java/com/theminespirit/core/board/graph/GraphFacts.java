package com.theminespirit.core.board.graph;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record GraphFacts(
        Map<String, Spirit> spirits,
        List<Threat> threats,
        List<Chest> chests,
        List<Resource> resources,
        Map<String, Station> stations,
        Map<String, Building> buildings,
        List<Stray> strays,
        Terrain terrain,
        StageReqs stageReqs) {
    public enum Reach {
        UNKNOWN,
        PATH,
        NO_PATH
    }

    public record Position(int x, int y, int z) {}

    public record Spirit(
            String id,
            Position position,
            float health,
            int food,
            int air,
            boolean inWater,
            boolean trapped,
            boolean stuck,
            @Nullable String activity,
            Map<String, Integer> inventory,
            List<Armor> armor,
            List<Tool> tools) {
        public Spirit {
            Objects.requireNonNull(id, "id духа не может быть null: факт принадлежит конкретному духу");
            Objects.requireNonNull(position, "позиция духа не может быть null: без неё не выбрать работу");
            inventory = Map.copyOf(
                    Objects.requireNonNull(inventory, "инвентарь духа не может быть null: пустой — пустая карта"));
            armor = List.copyOf(
                    Objects.requireNonNull(armor, "броня духа не может быть null: без брони — пустой список"));
            tools = List.copyOf(
                    Objects.requireNonNull(tools, "инструменты духа не могут быть null: без них — пустой список"));
        }
    }

    public record Armor(String slot, String item, int durability, int maxDurability) {
        public Armor {
            Objects.requireNonNull(slot, "слот брони не может быть null: броня надевается в конкретный слот");
            Objects.requireNonNull(item, "предмет брони не может быть null: прочность считается по предмету");
        }
    }

    public record Tool(String item, @Nullable String kind, int tier, int durability, int maxDurability) {
        public Tool {
            Objects.requireNonNull(item, "предмет инструмента не может быть null: прочность считается по предмету");
        }
    }

    public record Threat(
            String id,
            String type,
            float health,
            Position position,
            @Nullable String target) {
        public Threat {
            Objects.requireNonNull(id, "id угрозы не может быть null: по нему отличают врагов");
            Objects.requireNonNull(type, "тип угрозы не может быть null: без типа не выбрать защиту");
            Objects.requireNonNull(position, "позиция угрозы не может быть null: без неё не дойти до врага");
        }
    }

    public record Chest(Position position, Map<String, Integer> contents, int freeSlots, int totalSlots) {
        public Chest {
            Objects.requireNonNull(position, "позиция сундука не может быть null: без неё не дойти до склада");
            contents = Map.copyOf(
                    Objects.requireNonNull(contents, "содержимое сундука не может быть null: пустой — пустая карта"));
            if (freeSlots < 0 || totalSlots < 0 || freeSlots > totalSlots) {
                throw new IllegalArgumentException("Свободных слотов " + freeSlots + " из " + totalSlots
                        + ": число слотов не отрицательно и не больше общего");
            }
        }
    }

    public record Resource(String tag, Position position, int distance, Reach reach, int travelTicks) {
        public Resource {
            Objects.requireNonNull(tag, "тег ресурса не может быть null: по нему ищется путь");
            Objects.requireNonNull(position, "позиция ресурса не может быть null: без неё не дойти до ресурса");
            Objects.requireNonNull(reach, "достижимость ресурса не может быть null: неизвестное — UNKNOWN");
            if (distance < 0 || travelTicks < 0) {
                throw new IllegalArgumentException("Расстояние " + distance + " и дорога " + travelTicks
                        + " не бывают отрицательными: неизвестное задают UNKNOWN");
            }
        }
    }

    public record Station(
            String id,
            String type,
            @Nullable Position position,
            boolean busy,
            @Nullable String fuel,
            @Nullable String input,
            @Nullable String output) {
        public Station {
            Objects.requireNonNull(id, "id станции не может быть null: по нему учитывают очередь");
            Objects.requireNonNull(type, "тип станции не может быть null: без типа не выбрать задание");
        }
    }

    public record Building(
            String id,
            int level,
            int auditPercent,
            int damage,
            @Nullable Position position,
            boolean unknown) {
        public Building {
            Objects.requireNonNull(id, "id постройки не может быть null: по нему сверяют план");
            if (level < 0 || damage < 0) {
                throw new IllegalArgumentException("Уровень " + level + " и повреждения " + damage
                        + " не бывают отрицательными: отсутствие — ноль");
            }
            if (auditPercent < 0 || auditPercent > 100) {
                throw new IllegalArgumentException(
                        "Процент аудита " + auditPercent + " вне [0, 100]: неизвестное отмечают флагом unknown");
            }
        }
    }

    public record Stray(
            String id,
            String type,
            Position position,
            @Nullable String builder) {
        public Stray {
            Objects.requireNonNull(id, "id лишней постройки не может быть null: по нему её сносят");
            Objects.requireNonNull(type, "тип лишней постройки не может быть null: без типа не выбрать снос");
            Objects.requireNonNull(position, "позиция лишней постройки не может быть null: без неё не дойти");
        }
    }

    public record Terrain(int pits, int hanging, int heightDrop, int deepestPit, int traces) {
        public Terrain {
            if (pits < 0 || hanging < 0 || heightDrop < 0) {
                throw new IllegalArgumentException("Ямы " + pits + ", висящее " + hanging + ", перепад " + heightDrop
                        + " не бывают отрицательными: отсутствие — ноль");
            }
            if (deepestPit < 0 || traces < 0) {
                throw new IllegalArgumentException("Глубина ямы " + deepestPit + " и следы " + traces
                        + " не бывают отрицательными: отсутствие — ноль");
            }
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

    public GraphFacts {
        spirits = Map.copyOf(Objects.requireNonNull(spirits, "Духи не могут быть null: без духов — пустая карта"));
        threats = List.copyOf(Objects.requireNonNull(threats, "Угрозы не могут быть null: без угроз — пустой список"));
        chests =
                List.copyOf(Objects.requireNonNull(chests, "Сундуки не могут быть null: без сундуков — пустой список"));
        resources = List.copyOf(
                Objects.requireNonNull(resources, "Ресурсы не могут быть null: без ресурсов — пустой список"));
        stations =
                Map.copyOf(Objects.requireNonNull(stations, "Станции не могут быть null: без станций — пустая карта"));
        buildings = Map.copyOf(
                Objects.requireNonNull(buildings, "Постройки не могут быть null: без построек — пустая карта"));
        strays = List.copyOf(
                Objects.requireNonNull(strays, "Лишние постройки не могут быть null: без них — пустой список"));
        Objects.requireNonNull(terrain, "Рельеф не может быть null: ровный задают Terrain.empty()");
        Objects.requireNonNull(stageReqs, "Требования этапа не могут быть null: без требований — пустая карта");
    }

    public record World(List<Stray> strays, Terrain terrain, Map<String, Building> buildings) {
        public World {
            strays = List.copyOf(
                    Objects.requireNonNull(strays, "Лишние постройки не могут быть null: без них — пустой список"));
            Objects.requireNonNull(terrain, "Рельеф не может быть null: ровный задают Terrain.empty()");
            buildings = Map.copyOf(
                    Objects.requireNonNull(buildings, "Постройки не могут быть null: без них — пустая карта"));
        }
    }

    public static GraphFacts empty() {
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                Terrain.empty(),
                new StageReqs(Map.of(), Map.of()));
    }

    public GraphFacts withChests(List<Chest> known) {
        return new GraphFacts(spirits, threats, known, resources, stations, buildings, strays, terrain, stageReqs);
    }

    public GraphFacts withWorld(World world) {
        Objects.requireNonNull(world, "Факты участка не могут быть null: пустые задают пустым World");
        return new GraphFacts(
                spirits,
                threats,
                chests,
                resources,
                stations,
                world.buildings(),
                world.strays(),
                world.terrain(),
                stageReqs);
    }
}

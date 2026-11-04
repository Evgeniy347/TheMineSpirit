package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.GraphSiteFacts.StageReqs;
import com.theminespirit.core.board.graph.GraphSiteFacts.Stray;
import com.theminespirit.core.board.graph.GraphSiteFacts.Terrain;
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
        StageReqs stageReqs,
        GraphFeedback feedback) {
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
            List<Tool> tools,
            @Nullable String trap) {
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

        public Spirit(
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
            this(id, position, health, food, air, inWater, trapped, stuck, activity, inventory, armor, tools, null);
        }

        public Spirit withTrap(String cause) {
            return new Spirit(
                    id, position, health, food, air, inWater, true, stuck, activity, inventory, armor, tools, cause);
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

    public enum TargetKind {
        NONE,
        SPIRIT,
        PLAYER,
        ANIMAL,
        OTHER
    }

    public record Threat(
            String id,
            String type,
            float health,
            float maxHealth,
            Position position,
            @Nullable String target,
            TargetKind targetKind,
            boolean targetingSelf,
            boolean visible,
            @Nullable String state,
            int distance,
            @Nullable String lastHurtBy,
            double speed) {
        public Threat {
            Objects.requireNonNull(id, "id угрозы не может быть null: по нему отличают врагов");
            Objects.requireNonNull(type, "тип угрозы не может быть null: без типа не выбрать защиту");
            Objects.requireNonNull(position, "позиция угрозы не может быть null: без неё не дойти до врага");
            Objects.requireNonNull(targetKind, "вид цели угрозы не может быть null: без него не выбрать защиту");
            if (health < 0 || maxHealth <= 0 || health > maxHealth) {
                throw new IllegalArgumentException("Здоровье угрозы " + health + " из " + maxHealth
                        + ": оно не отрицательно и не больше максимума, максимум положителен");
            }
            if (distance < 0) {
                throw new IllegalArgumentException("Расстояние до угрозы " + distance
                        + " отрицательно: неизвестное отмечают отдельно, а не минусом");
            }
            if (speed < 0) {
                throw new IllegalArgumentException("Скорость угрозы " + speed + " отрицательна: стоящую задают нулём");
            }
        }

        public Threat(String id, String type, float health, Position position, @Nullable String target) {
            this(
                    id,
                    type,
                    health,
                    Math.max(health, 1f),
                    position,
                    target,
                    target == null ? TargetKind.NONE : TargetKind.OTHER,
                    false,
                    true,
                    null,
                    0,
                    null,
                    0);
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
            Map<String, Integer> missing,
            Map<String, Integer> extra,
            int wrong,
            @Nullable Position position,
            boolean unknown) {
        public Building {
            Objects.requireNonNull(id, "id постройки не может быть null: по нему сверяют план");
            missing = Map.copyOf(
                    Objects.requireNonNull(missing, "Недостача постройки не может быть null: пустая — пустая карта"));
            extra = Map.copyOf(
                    Objects.requireNonNull(extra, "Лишнее постройки не может быть null: пустое — пустая карта"));
            if (level < 0 || damage < 0 || wrong < 0 || auditPercent < 0 || auditPercent > 100) {
                throw new IllegalArgumentException("Уровень, процент, повреждения и чужие блоки вне границ: "
                        + "отсутствие — ноль, процент 0..100, неизвестное отмечают флагом unknown");
            }
        }

        public Building(String id, int level, int auditPercent, int damage, @Nullable Position at, boolean unknown) {
            this(id, level, auditPercent, damage, Map.of(), Map.of(), 0, at, unknown);
        }

        public int missingCount() {
            return missing.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int extraCount() {
            return extra.values().stream().mapToInt(Integer::intValue).sum();
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
        Objects.requireNonNull(feedback, "Обратная связь мира не может быть null: без неё — feedback.none()");
    }

    public GraphFacts(
            Map<String, Spirit> spirits,
            List<Threat> threats,
            List<Chest> chests,
            List<Resource> resources,
            Map<String, Station> stations,
            Map<String, Building> buildings,
            List<Stray> strays,
            Terrain terrain,
            StageReqs stageReqs) {
        this(
                spirits,
                threats,
                chests,
                resources,
                stations,
                buildings,
                strays,
                terrain,
                stageReqs,
                GraphFeedback.none());
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
                new StageReqs(Map.of(), Map.of()),
                GraphFeedback.none());
    }

    public GraphFacts withChests(List<Chest> known) {
        return new GraphFacts(
                spirits, threats, known, resources, stations, buildings, strays, terrain, stageReqs, feedback);
    }

    public GraphFacts withThreats(List<Threat> known) {
        return new GraphFacts(
                spirits, known, chests, resources, stations, buildings, strays, terrain, stageReqs, feedback);
    }

    public GraphFacts withSpirits(Map<String, Spirit> known) {
        return new GraphFacts(
                known, threats, chests, resources, stations, buildings, strays, terrain, stageReqs, feedback);
    }

    public GraphFacts withFeedback(GraphFeedback known) {
        return new GraphFacts(
                spirits, threats, chests, resources, stations, buildings, strays, terrain, stageReqs, known);
    }

    public GraphFacts withWorld(GraphWorldFacts world) {
        return new GraphFacts(
                spirits,
                threats,
                chests,
                resources,
                stations,
                world.buildings(),
                world.strays(),
                world.terrain(),
                stageReqs,
                feedback);
    }
}

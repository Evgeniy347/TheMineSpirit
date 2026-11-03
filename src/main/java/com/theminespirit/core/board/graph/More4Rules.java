package com.theminespirit.core.board.graph;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class More4Rules {
    public static final int HUNGRY_FOOD = 6;
    public static final float INJURED_HEALTH = 10.0F;
    public static final int SPACE_MIN = 8;
    public static final String RARE = "#rare";

    public static final String PLAYER_NEAR = "more4_player_near";
    public static final String PLAYER_HUNGRY = "more4_player_hungry";
    public static final String PLAYER_TOOL_BROKEN = "more4_player_tool_broken";
    public static final String WRONG_TOOL_HAND = "more4_wrong_tool_hand";
    public static final String PORTAL_FOUND = "more4_portal_found";
    public static final String PORTAL_UNSAFE = "more4_portal_unsafe";
    public static final String SPACE_FOUND = "more4_space_found";
    public static final String SPACE_TIGHT = "more4_space_tight";
    public static final String TAMED_PET = "more4_tamed_pet";
    public static final String RARE_FOUND = "more4_rare_found";
    public static final String RARE_MIXED = "more4_rare_mixed";
    public static final String CAUTION = "more4_caution";

    private static final String PICKAXE = "pickaxe";
    private static final String AXE = "axe";
    private static final String HOE = "hoe";

    public record Players(int near, int hungry, int brokenTools) {
        public Players {
            for (Map.Entry<String, Integer> count : Map.of(
                            "игроков рядом", near, "голодных игроков", hungry, "сломанных инструментов", brokenTools)
                    .entrySet()) {
                if (count.getValue() < 0) {
                    throw new IllegalArgumentException("Число " + count.getKey() + " = " + count.getValue()
                            + " отрицательно: отсутствие задают нулём");
                }
            }
            if (hungry > near || brokenTools > near) {
                throw new IllegalArgumentException("Голодных " + hungry + " и сломанных инструментов " + brokenTools
                        + " при " + near + " игроках рядом: состояние игрока считают только у присутствующего");
            }
        }

        public boolean present() {
            return near > 0;
        }
    }

    public record Portal(boolean found, boolean lit, boolean enclosed, boolean hazard) {
        public boolean safe() {
            return found && lit && enclosed && !hazard;
        }

        public boolean unsafe() {
            return found && !safe();
        }
    }

    public record Hand(
            @Nullable String requiredKind, @Nullable String heldKind, boolean ownsRequired) {
        public boolean wrong() {
            return requiredKind != null && ownsRequired && !requiredKind.equals(heldKind);
        }
    }

    public record Lots(boolean known, int free) {
        public Lots {
            if (free < 0) {
                throw new IllegalArgumentException(
                        "Свободных мест " + free + " не бывает меньше нуля: неизвестное отмечают флагом known");
            }
        }

        public boolean hasRoom() {
            return known && free >= SPACE_MIN;
        }

        public boolean tight() {
            return known && free < SPACE_MIN;
        }
    }

    public record Pets(int tamed, int untamed) {
        public Pets {
            if (tamed < 0 || untamed < 0) {
                throw new IllegalArgumentException(
                        "Питомцев " + tamed + "/" + untamed + " не бывает меньше нуля: никого — ноль");
            }
        }

        public boolean hasTamed() {
            return tamed > 0;
        }
    }

    public record Rare(int carried, int mixed) {
        public Rare {
            if (carried < 0 || mixed < 0) {
                throw new IllegalArgumentException(
                        "Редких предметов " + carried + "/" + mixed + " не бывает меньше нуля: нет — ноль");
            }
        }

        public boolean atRisk() {
            return carried > 0 || mixed > 0;
        }
    }

    public record Settlement(int injured, int hungry) {
        public Settlement {
            if (injured < 0 || hungry < 0) {
                throw new IllegalArgumentException(
                        "Раненых " + injured + " и голодных " + hungry + " не бывает меньше нуля: нет — ноль");
            }
        }

        public boolean cautious() {
            return injured > 0 || hungry > 0;
        }
    }

    public record Signals(
            Players players, Portal portal, int wrongHands, Lots lots, Pets pets, Rare rare, Settlement settlement) {
        public Signals {
            Objects.requireNonNull(players, "Игроки не могут быть null: никого — Players(0, 0, 0)");
            Objects.requireNonNull(portal, "Портал не может быть null: нет — Portal(false, false, false, false)");
            Objects.requireNonNull(lots, "Места под постройки не могут быть null: неизвестно — Lots(false, 0)");
            Objects.requireNonNull(pets, "Питомцы не могут быть null: никого — Pets(0, 0)");
            Objects.requireNonNull(rare, "Редкие предметы не могут быть null: нет — Rare(0, 0)");
            Objects.requireNonNull(settlement, "Состояние поселения не может быть null: здорово — Settlement(0, 0)");
            if (wrongHands < 0) {
                throw new IllegalArgumentException(
                        "Духов с неверным инструментом " + wrongHands + " не бывает меньше нуля: нет — ноль");
            }
        }
    }

    private More4Rules() {}

    public static Signals empty() {
        return new Signals(
                new Players(0, 0, 0),
                new Portal(false, false, false, false),
                0,
                new Lots(false, 0),
                new Pets(0, 0),
                new Rare(0, 0),
                new Settlement(0, 0));
    }

    public static Set<String> events(Signals signals) {
        Set<String> events = new LinkedHashSet<>();
        add(events, signals.players().present(), PLAYER_NEAR);
        add(events, signals.players().hungry() > 0, PLAYER_HUNGRY);
        add(events, signals.players().brokenTools() > 0, PLAYER_TOOL_BROKEN);
        add(events, signals.wrongHands() > 0, WRONG_TOOL_HAND);
        add(events, signals.portal().found(), PORTAL_FOUND);
        add(events, signals.portal().unsafe(), PORTAL_UNSAFE);
        add(events, signals.lots().hasRoom(), SPACE_FOUND);
        add(events, signals.lots().tight(), SPACE_TIGHT);
        add(events, signals.pets().hasTamed(), TAMED_PET);
        add(events, signals.rare().atRisk(), RARE_FOUND);
        add(events, signals.rare().mixed() > 0, RARE_MIXED);
        add(events, signals.settlement().cautious(), CAUTION);
        return Set.copyOf(events);
    }

    public static int wrongHands(Collection<Hand> hands) {
        Objects.requireNonNull(hands, "Руки духов не могут быть null: без духов — пустой список");
        return (int) hands.stream().filter(Hand::wrong).count();
    }

    public static int rare(SupplyGraph graph, Map<String, Integer> items) {
        Objects.requireNonNull(graph, "Граф не может быть null: группу редких предметов берут из него");
        Objects.requireNonNull(items, "Предметы не могут быть null: пустой запас — пустая карта");
        Set<String> known = new HashSet<>(graph.members(RARE));
        return items.entrySet().stream()
                .filter(entry -> known.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public static int rareMixed(SupplyGraph graph, List<GraphFacts.Chest> chests) {
        Objects.requireNonNull(graph, "Граф не может быть null: группу редких предметов берут из него");
        Objects.requireNonNull(chests, "Сундуки не могут быть null: без сундуков — пустой список");
        Set<String> known = new HashSet<>(graph.members(RARE));
        int mixed = 0;
        for (GraphFacts.Chest chest : chests) {
            boolean rare = false;
            boolean other = false;
            for (String item : chest.contents().keySet()) {
                if (known.contains(item)) {
                    rare = true;
                } else {
                    other = true;
                }
            }
            if (rare && other) {
                mixed++;
            }
        }
        return mixed;
    }

    public static @Nullable String requiredKind(@Nullable String activity) {
        if (activity == null) {
            return null;
        }
        return switch (activity) {
            case "mining" -> PICKAXE;
            case "woodcutting" -> AXE;
            case "farming" -> HOE;
            default -> null;
        };
    }

    private static void add(Set<String> events, boolean present, String name) {
        if (present) {
            events.add(name);
        }
    }
}

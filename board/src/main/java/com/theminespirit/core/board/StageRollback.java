package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class StageRollback {
    public static final String CAUSE = "откат этапа";
    private static final String GRAPH_BUILDING = "graph:building:";
    private static final Map<String, String> ALIASES = Map.of("storage", "warehouse", "pen", "pen_cow");

    public enum Readiness {
        READY,
        NOT_READY,
        UNKNOWN
    }

    @FunctionalInterface
    public interface World {
        Readiness ready(@Nullable BoardCase boardCase, @Nullable String planId, int level);
    }

    public record Mark(
            String key,
            @Nullable BoardCase boardCase,
            @Nullable String planId,
            int level,
            int stage) {
        public Mark {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "Ключ отметки пуст: откат этапа снимает отметки журнала по их ключам");
            }
            if ((boardCase == null) == (planId == null)) {
                throw new IllegalArgumentException("Отметка '" + key
                        + "' обязана называть либо кейс доски, либо постройку плана: иначе неизвестно, что снимать");
            }
            if (level < 1) {
                throw new IllegalArgumentException(
                        "Уровень отметки '" + key + "' = " + level + ": отметки журнала начинаются с первого");
            }
            if (stage < 0) {
                throw new IllegalArgumentException("Этап отметки '" + key + "' = " + stage
                        + " отрицателен: ноль означает «вне этапов», а не «до первого»");
            }
        }
    }

    public record Plan(
            int previous,
            int target,
            List<Mark> forget,
            @Nullable String refusal) {
        public Plan {
            if (previous < 1 || target < 1) {
                throw new IllegalArgumentException("Откат " + previous + "→" + target + ": этапы начинаются с первого");
            }
            forget = List.copyOf(Objects.requireNonNull(
                    forget, "Список снимаемых отметок не может быть null: пустой откат задают пустым списком"));
        }

        public boolean refused() {
            return refusal != null;
        }
    }

    private StageRollback() {}

    public static StageView view(
            StageRules rules, Map<String, Integer> buildingStages, BoardLedger ledger, World world) {
        Objects.requireNonNull(rules, "Правила этапов не могут быть null: по ним считается этап поселения");
        Objects.requireNonNull(buildingStages, "Этапы построек не могут быть null: без них не понять, что выше этапа");
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по отметкам");
        Objects.requireNonNull(world, "Мир не может быть null: готовность построек проверяется по нему");
        int stage = rules.stageOf(ledger);
        int stages = rules.stages().size();
        List<StageView.Requirement> requirements = new ArrayList<>();
        int shown = Math.min(stage, stages);
        for (int number = 1; number <= shown; number++) {
            StageRules.Stage current = rules.stages().get(number - 1);
            int owner = current.stage();
            current.buildings().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> requirements.add(new StageView.Requirement(
                            owner,
                            entry.getKey().name(),
                            entry.getValue(),
                            ledger.has(entry.getKey(), entry.getValue()),
                            world.ready(entry.getKey(), null, entry.getValue()))));
            current.requires().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> requirements.add(new StageView.Requirement(
                            owner,
                            entry.getKey(),
                            entry.getValue(),
                            ledger.hasPlan(entry.getKey(), entry.getValue()),
                            world.ready(null, entry.getKey(), entry.getValue()))));
        }
        return new StageView(stage, stages, rules.missingOf(ledger), requirements);
    }

    public static Plan plan(
            int target, StageRules rules, Map<String, Integer> buildingStages, BoardLedger ledger, World world) {
        Objects.requireNonNull(rules, "Правила этапов не могут быть null: по ним проверяется откат");
        Objects.requireNonNull(buildingStages, "Этапы построек не могут быть null: без них не понять, что выше цели");
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: откат возвращает его отметки");
        Objects.requireNonNull(world, "Мир не может быть null: непроверенная постройка открывается заново");
        int stages = rules.stages().size();
        if (target < 1 || target > stages) {
            throw new IllegalArgumentException(
                    "Этап " + target + " вне 1.." + stages + ": этапов поселения всего " + stages);
        }
        int previous = rules.stageOf(ledger);
        if (target == previous) {
            return new Plan(previous, target, List.of(), null);
        }
        if (target > previous) {
            return new Plan(
                    previous,
                    target,
                    List.of(),
                    "Этап вперёд не ставится: он закрывается постройкой целиком, а этап " + previous + " не закрыт ("
                            + rules.missingOf(ledger) + ")");
        }
        List<Mark> forget = new ArrayList<>();
        for (Mark mark : marks(ledger.keys(), rules, buildingStages)) {
            if (mark.stage() > target) {
                forget.add(mark);
                continue;
            }
            if (mark.stage() == target
                    && world.ready(mark.boardCase(), mark.planId(), mark.level()) != Readiness.READY) {
                forget.add(mark);
            }
        }
        Set<String> keys = new LinkedHashSet<>(ledger.keys());
        forget.forEach(mark -> keys.remove(mark.key()));
        BoardLedger after = new BoardLedger();
        after.restore(keys);
        int result = rules.stageOf(after);
        if (result != target) {
            return new Plan(
                    previous,
                    target,
                    List.of(),
                    "Этап " + target + " закрыт по миру целиком: откатывать нечего, после снятия отметок этап "
                            + result);
        }
        return new Plan(previous, target, List.copyOf(forget), null);
    }

    public static OptionalInt ticketStage(
            String ticketId, BoardCase boardCase, int level, StageRules rules, Map<String, Integer> buildingStages) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: по нему видно, к какому этапу оно относится");
        Objects.requireNonNull(boardCase, "Кейс задания не может быть null: без него этап задания не вычислить");
        Objects.requireNonNull(rules, "Правила этапов не могут быть null: по ним считается этап задания");
        Objects.requireNonNull(buildingStages, "Этапы построек не могут быть null: по ним считается этап задания");
        OptionalInt byPlan = planId(ticketId)
                .map(id -> planStage(id, rules, buildingStages))
                .filter(stage -> stage > 0)
                .map(stage -> OptionalInt.of(stage))
                .orElseGet(OptionalInt::empty);
        if (byPlan.isPresent()) {
            return byPlan;
        }
        return orEmpty(caseStage(boardCase, level, rules));
    }

    private static List<Mark> marks(Set<String> keys, StageRules rules, Map<String, Integer> buildingStages) {
        List<Mark> marks = new ArrayList<>(keys.size());
        for (String key : keys) {
            marks.add(mark(key, rules, buildingStages));
        }
        marks.sort(Comparator.comparing(Mark::key));
        return marks;
    }

    private static Mark mark(String key, StageRules rules, Map<String, Integer> buildingStages) {
        if (!key.startsWith(BoardLedger.PREFIX)) {
            throw new IllegalArgumentException("Ключ отметки '" + key + "' не начинается с '" + BoardLedger.PREFIX
                    + "': в журнале отметок только постройки этапов");
        }
        int cut = key.lastIndexOf(':');
        if (cut <= BoardLedger.PREFIX.length() || cut == key.length() - 1) {
            throw new IllegalArgumentException(
                    "Ключ отметки '" + key + "' не разбирается как «постройка:уровень»: откат не знает, что снимать");
        }
        String name = key.substring(BoardLedger.PREFIX.length(), cut);
        int level;
        try {
            level = Integer.parseInt(key.substring(cut + 1));
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "Ключ отметки '" + key + "' заканчивается не числом уровня: "
                            + "откат не может снять отметку по такому ключу",
                    notANumber);
        }
        BoardCase boardCase = boardCase(name);
        if (boardCase != null) {
            return new Mark(key, boardCase, null, level, caseStage(boardCase, level, rules));
        }
        return new Mark(key, null, name, level, planStage(name, rules, buildingStages));
    }

    private static @Nullable BoardCase boardCase(String name) {
        try {
            return BoardCase.valueOf(name);
        } catch (IllegalArgumentException notACase) {
            return null;
        }
    }

    private static int caseStage(BoardCase boardCase, int level, StageRules rules) {
        boolean staged = false;
        for (StageRules.Stage stage : rules.stages()) {
            Integer wanted = stage.buildings().get(boardCase);
            if (wanted == null) {
                continue;
            }
            staged = true;
            if (wanted == level) {
                return stage.stage();
            }
        }
        return staged ? rules.stages().size() + 1 : 0;
    }

    private static int planStage(String planId, StageRules rules, Map<String, Integer> buildingStages) {
        Integer plain = buildingStages.get(planId);
        if (plain != null) {
            return plain;
        }
        String alias = ALIASES.get(planId);
        if (alias != null && buildingStages.get(alias) != null) {
            return buildingStages.get(alias);
        }
        for (StageRules.Stage stage : rules.stages()) {
            if (stage.plan().contains(planId) || stage.requires().containsKey(planId)) {
                return stage.stage();
            }
        }
        String candidate = planId;
        int cut = candidate.lastIndexOf('_');
        while (cut > 0) {
            candidate = candidate.substring(0, cut);
            Integer found = buildingStages.get(candidate);
            if (found != null) {
                return found;
            }
            cut = candidate.lastIndexOf('_');
        }
        return 0;
    }

    private static Optional<String> planId(String ticketId) {
        if (!ticketId.startsWith(GRAPH_BUILDING) || ticketId.length() == GRAPH_BUILDING.length()) {
            return Optional.empty();
        }
        String name = ticketId.substring(GRAPH_BUILDING.length());
        int cut = name.indexOf(':');
        return Optional.of(cut < 0 ? name : name.substring(0, cut));
    }

    private static OptionalInt orEmpty(int stage) {
        return stage > 0 ? OptionalInt.of(stage) : OptionalInt.empty();
    }
}

package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StageAudit {
    private static final int PERCENT = 100;

    private StageAudit() {}

    public static boolean allDone(List<BoardStatus> statuses) {
        Objects.requireNonNull(statuses, "Статусы частей не могут быть null: по ним решают, закрыт ли шаг");
        return !statuses.isEmpty() && statuses.stream().allMatch(status -> status == BoardStatus.DONE);
    }

    public static Optional<String> nothingToBuild(int total) {
        if (total < 0) {
            throw new IllegalArgumentException(
                    "План стройки на " + total + " блоков: число блоков не бывает отрицательным, проверьте подсчёт");
        }
        return total == 0
                ? Optional.of("стройка закрыта без работы: в плане 0 блоков — клетки ещё не освобождены "
                        + "или строить нечего")
                : Optional.empty();
    }

    public static boolean unbuilt(int missing, int total, int rebuildPercent) {
        if (missing < 0 || total < 0 || missing > total) {
            throw new IllegalArgumentException("Аудит постройки: нет " + missing + " из " + total
                    + " — недостача не может быть отрицательной или больше плана, проверьте подсчёт блоков");
        }
        return total > 0 && (long) missing * PERCENT > (long) rebuildPercent * total;
    }

    public enum Decision {
        COMPLETE,
        AUDIT,
        OPEN
    }

    public static Decision decide(BoardStatus base, boolean partsDone, int done, int total) {
        if (done < 0 || total < 0 || done > total) {
            throw new IllegalArgumentException("Готовность постройки: " + done + " из " + total
                    + " — поставленное не может быть больше плана, проверьте подсчёт блоков");
        }
        if (total > 0 && done >= total) {
            return Decision.COMPLETE;
        }
        if (done > 0 && base != BoardStatus.ASSIGNED) {
            return Decision.AUDIT;
        }
        if (base != BoardStatus.DONE || !partsDone) {
            return Decision.OPEN;
        }
        return Decision.AUDIT;
    }

    public static boolean repairBlockedByUpgrade(BoardCase step, boolean upgrading) {
        Objects.requireNonNull(step, "Кейс ремонта не может быть null: от него зависит, ждать ли апгрейд");
        return upgrading && step != BoardCase.BUILD_MINE;
    }

    public static boolean keeps(BoardOutcome outcome) {
        Objects.requireNonNull(outcome, "Исход ремонта не может быть null: по нему решают, снимать ли отметку");
        return outcome == BoardOutcome.ACCEPTED || outcome == BoardOutcome.DUPLICATE || outcome == BoardOutcome.BACKOFF;
    }

    public static boolean progressed(int placedBefore, int placedAfter, int total) {
        return placedAfter > placedBefore || placedAfter >= total;
    }

    public static Map<String, Integer> shortage(Map<String, Integer> need) {
        Map<String, Integer> shortage = new HashMap<>();
        need.forEach((item, count) -> {
            if (count != null && count > 0) {
                shortage.put(item, count);
            }
        });
        return Map.copyOf(shortage);
    }
}

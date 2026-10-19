package com.theminespirit.core.husbandry;

import java.util.Objects;

public record HerdTally(
        int fed,
        int babiesFed,
        int born,
        int sheared,
        int milked,
        int eggsPicked,
        int culled,
        int kept,
        int escapedReturned,
        int skipped,
        int target) {
    public HerdTally {
        if (fed < 0
                || babiesFed < 0
                || born < 0
                || sheared < 0
                || milked < 0
                || eggsPicked < 0
                || culled < 0
                || kept < 0
                || escapedReturned < 0
                || skipped < 0
                || target < 0) {
            throw new IllegalArgumentException("Счётчики итога стада не могут быть отрицательными: fed=" + fed
                    + ", babiesFed=" + babiesFed + ", born=" + born + ", sheared=" + sheared + ", milked=" + milked
                    + ", eggsPicked=" + eggsPicked + ", culled=" + culled + ", kept=" + kept + ", escapedReturned="
                    + escapedReturned + ", skipped=" + skipped + ", target=" + target);
        }
    }

    public Outcome outcome(HerdJob job) {
        Objects.requireNonNull(job, "Работа не может быть null: без неё неизвестно, что считать успехом");
        if (!hasWork()) {
            return new Failed("нечего делать: стадо не дало ни голов, ни продукции" + counters());
        }
        return switch (job) {
            case BREED ->
                born >= Math.max(1, target)
                        ? new Succeeded("сведено пар: " + born + " из " + target + counters())
                        : new Failed("сведено пар: " + born + " из " + target + counters());
            case FEED -> feedOutcome();
            case SHEAR ->
                sheared >= Math.max(1, target)
                        ? new Succeeded("пострижено " + sheared + " из " + target + counters())
                        : new Failed("пострижено " + sheared + " из " + target + counters());
            case MILK ->
                milked >= Math.max(1, target)
                        ? new Succeeded("подоено " + milked + " из " + target + counters())
                        : new Failed("подоено " + milked + " из " + target + counters());
            case COLLECT_EGGS ->
                eggsPicked > 0
                        ? new Succeeded("подобрано яиц: " + eggsPicked + counters())
                        : new Failed("яиц в загоне нет" + counters());
            case CULL ->
                kept > 0 && kept <= Math.max(1, target)
                        ? new Succeeded("забито " + culled + ", содержится " + kept + " из " + target + counters())
                        : new Failed("содержится " + kept + " из " + target + ", забито " + culled + counters());
            case HERD_CHECK -> new Succeeded("стадо: голов " + totalHeads() + counters());
            case HUNT ->
                culled >= Math.max(1, target)
                        ? new Succeeded("забито диких: " + culled + " из " + target + counters())
                        : new Failed("забито диких: " + culled + " из " + target + counters());
        };
    }

    private Outcome feedOutcome() {
        if (fed >= Math.max(1, target)) {
            return new Succeeded(
                    "покормлено " + fed + " из " + target + ", из них детёнышей " + babiesFed + counters());
        }
        if (fed == 0 && escapedReturned > 0) {
            return new Succeeded("отбившиеся возвращены: " + escapedReturned + counters());
        }
        return new Failed("покормлено " + fed + " из " + target + counters());
    }

    private boolean hasWork() {
        return fed > 0
                || babiesFed > 0
                || born > 0
                || sheared > 0
                || milked > 0
                || eggsPicked > 0
                || culled > 0
                || kept > 0
                || escapedReturned > 0;
    }

    private int totalHeads() {
        return fed + babiesFed + born + culled + kept + escapedReturned;
    }

    private String counters() {
        StringBuilder text = new StringBuilder();
        append(text, fed, "покормлено");
        append(text, babiesFed, "детёнышей");
        append(text, born, "родилось");
        append(text, sheared, "пострижено");
        append(text, milked, "подоено");
        append(text, eggsPicked, "яиц");
        append(text, culled, "забито");
        append(text, kept, "содержится");
        append(text, escapedReturned, "возвращено");
        append(text, skipped, "пропущено опасных");
        return text.toString();
    }

    private static void append(StringBuilder text, int count, String label) {
        if (count > 0) {
            text.append("; ").append(label).append(": ").append(count);
        }
    }

    public sealed interface Outcome permits Succeeded, Failed {}

    public record Succeeded(String text) implements Outcome {
        public Succeeded {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Текст успеха не может быть пустым: стратег по числам решает, что делать дальше");
            }
        }
    }

    public record Failed(String text) implements Outcome {
        public Failed {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Текст провала не может быть пустым: стратег по нему решает, что делать дальше");
            }
        }
    }
}

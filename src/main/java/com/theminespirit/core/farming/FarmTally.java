package com.theminespirit.core.farming;

public record FarmTally(int done, int requested, int ineligible, int noStand, int noSeeds, int noTool, int dry,
        int unreachable, int radius) {
    public FarmTally {
        if (done < 0 || requested < 0 || ineligible < 0 || noStand < 0 || noSeeds < 0 || noTool < 0 || dry < 0
                || unreachable < 0 || radius < 0) {
            throw new IllegalArgumentException("Счётчики итога работы не могут быть отрицательными: done=" + done
                    + ", requested=" + requested + ", ineligible=" + ineligible + ", noStand=" + noStand
                    + ", noSeeds=" + noSeeds + ", noTool=" + noTool + ", dry=" + dry + ", unreachable=" + unreachable
                    + ", radius=" + radius);
        }
    }

    public Outcome outcome(FarmJob job) {
        if (job == null) {
            throw new IllegalArgumentException("Работа не может быть null: без неё неизвестно, что считать успехом");
        }
        if (noTool > 0) {
            return new Failed(missingTool(job) + progressSuffix() + counters(job));
        }
        if (requested <= 0) {
            if (job == FarmJob.HARVEST) {
                return new Failed(nothingToDo(job) + counters(job));
            }
            return new Failed(nothingToDo(job) + progressSuffix() + counters(job));
        }
        if (done >= requested && noStand == 0 && noSeeds == 0 && unreachable == 0) {
            return new Succeeded();
        }
        return new Failed(progress(job) + counters(job));
    }

    private String missingTool(FarmJob job) {
        String what = switch (job) {
            case TILL -> "нет мотыги в инвентаре";
            case PLANT -> "нет семян в инвентаре";
            case HARVEST -> "нет семян для пересадки";
            case BONE_MEAL -> "нет костной муки в инвентаре";
        };
        return what + ": работа не начата, мир не изменён";
    }

    private String nothingToDo(FarmJob job) {
        return switch (job) {
            case TILL -> "подходящей земли для вспашки нет";
            case PLANT -> "пустой вспаханной земли нет";
            case HARVEST -> "зрелых посевов в радиусе " + radius + " нет; растёт: " + ineligible;
            case BONE_MEAL -> "недозрелых посевов нет";
        };
    }

    private String progress(FarmJob job) {
        return switch (job) {
            case TILL -> "вспахано " + done + " из " + requested + " клеток";
            case PLANT -> "посажено " + done + " из " + requested + " клеток";
            case HARVEST -> "собрано " + done + " из " + requested + " зрелых клеток";
            case BONE_MEAL -> "подкормлено " + done + " из " + requested + " посевов";
        };
    }

    private String progressSuffix() {
        return "; сделано " + done + " из " + requested;
    }

    private String counters(FarmJob job) {
        StringBuilder text = new StringBuilder();
        append(text, ineligible, job == FarmJob.HARVEST ? "ещё растёт" : "не подходит");
        append(text, noStand, "без места для духа");
        append(text, noSeeds, job == FarmJob.BONE_MEAL ? "нет костной муки" : "нет семян");
        append(text, noTool, "без инструмента");
        append(text, dry, "без воды");
        append(text, unreachable, "недостижимо");
        return text.toString();
    }

    private static void append(StringBuilder text, int count, String reason) {
        if (count > 0) {
            text.append("; ").append(reason).append(": ").append(count);
        }
    }

    public sealed interface Outcome permits Succeeded, Failed {
    }

    public record Succeeded() implements Outcome {
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

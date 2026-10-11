package com.theminespirit.core.engine;

import java.util.Objects;

public record RunProgress(int step, int totalSteps, String currentScript, Integer done, Integer target) {
    public RunProgress {
        if (step < 1) {
            throw new IllegalArgumentException("Шаг задачи " + step + " меньше 1: шаги нумеруются с 1");
        }
        if (totalSteps < 1) {
            throw new IllegalArgumentException("Число шагов задачи " + totalSteps + " меньше 1: у задачи есть корень");
        }
        if (step > totalSteps) {
            throw new IllegalArgumentException("Шаг " + step + " больше числа шагов " + totalSteps
                    + ": позиция не может выйти за пределы плана");
        }
        if (done != null && done < 0) {
            throw new IllegalArgumentException("Счётчик done=" + done + " отрицателен: сделанного не бывает меньше нуля");
        }
        if (target != null && target < 1) {
            throw new IllegalArgumentException("Цель target=" + target + " меньше 1: счётный скрипт стремится к положительной цели");
        }
    }

    public static RunProgress singleStep(String currentScript) {
        return new RunProgress(1, 1, currentScript, null, null);
    }
}

package com.theminespirit.core.board;

import java.util.Objects;

public final class BuildProgressNote {
    private BuildProgressNote() {}

    public static String line(BoardCase boardCase, int level, int stageLevel, int done, int total, int cut, int fill) {
        Objects.requireNonNull(boardCase, "Кейс стройки не может быть null: строка называет этап");
        if (level < 1 || done < 0 || total < done || cut < 0 || fill < 0) {
            throw new IllegalArgumentException(
                    "Ход стройки " + done + " из " + total + " (уровень " + level + ", срезка " + cut + ", подсыпка "
                            + fill + ") не сходится: счётчики идут вперёд и не превышают целое");
        }
        return "[plot] этап " + boardCase + " уровня " + level + ": поставлено " + done + " из " + total + ", высота "
                + stageLevel + ", срезка " + cut + ", подсыпка " + fill;
    }
}

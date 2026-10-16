package com.theminespirit.core.board;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Жизненный цикл животного на доске: отлов → загон → приманка → разбор укрытия.
 * Переходы — чистые функции состояния адаптер вызывает: captured (capture выполнен),
 * penBuilt (загон стоит), lured (животное в загоне), animalDied (гибель: «приманить»
 * снимается, появляется «разобрать укрытие»). Повторные вызовы не плодят дублей.
 */
public final class AnimalLifecycle {
    private static final String PEN_ID = "animal_pen";
    private static final String LURE_ID = "animal_lure";
    private static final String DISMANTLE_ID = "animal_dismantle";

    private final Board board;
    private final String lureItem;
    private Stage stage = Stage.NONE;

    public AnimalLifecycle(Board board, String lureItem) {
        this.board = Objects.requireNonNull(board, "board не может быть null: доска — "
                + "канал постановки и снятия заданий цикла");
        if (lureItem == null || lureItem.isBlank()) {
            throw new IllegalArgumentException("lureItem пустое: приманка — гейт задания "
                    + "«приманить», без неё доска не отличит готовность к свиданию");
        }
        this.lureItem = lureItem;
    }

    public void captured(String captureId) {
        Objects.requireNonNull(captureId, "captureId не может быть null: загон зависит от "
                + "задания отлова по id, без связи доска выдаст «приманить» без загона");
        if (stage != Stage.NONE || hasActive(BoardCase.BUILD_PEN)) {
            return;
        }
        board.submit(new BoardTicket(PEN_ID, BoardCase.BUILD_PEN, Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_PEN), board.now(), BoardTuning.DEFAULT_TTL_TICKS,
                List.of(), Map.of()));
        stage = Stage.CAPTURED;
    }

    public void penBuilt(String captureId) {
        Objects.requireNonNull(captureId, "captureId не может быть null: «приманить» зависит "
                + "от отлова по id");
        if (stage != Stage.CAPTURED || hasActive(BoardCase.LURE_ANIMAL)) {
            return;
        }
        board.submit(new BoardTicket(LURE_ID, BoardCase.LURE_ANIMAL, Optional.empty(),
                BoardRules.priorityOf(BoardCase.LURE_ANIMAL), board.now(), BoardTuning.DEFAULT_TTL_TICKS,
                List.of(captureId), Map.of(lureItem, 1)));
        stage = Stage.PEN_BUILT;
    }

    public void lured() {
        if (stage != Stage.PEN_BUILT || hasActive(BoardCase.DISMANTLE_SHELTER)) {
            return;
        }
        board.submit(new BoardTicket(DISMANTLE_ID, BoardCase.DISMANTLE_SHELTER, Optional.empty(),
                BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER), board.now(), BoardTuning.DEFAULT_TTL_TICKS,
                List.of(), Map.of()));
        stage = Stage.LURED;
    }

    public void animalDied() {
        if (stage == Stage.NONE) {
            return;
        }
        board.all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                .filter(ticket -> board.statusOf(ticket.id())
                        .map(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .orElse(false))
                .forEach(ticket -> board.cancel(ticket.id()));
        if (!hasActive(BoardCase.DISMANTLE_SHELTER)) {
            board.submit(new BoardTicket(DISMANTLE_ID, BoardCase.DISMANTLE_SHELTER, Optional.empty(),
                    BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER), board.now(),
                    BoardTuning.DEFAULT_TTL_TICKS, List.of(), Map.of()));
        }
        stage = Stage.DISMANTLED;
    }

    private boolean hasActive(BoardCase boardCase) {
        return board.all().stream()
                .filter(ticket -> ticket.boardCase() == boardCase)
                .anyMatch(ticket -> board.statusOf(ticket.id())
                        .map(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .orElse(false));
    }

    private enum Stage {
        NONE,
        CAPTURED,
        PEN_BUILT,
        LURED,
        DISMANTLED
    }
}

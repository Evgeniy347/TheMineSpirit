package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Жизненный цикл животного одного вида на доске: отлов приманкой → загон → привести в загон. Укрытия из блоков
 * нет (упрощение 44.4, решение владельца 18.10), разбирать после отлова нечего. Переходы — чистые функции
 * состояния, адаптер вызывает: captured (capture выполнен), penBuilt (загон стоит), lured (животное в загоне),
 * animalDied (гибель: «приманить» снимается). Повторные вызовы не плодят дублей.
 * Префикс id делает цикл независимым: реестр {@link HerdCycles} держит по циклу на вид.
 */
public final class AnimalLifecycle {
    private static final String DEFAULT_PREFIX = "animal";

    private final Board board;
    private final String prefix;
    private final String lureItem;
    private Stage stage = Stage.NONE;
    private String cause = BoardTicket.ALWAYS_CAUSE;

    public AnimalLifecycle(Board board, String lureItem) {
        this(board, DEFAULT_PREFIX, lureItem);
    }

    public AnimalLifecycle(Board board, Species species) {
        this(
                board,
                species.name().toLowerCase(Locale.ROOT),
                shortId(FeedTable.feedFor(species).get(0)));
    }

    private static String shortId(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }

    private AnimalLifecycle(Board board, String prefix, String lureItem) {
        this.board = Objects.requireNonNull(
                board, "board не может быть null: доска — " + "канал постановки и снятия заданий цикла");
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException(
                    "prefix цикла пуст: по нему различают циклы видов, " + "без префикса два вида писали бы в одни id");
        }
        if (lureItem == null || lureItem.isBlank()) {
            throw new IllegalArgumentException("lureItem пустое: приманка — гейт задания "
                    + "«приманить», без неё доска не отличит готовность к свиданию");
        }
        this.prefix = prefix;
        this.lureItem = lureItem;
    }

    public void captured(String captureId) {
        Objects.requireNonNull(
                captureId,
                "captureId не может быть null: загон зависит от "
                        + "задания отлова по id, без связи доска выдаст «приманить» без загона");
        if (stage != Stage.NONE || hasActive(penId())) {
            return;
        }
        cause = "animal:" + captureId;
        board.submit(new BoardTicket(
                penId(),
                BoardCase.BUILD_PEN,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_PEN),
                board.now(),
                1,
                cause,
                List.of(),
                Map.of()));
        stage = Stage.CAPTURED;
    }

    public void penBuilt(String captureId) {
        Objects.requireNonNull(captureId, "captureId не может быть null: «приманить» зависит " + "от отлова по id");
        if (stage != Stage.CAPTURED || hasActive(lureId())) {
            return;
        }
        board.submit(new BoardTicket(
                lureId(),
                BoardCase.LURE_ANIMAL,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.LURE_ANIMAL),
                board.now(),
                1,
                cause,
                List.of(captureId),
                Map.of(lureItem, 1)));
        stage = Stage.PEN_BUILT;
    }

    public void lured() {
        if (stage == Stage.PEN_BUILT) {
            stage = Stage.LURED;
        }
    }

    public void animalDied() {
        if (stage == Stage.NONE) {
            return;
        }
        board.cancel(lureId());
        stage = Stage.GONE;
    }

    private boolean hasActive(String ticketId) {
        return board.statusOf(ticketId)
                .map(status -> status == BoardStatus.PENDING
                        || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .orElse(false);
    }

    private String penId() {
        return prefix + "_pen";
    }

    private String lureId() {
        return prefix + "_lure";
    }

    private enum Stage {
        NONE,
        CAPTURED,
        PEN_BUILT,
        LURED,
        GONE
    }
}

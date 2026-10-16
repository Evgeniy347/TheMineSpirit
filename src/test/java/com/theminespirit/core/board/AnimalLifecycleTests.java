package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class AnimalLifecycleTests {

    private static boolean hasActive(Board board, BoardCase boardCase) {
        return board.all().stream()
                .filter(ticket -> ticket.boardCase() == boardCase)
                .anyMatch(ticket -> board.statusOf(ticket.id())
                        .map(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .orElse(false));
    }

    private static void captureOnBoard(Board board, String captureId) {
        board.submit(new BoardTicket(captureId, BoardCase.CAPTURE_ANIMAL, Optional.empty(),
                BoardRules.priorityOf(BoardCase.CAPTURE_ANIMAL), 0, BoardTuning.DEFAULT_TTL_TICKS,
                List.of(), Map.of()));
        board.claimable(new StockView(Map.of()), 0);
        board.claim(captureId, UUID.randomUUID(), 0);
        board.complete(captureId);
    }

    @Test
    void captured_Submits_BuildPen() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        lifecycle.captured("capture_1");

        assertTrue(hasActive(board, BoardCase.BUILD_PEN),
                "После отлова на доске загон: цикл «отлов → загон» идёт строго по порядку");
    }

    @Test
    void penBuilt_Submits_LureWithGate() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");

        lifecycle.penBuilt("capture_1");

        assertTrue(hasActive(board, BoardCase.LURE_ANIMAL),
                "Загон построен — на доске «приманить»: второе звено цикла");
        BoardTicket lure = board.all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                .findFirst().orElseThrow();
        assertEquals(List.of("capture_1"), lure.dependsOn(),
                "«Приманить» зависит от отлова: без загона приманка не нужна");
        assertEquals(Map.of("wheat", 1), lure.requiredItems(),
                "Гейт «приманить» — приманка: без приманки в запасах задание ждёт, "
                        + "а не тянет духа в пустой загон");
    }

    @Test
    void lure_Gate_Holds() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");

        assertEquals(List.of(), board.claimable(new StockView(Map.of()), 0).stream()
                        .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                        .toList(),
                "Без приманки «приманить» не выдаётся: гейт держит задание в очереди");
        assertEquals(Optional.of(BoardStatus.WAITING_RESOURCES), board.statusOf("animal_lure"),
                "Задание видит наблюдатель как ждущее ресурс, а не потерянное");
        assertEquals(List.of(board.all().stream()
                        .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                        .findFirst().orElseThrow()),
                board.claimable(new StockView(Map.of("wheat", 1)), 0).stream()
                        .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                        .toList(),
                "С приманкой в запасах задание выдаётся: доска сама снимает гейт");
    }

    @Test
    void lured_Submits_DismantleShelter() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");
        board.claimable(new StockView(Map.of("wheat", 1)), 0);
        board.claim("animal_lure", UUID.randomUUID(), 0);
        board.complete("animal_lure");

        lifecycle.lured();

        assertTrue(hasActive(board, BoardCase.DISMANTLE_SHELTER),
                "Животное в загоне — укрытие разбирается: третье звено цикла");
    }

    @Test
    void animalDied_Cancels_Lure() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");

        lifecycle.animalDied();

        assertEquals(Optional.of(BoardStatus.CANCELLED), board.statusOf("animal_lure"),
                "Гибель снимает «приманить»: приманка больше не нужна");
        assertTrue(hasActive(board, BoardCase.DISMANTLE_SHELTER),
                "После гибели появляется «разобрать укрытие»: кольцо отлова разбирается, "
                        + "даже если животное не дожило до загона");
    }

    @Test
    void animalDied_BeforeCapture_SubmitsNothing() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        lifecycle.animalDied();

        assertTrue(board.all().isEmpty(),
                "Гибель до отлова ничего не ставит: укрытия нет, разбирать нечего");
    }

    @Test
    void lured_Twice_SubmitsSingleDismantle() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");

        lifecycle.lured();
        lifecycle.lured();

        assertEquals(1, board.all().stream()
                        .filter(ticket -> ticket.boardCase() == BoardCase.DISMANTLE_SHELTER)
                        .count(),
                "«Разобрать укрытие» дважды не ставится: одно укрытие — одна разборка");
    }

    @Test
    void penBuilt_WithoutCapture_SubmitsNothing() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        lifecycle.penBuilt("capture_1");

        assertTrue(board.all().isEmpty(),
                "«Приманить» без отлова не ставится: загон без животного не нужен");
    }

    @Test
    void penBuilt_AfterDeath_DoesNotReturnLure() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");
        lifecycle.animalDied();

        lifecycle.penBuilt("capture_1");

        assertEquals(Optional.of(BoardStatus.CANCELLED), board.statusOf("animal_lure"),
                "Снятое «приманить» не возвращается без нового отлова: гибель закрыла цикл, "
                        + "повторный penBuilt не оживляет мёртвое задание");
        assertFalse(hasActive(board, BoardCase.LURE_ANIMAL),
                "После гибели цикла новых заданий «приманить» нет: сначала новый отлов");
    }

    @Test
    void lifecycle_NullArgs_AreRejected() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        assertThrows(NullPointerException.class, () -> lifecycle.captured(null),
                "captureId=null обязан отвергаться: без id зависимость «приманить» потеряна");
        assertThrows(NullPointerException.class, () -> lifecycle.penBuilt(null),
                "captureId=null обязан отвергаться: без id зависимость потеряна");
    }

    @Test
    void lifecycle_NullBoard_IsRejected() {
        assertThrows(NullPointerException.class, () -> new AnimalLifecycle(null, "wheat"),
                "Доска обязана быть задана: без неё цикл не может ставить и снимать задания");
    }

    @Property
    void anyEventSequence_Distinct_Actives(@ForAll("eventSequences") List<String> events) {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");

        for (String event : events) {
            switch (event) {
                case "penBuilt" -> lifecycle.penBuilt("capture_1");
                case "lured" -> lifecycle.lured();
                case "animalDied" -> lifecycle.animalDied();
                default -> throw new IllegalArgumentException("Неизвестное событие: " + event);
            }
        }

        for (BoardCase boardCase : List.of(BoardCase.BUILD_PEN, BoardCase.LURE_ANIMAL,
                BoardCase.DISMANTLE_SHELTER)) {
            long active = board.all().stream()
                    .filter(ticket -> ticket.boardCase() == boardCase)
                    .filter(ticket -> board.statusOf(ticket.id())
                            .map(status -> status == BoardStatus.PENDING
                                    || status == BoardStatus.WAITING_RESOURCES
                                    || status == BoardStatus.ASSIGNED)
                            .orElse(false))
                    .count();
            assertTrue(active <= 1,
                    "Из любого состояния цикл не порождает двух активных заданий кейса "
                            + boardCase + ": получено " + active);
        }
    }

    @Property
    void sameSequence_Is_Deterministic(@ForAll("eventSequences") List<String> events) {
        Board first = new Board(() -> 0);
        AnimalLifecycle firstLifecycle = new AnimalLifecycle(first, "wheat");
        firstLifecycle.captured("capture_1");
        Board second = new Board(() -> 0);
        AnimalLifecycle secondLifecycle = new AnimalLifecycle(second, "wheat");
        secondLifecycle.captured("capture_1");

        for (String event : events) {
            apply(firstLifecycle, event);
            apply(secondLifecycle, event);
        }

        assertEquals(first.all(), second.all(),
                "Переходы цикла детерминированы: один вход — один состав доски, иначе "
                        + "наблюдатель видит разное состояние при тех же событиях мира");
        for (String event : events) {
            apply(firstLifecycle, event);
            apply(secondLifecycle, event);
        }
        assertEquals(first.all(), second.all(),
                "Детерминизм сохраняется на второй прогон: состояние автомата зависит "
                        + "только от последовательности событий");
    }

    private static void apply(AnimalLifecycle lifecycle, String event) {
        switch (event) {
            case "penBuilt" -> lifecycle.penBuilt("capture_1");
            case "lured" -> lifecycle.lured();
            case "animalDied" -> lifecycle.animalDied();
            default -> throw new IllegalArgumentException("Неизвестное событие: " + event);
        }
    }

    @Provide
    Arbitrary<List<String>> eventSequences() {
        Arbitrary<String> events = Arbitraries.of("penBuilt", "lured", "animalDied");
        return events.list().ofMaxSize(6);
    }
}

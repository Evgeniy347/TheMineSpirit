package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class AnimalLifecycleTests {

    @Provide
    Arbitrary<List<String>> eventSequences() {
        Arbitrary<String> events = Arbitraries.of("penBuilt", "lured", "animalDied");
        return events.list().ofMaxSize(6);
    }

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
        board.submit(new BoardTicket(
                captureId,
                BoardCase.CAPTURE_ANIMAL,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.CAPTURE_ANIMAL),
                0,
                1,
                "animal:capture_1",
                List.of(),
                Map.of()));
        board.claimable(new StockView(Map.of()), 0);
        board.claim(captureId, UUID.randomUUID(), 0);
        board.complete(captureId);
    }

    @Test
    void captured_Submits_BuildPen() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        lifecycle.captured("capture_1");

        assertTrue(
                hasActive(board, BoardCase.BUILD_PEN),
                "После отлова на доске загон: цикл «отлов → загон» идёт строго по порядку Иначе тест captured_Submits_BuildPen перестанет ловить эту поломку. При смене captured_Submits_BuildPen проверь это утверждение.");
    }

    @Test
    void penBuilt_Submits_LureWithGate() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");

        lifecycle.penBuilt("capture_1");

        assertTrue(
                hasActive(board, BoardCase.LURE_ANIMAL),
                "Загон построен — на доске «приманить»: второе звено цикла Иначе тест penBuilt_Submits_LureWithGate перестанет ловить эту поломку. При смене penBuilt_Submits_LureWithGate проверь это утверждение.");
        BoardTicket lure = board.all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of("capture_1"),
                lure.dependsOn(),
                "«Приманить» зависит от отлова: без загона приманка не нужна Иначе тест penBuilt_Submits_LureWithGate перестанет ловить эту поломку. При смене penBuilt_Submits_LureWithGate проверь это утверждение.");
        assertEquals(
                Map.of("wheat", 1),
                lure.requiredItems(),
                "Гейт «приманить» — приманка: без приманки в запасах задание ждёт, "
                        + "а не тянет духа в пустой загон Иначе тест penBuilt_Submits_LureWithGate перестанет ловить эту поломку. При смене penBuilt_Submits_LureWithGate проверь это утверждение.");
    }

    @Test
    void lure_Gate_Holds() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");

        assertEquals(
                List.of(),
                board.claimable(new StockView(Map.of()), 0).stream()
                        .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                        .toList(),
                "Без приманки «приманить» не выдаётся: гейт держит задание в очереди Иначе тест lure_Gate_Holds перестанет ловить эту поломку. При смене lure_Gate_Holds проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.WAITING_RESOURCES),
                board.statusOf("animal_lure"),
                "Задание видит наблюдатель как ждущее ресурс, а не потерянное Иначе тест lure_Gate_Holds перестанет ловить эту поломку. При смене lure_Gate_Holds проверь это утверждение.");
        assertEquals(
                List.of(board.all().stream()
                        .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                        .findFirst()
                        .orElseThrow()),
                board.claimable(new StockView(Map.of("wheat", 1)), 0).stream()
                        .filter(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL)
                        .toList(),
                "С приманкой в запасах задание выдаётся: доска сама снимает гейт Иначе тест lure_Gate_Holds перестанет ловить эту поломку. При смене lure_Gate_Holds проверь это утверждение.");
    }

    @Test
    void lured_AnimalInPen_SubmitsNoShelterDismantling() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");
        board.claimable(new StockView(Map.of("wheat", 1)), 0);
        board.claim("animal_lure", UUID.randomUUID(), 0);
        board.complete("animal_lure");

        lifecycle.lured();

        assertFalse(
                hasActive(board, BoardCase.DISMANTLE_SHELTER),
                "Животное в загоне — цикл завершён: укрытия из блоков нет (упрощение 44.4), разбирать нечего Иначе тест lured_AnimalInPen_SubmitsNoShelterDismantling перестанет ловить эту поломку. При смене lured_AnimalInPen_SubmitsNoShelterDismantling проверь это утверждение.");
    }

    @Test
    void animalDied_Cancels_Lure() {
        Board board = new Board(() -> 0);
        captureOnBoard(board, "capture_1");
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");

        lifecycle.animalDied();

        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("animal_lure"),
                "Гибель снимает «приманить»: приманка больше не нужна Иначе тест animalDied_Cancels_Lure перестанет ловить эту поломку. При смене animalDied_Cancels_Lure проверь это утверждение.");
        assertFalse(
                hasActive(board, BoardCase.DISMANTLE_SHELTER),
                "После гибели разбирать нечего: отлов приманкой не ставит укрытия (упрощение 44.4) Иначе тест animalDied_Cancels_Lure перестанет ловить эту поломку. При смене animalDied_Cancels_Lure проверь это утверждение.");
    }

    @Test
    void animalDied_BeforeCapture_SubmitsNothing() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        lifecycle.animalDied();

        assertTrue(
                board.all().isEmpty(),
                "Гибель до отлова ничего не ставит: укрытия нет, разбирать нечего Иначе тест animalDied_BeforeCapture_SubmitsNothing перестанет ловить эту поломку. При смене animalDied_BeforeCapture_SubmitsNothing проверь это утверждение.");
    }

    @Test
    void penBuilt_WithoutCapture_SubmitsNothing() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        lifecycle.penBuilt("capture_1");

        assertTrue(
                board.all().isEmpty(),
                "«Приманить» без отлова не ставится: загон без животного не нужен Иначе тест penBuilt_WithoutCapture_SubmitsNothing перестанет ловить эту поломку. При смене penBuilt_WithoutCapture_SubmitsNothing проверь это утверждение.");
    }

    @Test
    void penBuilt_AfterDeath_DoesNotReturnLure() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");
        lifecycle.captured("capture_1");
        lifecycle.penBuilt("capture_1");
        lifecycle.animalDied();

        lifecycle.penBuilt("capture_1");

        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("animal_lure"),
                "Снятое «приманить» не возвращается без нового отлова: гибель закрыла цикл, "
                        + "повторный penBuilt не оживляет мёртвое задание Иначе тест penBuilt_AfterDeath_DoesNotReturnLure перестанет ловить эту поломку. При смене penBuilt_AfterDeath_DoesNotReturnLure проверь это утверждение.");
        assertFalse(
                hasActive(board, BoardCase.LURE_ANIMAL),
                "После гибели цикла новых заданий «приманить» нет: сначала новый отлов Иначе тест penBuilt_AfterDeath_DoesNotReturnLure перестанет ловить эту поломку. При смене penBuilt_AfterDeath_DoesNotReturnLure проверь это утверждение.");
    }

    @Test
    void captured_LateClock_SubmitsPen() {
        AtomicLong clock = new AtomicLong(100_000);
        Board board = new Board(clock::get);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        lifecycle.captured("capture_1");

        assertTrue(
                hasActive(board, BoardCase.BUILD_PEN),
                "Отлов ставит загон временем доски, а не нулевым createdAt: иначе через 5 минут "
                        + "работы сервера (тик " + clock.get() + ") задание просрочено уже при постановке "
                        + "и цикл животного молча не идёт При смене captured_LateClock_SubmitsPen проверь это утверждение.");
    }

    @Test
    void lifecycle_NullArgs_AreRejected() {
        Board board = new Board(() -> 0);
        AnimalLifecycle lifecycle = new AnimalLifecycle(board, "wheat");

        assertThrows(
                NullPointerException.class,
                () -> lifecycle.captured(null),
                "captureId=null обязан отвергаться: без id зависимость «приманить» потеряна Иначе тест lifecycle_NullArgs_AreRejected перестанет ловить эту поломку. При смене lifecycle_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> lifecycle.penBuilt(null),
                "captureId=null обязан отвергаться: без id зависимость потеряна Иначе тест lifecycle_NullArgs_AreRejected перестанет ловить эту поломку. При смене lifecycle_NullArgs_AreRejected проверь это утверждение.");
    }

    @Test
    void lifecycle_NullBoard_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new AnimalLifecycle(null, "wheat"),
                "Доска обязана быть задана: без неё цикл не может ставить и снимать задания Иначе тест lifecycle_NullBoard_IsRejected перестанет ловить эту поломку. При смене lifecycle_NullBoard_IsRejected проверь это утверждение.");
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
        for (BoardCase boardCase : List.of(BoardCase.BUILD_PEN, BoardCase.LURE_ANIMAL, BoardCase.DISMANTLE_SHELTER)) {
            long active = board.all().stream()
                    .filter(ticket -> ticket.boardCase() == boardCase)
                    .filter(ticket -> board.statusOf(ticket.id())
                            .map(status -> status == BoardStatus.PENDING
                                    || status == BoardStatus.WAITING_RESOURCES
                                    || status == BoardStatus.ASSIGNED)
                            .orElse(false))
                    .count();
            assertTrue(
                    active <= 1,
                    "Из любого состояния цикл не порождает двух активных заданий кейса " + boardCase
                            + ": получено  Иначе тест anyEventSequence_Distinct_Actives перестанет ловить эту поломку. При смене anyEventSequence_Distinct_Actives проверь это утверждение."
                            + active);
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
            AnimalLifecycleFixtures.apply(firstLifecycle, event);
            AnimalLifecycleFixtures.apply(secondLifecycle, event);
        }
        assertEquals(
                first.all(),
                second.all(),
                "Переходы цикла детерминированы: один вход — один состав доски, иначе "
                        + "наблюдатель видит разное состояние при тех же событиях мира При смене sameSequence_Is_Deterministic проверь это утверждение.");
        for (String event : events) {
            AnimalLifecycleFixtures.apply(firstLifecycle, event);
            AnimalLifecycleFixtures.apply(secondLifecycle, event);
        }
        assertEquals(
                first.all(),
                second.all(),
                "Детерминизм сохраняется на второй прогон: состояние автомата зависит "
                        + "только от последовательности событий Иначе тест sameSequence_Is_Deterministic перестанет ловить эту поломку. При смене sameSequence_Is_Deterministic проверь это утверждение.");
    }
}

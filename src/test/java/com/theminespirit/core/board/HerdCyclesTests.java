package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class HerdCyclesTests {

    private static boolean active(Board board, String ticketId) {
        return board.statusOf(ticketId)
                .map(status -> status == BoardStatus.PENDING
                        || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .orElse(false);
    }

    private static void apply(AnimalLifecycle lifecycle, String captureId, String event) {
        switch (event) {
            case "penBuilt" -> lifecycle.penBuilt(captureId);
            case "lured" -> lifecycle.lured();
            case "animalDied" -> lifecycle.animalDied();
            default -> throw new IllegalArgumentException("Неизвестное событие: " + event);
        }
    }

    @Test
    void cycle_TwoSpecies_TwoCyclesDoNotInterfere() {
        Board board = new Board(() -> 0);
        HerdCycles cycles = new HerdCycles(board);

        cycles.cycle(Species.COW).captured("cow_1");
        cycles.cycle(Species.PIG).captured("pig_1");

        assertTrue(active(board, "cow_pen") && active(board, "pig_pen"),
                "У каждого вида свой загон: два вида — два независимых цикла, а не одна машина");
        assertEquals(List.of("cow_pen", "pig_pen"),
                board.all().stream().map(ticket -> ticket.id()).sorted().toList(),
                "id заданий видов различаются префиксом: иначе второй вид затирал бы задания первого");
    }

    @Test
    void cycle_TwiceSameSpecies_ReturnsSameCycle() {
        Board board = new Board(() -> 0);
        HerdCycles cycles = new HerdCycles(board);

        assertSame(cycles.cycle(Species.COW), cycles.cycle(Species.COW),
                "Один вид — один цикл: два цикла на вид плодили бы вторую очередь заданий");
        assertEquals(1, cycles.species().size(),
                "Реестр помнит виды, которые открывали: новый вид добавляется, повторный — нет");
    }

    @Test
    void cycle_ParallelSpecies_BothLuresActive() {
        Board board = new Board(() -> 0);
        HerdCycles cycles = new HerdCycles(board);
        cycles.cycle(Species.COW).captured("cow_1");
        cycles.cycle(Species.PIG).captured("pig_1");
        cycles.cycle(Species.COW).penBuilt("cow_1");
        cycles.cycle(Species.PIG).penBuilt("pig_1");

        assertTrue(active(board, "cow_lure") && active(board, "pig_lure"),
                "Оба вида приманены параллельно: цикл одного вида не снимает задания другого");
    }

    @Test
    void cycle_Death_RemovesOnlyOwnSpeciesLure() {
        Board board = new Board(() -> 0);
        HerdCycles cycles = new HerdCycles(board);
        cycles.cycle(Species.COW).captured("cow_1");
        cycles.cycle(Species.PIG).captured("pig_1");
        cycles.cycle(Species.COW).penBuilt("cow_1");
        cycles.cycle(Species.PIG).penBuilt("pig_1");

        cycles.cycle(Species.COW).animalDied();

        assertEquals(Optional.of(BoardStatus.CANCELLED), board.statusOf("cow_lure"),
                "Гибель коровы снимает «приманить» коровы: приманка коровы больше не нужна");
        assertTrue(active(board, "pig_lure"),
                "Гибель коровы не трогает свинью: циклы видов независимы");
    }

    @Test
    void cycle_NoPen_NoLure() {
        Board board = new Board(() -> 0);
        HerdCycles cycles = new HerdCycles(board);

        cycles.cycle(Species.COW).penBuilt("cow_1");

        assertTrue(board.all().isEmpty(),
                "«Приманить» без отлова и загона не ставится: зависимость цикла держит порядок");
    }

    @Test
    void cycle_NullSpecies_Rejected() {
        Board board = new Board(() -> 0);
        HerdCycles cycles = new HerdCycles(board);

        assertThrows(NullPointerException.class, () -> cycles.cycle(null),
                "Вид null обязан отвергаться: без него непонятно, чей цикл открывать");
    }

    @Test
    void cycle_NullBoard_Rejected() {
        assertThrows(NullPointerException.class, () -> new HerdCycles(null),
                "Доска обязана быть задана: без неё циклы не могут ставить задания");
    }

    @Property
    void anyEvents_ParallelSpecies_AtMostOneActivePerCase(@ForAll("events") List<String> cowEvents,
            @ForAll("events") List<String> pigEvents) {
        Board board = new Board(() -> 0);
        HerdCycles cycles = new HerdCycles(board);
        cycles.cycle(Species.COW).captured("cow_1");
        cycles.cycle(Species.PIG).captured("pig_1");
        cowEvents.forEach(event -> apply(cycles.cycle(Species.COW), "cow_1", event));
        pigEvents.forEach(event -> apply(cycles.cycle(Species.PIG), "pig_1", event));

        for (String prefix : List.of("cow", "pig")) {
            for (BoardCase boardCase : List.of(BoardCase.BUILD_PEN, BoardCase.LURE_ANIMAL,
                    BoardCase.DISMANTLE_SHELTER)) {
                long active = board.all().stream()
                        .filter(ticket -> ticket.boardCase() == boardCase)
                        .filter(ticket -> ticket.id().startsWith(prefix + "_"))
                        .filter(ticket -> active(board, ticket.id()))
                        .count();
                assertTrue(active <= 1,
                        "У вида " + prefix + " не больше одного активного задания " + boardCase
                                + ": получено " + active + " — два загона или две приманки на вид "
                                + "разошлись бы по доске");
            }
        }
    }

    @Provide
    Arbitrary<List<String>> events() {
        return Arbitraries.of("penBuilt", "lured", "animalDied").list().ofMaxSize(6);
    }
}

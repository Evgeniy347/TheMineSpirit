package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.VitalNeeds;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchVitalStuckTests {
    private static final UUID SPIRIT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final VitalNeeds.Need HUNGER = VitalNeeds.byId("hunger").orElseThrow();

    @Test
    void refusal_StuckHungrySpirit_AllowsFoodButNotOrdinaryWork() {
        Board board = new Board(() -> 0L);
        board.stuck().mark(SPIRIT, 0L, "нет пути к отдыху", true);
        DispatchRound round = new DispatchRound(board, 0L);
        DispatchRound.Candidate hungry = new DispatchRound.Candidate(
                SPIRIT,
                "дух",
                null,
                List.of(),
                new EatNeed(5, 20, 20, 20),
                SpiritView.free(),
                false,
                List.of(),
                Map.of(HUNGER.id(), HUNGER.blocked()),
                List.of());
        BoardTicket food = new BoardTicket(
                "graph:restock_food@" + SPIRIT,
                BoardCase.RESTOCK_FOOD,
                Optional.of(SPIRIT),
                50,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        BoardTicket ordinary = new BoardTicket(
                "graph:gather:stone",
                BoardCase.GATHER_MATERIALS,
                Optional.of(SPIRIT),
                50,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(food);
        board.submit(ordinary);

        assertFalse(
                round.refusal(food, hungry).isPresent(),
                "Застрявший голодный дух получает разрешённый путь к еде: иначе premature stuck-отказ "
                        + "навсегда оставляет его без питания"
                        + " При смене refusal_StuckHungrySpirit_AllowsFoodButNotOrdinaryWork проверь DispatchRound.refusal.");
        assertTrue(
                round.refusal(ordinary, hungry).isPresent(),
                "Обычная работа застрявшему духу всё ещё запрещена: исключение действует только на "
                        + "удовлетворение жизненной нужды"
                        + " Иначе тест refusal_StuckHungrySpirit_AllowsFoodButNotOrdinaryWork перестанет ловить эту поломку. "
                        + "При смене refusal_StuckHungrySpirit_AllowsFoodButNotOrdinaryWork проверь это утверждение.");
    }
}

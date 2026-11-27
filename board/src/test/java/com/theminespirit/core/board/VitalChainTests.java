package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.VitalNeeds;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VitalChainTests {
    private static final UUID SPIRIT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final VitalNeeds.Need HUNGER = VitalNeeds.byId("hunger").orElseThrow();

    @Test
    void withPath_WaitingResources_KeepsHunger() {
        Board board = new Board(() -> 0L);
        BoardTicket food = new BoardTicket(
                "graph:restock_food@" + SPIRIT,
                BoardCase.RESTOCK_FOOD,
                Optional.of(SPIRIT),
                50,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of("bread", 1));
        board.submit(food);

        board.claimable(new StockView(Map.of()), 0L);

        assertEquals(
                Map.of(HUNGER.id(), HUNGER.blocked()),
                VitalChain.withPath(Map.of(HUNGER.id(), HUNGER.blocked()), board, SPIRIT),
                "Голод остаётся живым, пока еда ждёт ресурса: иначе обычная работа снова выдаётся голодному "
                        + "духу и цепочка питания исчезает с доски"
                        + " При смене withPath_WaitingResources_KeepsHunger проверь VitalChain.leads.");
    }

    @Test
    void withPath_FailedFoodTicket_DoesNotCountAsPath() {
        Board board = new Board(() -> 0L);
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
        board.submit(food);
        board.claim(food.id(), SPIRIT, 0L);
        board.fail(food.id());

        assertTrue(
                VitalChain.withPath(Map.of(HUNGER.id(), HUNGER.blocked()), board, SPIRIT)
                        .isEmpty(),
                "Провалившееся задание не считается путём к еде: иначе голод будет выглядеть закрытым, "
                        + "хотя еда так и не получена"
                        + " Иначе тест withPath_FailedFoodTicket_DoesNotCountAsPath перестанет ловить эту поломку. "
                        + "При смене withPath_FailedFoodTicket_DoesNotCountAsPath проверь это утверждение.");
    }
}

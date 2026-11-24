package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanDemolitionOrderTests {
    @Test
    void order_FenceAmongTallerStrays_FenceGoesFirst() {
        BlockCoord highDirt = new BlockCoord(0, 70, 0);
        BlockCoord fence = new BlockCoord(5, 68, 5);

        assertEquals(
                List.of(fence, highDirt),
                PlanDemolition.order(List.of(highDirt, fence), Set.of(fence)),
                "Столб-заглушка обязан идти в уборку первым: иначе задача на снос забора ждёт тысячи чужих клеток"
                        + " При смене order_FenceAmongTallerStrays_FenceGoesFirst проверь это утверждение.");
    }

    @Test
    void stubPieces_DistantStubs_SplitApart() {
        BlockCoord nearTree = new BlockCoord(-158, 68, -523);
        BlockCoord byTrunk = new BlockCoord(-158, 68, -517);

        assertEquals(
                2,
                PlanDemolition.stubPieces(List.of(nearTree, byTrunk)).size(),
                "Каждый столб идёт своим куском: срыв на одном не держит остальные"
                        + " Иначе тест stubPieces_DistantStubs_SplitApart перестанет ловить эту поломку. При смене stubPieces_DistantStubs_SplitApart проверь это утверждение.");
    }

    @Test
    void stubPieces_OrchardFirst_OrchardPieceLeads() {
        BlockCoord orchard = new BlockCoord(-158, 68, -517);
        BlockCoord far = new BlockCoord(-197, 68, -400);

        List<List<BlockCoord>> pieces = PlanDemolition.stubPieces(List.of(orchard, far));

        assertEquals(
                orchard,
                pieces.get(0).get(0),
                "Садовый столб сносится первым: он стоит у дерева и мешает рубке, остальные ждут"
                        + " Иначе тест stubPieces_OrchardFirst_OrchardPieceLeads перестанет ловить эту поломку. При смене stubPieces_OrchardFirst_OrchardPieceLeads проверь это утверждение.");
    }

    @Test
    void stubPieces_NeighbourStubs_OnePiece() {
        BlockCoord first = new BlockCoord(-158, 68, -517);
        BlockCoord second = new BlockCoord(-157, 68, -518);

        List<List<BlockCoord>> pieces = PlanDemolition.stubPieces(List.of(first, second));

        assertEquals(
                1,
                pieces.size(),
                "Соседние столбы сносятся одним куском"
                        + " Иначе тест stubPieces_NeighbourStubs_OnePiece перестанет ловить эту поломку. При смене stubPieces_NeighbourStubs_OnePiece проверь это утверждение.");
        assertEquals(
                Set.of(first, second),
                Set.copyOf(pieces.get(0)),
                "В кусок попадают оба соседних столба, и ничего не теряется"
                        + " Иначе тест stubPieces_NeighbourStubs_OnePiece перестанет ловить эту поломку. При смене stubPieces_NeighbourStubs_OnePiece проверь это утверждение.");
    }

    @Test
    void order_WithoutFences_TallestGoesFirst() {
        BlockCoord low = new BlockCoord(0, 68, 0);
        BlockCoord high = new BlockCoord(1, 70, 1);

        assertEquals(
                List.of(high, low),
                PlanDemolition.order(List.of(low, high), Set.of()),
                "Без заглушек очередь прежняя: выше — раньше"
                        + " Иначе тест order_WithoutFences_TallestGoesFirst перестанет ловить эту поломку. При смене order_WithoutFences_TallestGoesFirst проверь это утверждение.");
    }
}

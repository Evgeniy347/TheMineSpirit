package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaseBookOccupancyTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void occupied_ActiveThenCommittedCell_TrueThenFalse() {
        LeaseBook book = new LeaseBook();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        BlockCoord cell = new BlockCoord(4, 71, 6);
        BlockCoord free = new BlockCoord(4, 72, 6);

        assertFalse(
                book.occupied(free),
                "Свободная клетка не занята: иначе расчистка выкидывает обычные цели и участок не добирает блоки"
                        + " Иначе тест occupied_ActiveThenCommittedCell_TrueThenFalse перестанет ловить эту поломку. При смене occupied_ActiveThenCommittedCell_TrueThenFalse проверь это утверждение.");
        book.record(lease.id(), LeaseCell.placed(cell));
        assertTrue(
                book.occupied(cell),
                "Клетка открытой аренды занята: иначе скан зацепит подмости духа и обрушит его стоянку"
                        + " Иначе тест occupied_ActiveThenCommittedCell_TrueThenFalse перестанет ловить эту поломку. При смене occupied_ActiveThenCommittedCell_TrueThenFalse проверь это утверждение.");
        book.commit(lease.id());
        assertFalse(
                book.occupied(cell),
                "После commit клетка освобождена: иначе занятость копится навсегда и скан перестаёт брать цели"
                        + " Иначе тест occupied_ActiveThenCommittedCell_TrueThenFalse перестанет ловить эту поломку. При смене occupied_ActiveThenCommittedCell_TrueThenFalse проверь это утверждение.");
    }

    @Test
    void revision_EachMutation_Grows() {
        LeaseBook book = new LeaseBook();
        long opened = book.revision();
        Lease lease = book.open(SPIRIT, "t1", "scaffold", 10);
        long afterOpen = book.revision();
        BlockCoord cell = new BlockCoord(5, 70, 7);
        book.record(lease.id(), LeaseCell.placed(cell));
        long afterRecord = book.revision();
        book.commit(lease.id());

        assertTrue(
                afterOpen > opened,
                "open обязан двигать ревизию: иначе пропуск повторов занятости не заметит новую аренду и скан её пропустит"
                        + " Иначе тест revision_EachMutation_Grows перестанет ловить эту поломку. При смене revision_EachMutation_Grows проверь это утверждение.");
        assertTrue(
                afterRecord > afterOpen,
                "record обязан двигать ревизию: иначе новая клетка аренды останется для скана свободной"
                        + " Иначе тест revision_EachMutation_Grows перестанет ловить эту поломку. При смене revision_EachMutation_Grows проверь это утверждение.");
        assertTrue(
                book.revision() > afterRecord,
                "commit обязан двигать ревизию: иначе скан не узнает, что клетки освободились, и пропустит их навсегда"
                        + " Иначе тест revision_EachMutation_Grows перестанет ловить эту поломку. При смене revision_EachMutation_Grows проверь это утверждение.");
    }
}

package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.engine.FailureFact;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpiritFactsTests {
    @Test
    void noPath_NoTreesFact_DoesNotMarkSpiritStuck() {
        assertFalse(
                SpiritFacts.noPath(BoardCase.GATHER_MATERIALS, Optional.of(FailureFact.NO_TREES)),
                "«Деревьев нет» — факт мира, а не застрявший дух: иначе после каждой рубки духа уводят в rescue "
                        + "и он не получает работу, хотя с ним всё в порядке");
    }

    @Test
    void noPath_PathFact_MarksSpiritStuck() {
        assertTrue(
                SpiritFacts.noPath(BoardCase.GATHER_MATERIALS, Optional.of(FailureFact.NO_PATH)),
                "«Пути нет» — по-прежнему метка застревания: дух ждёт спасения, а не новой задачи");
    }

    @Test
    void noPath_RescueCase_IsExempt() {
        assertFalse(
                SpiritFacts.noPath(BoardCase.RESCUE_SPIRIT, Optional.of(FailureFact.NO_PATH)),
                "Провал спасения не ставит метку: иначе дух не получает следующую попытку выхода");
    }
}

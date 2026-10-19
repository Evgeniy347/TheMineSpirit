package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class DropRetriesTests {
    @Test
    void retries_SameSpotTwice_KeepsOneArea() {
        DropRetries retries = new DropRetries(4);

        retries.failed(new BlockCoord(10, 20, 30));
        retries.failed(new BlockCoord(10, 20, 30));

        assertEquals(
                1,
                retries.size(),
                "Повторный провал в той же клетке не добавляет обход: один сборщик с тем же радиусом уже "
                        + "накрывает этот дроп, лишний проход только тратит тики");
        assertEquals(
                new BlockCoord(10, 20, 30),
                retries.poll(),
                "Очередь возвращает место провала, а не пустую клетку: повтор должен собирать дроп там же");
    }

    @Test
    void retries_SpotsWithinRadius_MergeIntoOneArea() {
        DropRetries retries = new DropRetries(4);

        retries.failed(new BlockCoord(0, 0, 0));
        retries.failed(new BlockCoord(4, 0, 0));

        assertEquals(
                1,
                retries.size(),
                "Точки на границе радиуса входят в один обход: сборщик с радиусом 4 накрывает обе, "
                        + "второй проход по тому же дропу не нужен");
    }

    @Test
    void retries_SpotsBeyondRadius_KeepBothInOrder() {
        DropRetries retries = new DropRetries(4);

        retries.failed(new BlockCoord(0, 0, 0));
        retries.failed(new BlockCoord(9, 0, 0));

        assertEquals(
                2,
                retries.size(),
                "Провалы дальше радиуса — разные места: один обход второй дроп не накрыл бы, и он остался бы "
                        + "в мире");
        assertEquals(new BlockCoord(0, 0, 0), retries.poll(), "Первым повторяется более ранний провал");
        assertEquals(new BlockCoord(9, 0, 0), retries.poll(), "Затем — следующий");
        assertTrue(retries.isEmpty(), "После двух обходов очередь пуста");
    }

    @Test
    void retries_EmptyQueue_PollReturnsNull() {
        DropRetries retries = new DropRetries(4);

        assertTrue(retries.isEmpty(), "Новая очередь пуста: повторять нечего");
        assertEquals(0, retries.size(), "Размер пустой очереди — ноль");
        assertNull(retries.poll(), "Из пустой очереди нечего достать: пустой обход не должен запускаться");
    }

    @Test
    void retries_RadiusBelowOne_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DropRetries(0),
                "Нулевой радиус повтора обязан отвергаться: обход не накрыл бы даже клетку провала");
    }

    @Test
    void retries_NullSpot_Throws() {
        DropRetries retries = new DropRetries(4);

        assertThrows(
                NullPointerException.class,
                () -> retries.failed(null),
                "Место провала без координат некуда повторять: null обязан отвергаться");
    }
}

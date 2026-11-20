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
                        + "накрывает этот дроп, лишний проход только тратит тики"
                        + " Иначе тест retries_SameSpotTwice_KeepsOneArea перестанет ловить эту поломку. При смене retries_SameSpotTwice_KeepsOneArea проверь это утверждение.");
        assertEquals(
                new BlockCoord(10, 20, 30),
                retries.poll(),
                "Очередь возвращает место провала, а не пустую клетку: повтор должен собирать дроп там же"
                        + " Иначе тест retries_SameSpotTwice_KeepsOneArea перестанет ловить эту поломку. При смене retries_SameSpotTwice_KeepsOneArea проверь это утверждение.");
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
                        + "второй проход по тому же дропу не нужен"
                        + " Иначе тест retries_SpotsWithinRadius_MergeIntoOneArea перестанет ловить эту поломку. При смене retries_SpotsWithinRadius_MergeIntoOneArea проверь это утверждение.");
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
                        + "в мире"
                        + " Иначе тест retries_SpotsBeyondRadius_KeepBothInOrder перестанет ловить эту поломку. При смене retries_SpotsBeyondRadius_KeepBothInOrder проверь это утверждение.");
        assertEquals(
                new BlockCoord(0, 0, 0),
                retries.poll(),
                "Первым повторяется более ранний провал"
                        + " Иначе тест retries_SpotsBeyondRadius_KeepBothInOrder перестанет ловить эту поломку. При смене retries_SpotsBeyondRadius_KeepBothInOrder проверь это утверждение.");
        assertEquals(
                new BlockCoord(9, 0, 0),
                retries.poll(),
                "Затем — следующий"
                        + " Иначе тест retries_SpotsBeyondRadius_KeepBothInOrder перестанет ловить эту поломку. При смене retries_SpotsBeyondRadius_KeepBothInOrder проверь это утверждение.");
        assertTrue(
                retries.isEmpty(),
                "После двух обходов очередь пуста"
                        + " Иначе тест retries_SpotsBeyondRadius_KeepBothInOrder перестанет ловить эту поломку. При смене retries_SpotsBeyondRadius_KeepBothInOrder проверь это утверждение.");
    }

    @Test
    void retries_EmptyQueue_PollReturnsNull() {
        DropRetries retries = new DropRetries(4);

        assertTrue(
                retries.isEmpty(),
                "Новая очередь пуста: повторять нечего"
                        + " Иначе тест retries_EmptyQueue_PollReturnsNull перестанет ловить эту поломку. При смене retries_EmptyQueue_PollReturnsNull проверь это утверждение.");
        assertEquals(
                0,
                retries.size(),
                "Размер пустой очереди — ноль"
                        + " Иначе тест retries_EmptyQueue_PollReturnsNull перестанет ловить эту поломку. При смене retries_EmptyQueue_PollReturnsNull проверь это утверждение.");
        assertNull(
                retries.poll(),
                "Из пустой очереди нечего достать: пустой обход не должен запускаться"
                        + " Иначе тест retries_EmptyQueue_PollReturnsNull перестанет ловить эту поломку. При смене retries_EmptyQueue_PollReturnsNull проверь это утверждение.");
    }

    @Test
    void retries_RadiusBelowOne_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DropRetries(0),
                "Нулевой радиус повтора обязан отвергаться: обход не накрыл бы даже клетку провала"
                        + " Иначе тест retries_RadiusBelowOne_Throws перестанет ловить эту поломку. При смене retries_RadiusBelowOne_Throws проверь это утверждение.");
    }

    @Test
    void retries_NullSpot_Throws() {
        DropRetries retries = new DropRetries(4);

        assertThrows(
                NullPointerException.class,
                () -> retries.failed(null),
                "Место провала без координат некуда повторять: null обязан отвергаться"
                        + " Иначе тест retries_NullSpot_Throws перестанет ловить эту поломку. При смене retries_NullSpot_Throws проверь это утверждение.");
    }
}

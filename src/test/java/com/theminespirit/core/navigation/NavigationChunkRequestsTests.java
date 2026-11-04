package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavigationChunkRequests.Chunk;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NavigationChunkRequestsTests {
    private static final int PER_TRIP = 8;
    private static final int PER_SECOND = 2;

    @Test
    void corridor_FromSettlementToFarQuarry_CoversChunksWithoutGaps() {
        List<Chunk> chunks =
                NavigationChunkRequests.corridor(new BlockCoord(-171, 69, -489), new BlockCoord(-82, 67, -87), 1);

        assertTrue(
                chunks.contains(new Chunk(-171 >> 4, -489 >> 4)),
                "Коридор начинается с чанка духа: иначе расчёт не начнётся с известного места");
        assertTrue(
                chunks.contains(new Chunk(-82 >> 4, -87 >> 4)),
                "observe22: цель (-82,67,-87) в 430 блоках — её чанк обязан быть в коридоре, иначе путь снова "
                        + "«не найден» по кругу");
        assertTrue(
                chunks.size() >= 25, "На 430 блоках коридор проходит не меньше 27 чанков, получено " + chunks.size());
    }

    @Test
    void next_MoreMissingThanBudget_TakesOnlyPerTrip() {
        NavigationChunkRequests requests = new NavigationChunkRequests(PER_TRIP, 64);
        List<Chunk> missing = chunks(20);

        List<Chunk> chosen = requests.next(0, missing, 0);

        assertEquals(
                PER_TRIP, chosen.size(), "За один расчёт грузят не больше бюджета похода: диск сервера не резиновый");
    }

    @Test
    void next_RepeatedCallsInSecond_RespectPerSecond() {
        NavigationChunkRequests requests = new NavigationChunkRequests(64, PER_SECOND);

        List<Chunk> first = requests.next(0, chunks(20), 0);
        List<Chunk> second = requests.next(1, chunks(20), first.size());
        List<Chunk> third = requests.next(19, chunks(20), first.size() + second.size());

        assertEquals(PER_SECOND, first.size(), "В первую секунду грузят ровно бюджет секунды");
        assertTrue(second.isEmpty(), "Во второй вызов той же секунды новых тикетов нет: бюджет исчерпан");
        assertTrue(third.isEmpty(), "До конца секунды бюджет не обновляется");
    }

    @Test
    void next_NewSecond_BudgetRestored() {
        NavigationChunkRequests requests = new NavigationChunkRequests(64, PER_SECOND);
        requests.next(0, chunks(20), 0);

        List<Chunk> next = requests.next(NavigationChunkRequests.WINDOW_TICKS, chunks(20), PER_SECOND);

        assertEquals(PER_SECOND, next.size(), "Через секунду бюджет загрузки снова полон: поход продолжается");
    }

    @Test
    void next_AlreadyHeld_CountsAgainstTripBudget() {
        NavigationChunkRequests requests = new NavigationChunkRequests(PER_TRIP, 64);

        List<Chunk> chosen = requests.next(0, chunks(20), PER_TRIP);

        assertTrue(
                chosen.isEmpty(),
                "Тикеты прошлых шагов похода входят в бюджет: повторная загрузка того же коридора запрещена");
    }

    private static List<Chunk> chunks(int count) {
        return IntStream.range(0, count).mapToObj(index -> new Chunk(index, 0)).toList();
    }
}

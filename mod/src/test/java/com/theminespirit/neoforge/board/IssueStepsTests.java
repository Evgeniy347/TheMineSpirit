package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

class IssueStepsTests {
    @Test
    void lap_Step_ReturnsNextStart() {
        IssueSteps steps = new IssueSteps();
        long before = System.nanoTime();
        assertTrue(
                steps.lap("claimable", before) >= before,
                "отсечка шага начинает следующий шаг, а не прошлый: иначе время шагов складывается дважды и разметка"
                        + " раздачи врёт, куда уходит тик. Проверить при смене: IssueSteps.lap и порядок вызовов в issueAll");
    }

    @Test
    void end_FastIssue_KeptBelowSlowMark() {
        IssueSteps steps = new IssueSteps();
        steps.begin();
        steps.add("choose", System.nanoTime());
        steps.end(0, NOPLogger.NOP_LOGGER);
        assertTrue(
                steps.worstTotal() < IssueSteps.SLOW_MILLIS,
                "быстрая раздача не попадает в сводку медленных (сервер 12.11: dispatch 600–800 мс вне пересборки)."
                        + " Сломается: журнал засорится строками каждые 1200 тиков. Проверить при смене: SLOW_MILLIS");
    }

    @Test
    void window_BigBoard_HeadPlusRotatingTail() {
        List<Integer> board = IntStream.range(0, 600).boxed().toList();
        List<Integer> first = IssueSteps.window(board, 0);
        List<Integer> wrapped = IssueSteps.window(board, 519);

        assertEquals(
                IssueSteps.HEAD + IssueSteps.TAIL,
                first.size(),
                "раздача смотрит голову доски по приоритету и порцию хвоста, а не все ~600 заявок (сервер 12.11:"
                        + " choose держал тик до 1064 мс). Сломается: тик снова встанет на секунду при большой доске."
                        + " Проверить при смене: порядок DispatchRound.order (голова — самые важные)");
        assertEquals(
                List.of(80, 119),
                List.of(first.get(IssueSteps.HEAD), first.get(first.size() - 1)),
                "хвост начинается сразу за головой: заявка не должна попасть в окно дважды — двойной выбор духа"
                        + " удваивает стоимость. Проверить при смене: смещение HEAD в IssueSteps.window");
        assertEquals(
                List.of(599, 80),
                List.of(wrapped.get(IssueSteps.HEAD), wrapped.get(IssueSteps.HEAD + 1)),
                "окно хвоста идёт по кругу: каждая низкоприоритетная заявка рано или поздно рассматривается."
                        + " Сломается: дешёвые заявки в конце доски не получат духа никогда (голодание)");
    }

    @Test
    void window_SmallBoard_TakesAll() {
        List<Integer> board = List.of(1, 2, 3);
        assertEquals(
                board,
                IssueSteps.window(board, 7),
                "маленькая доска рассматривается целиком и в своём порядке: окно нужно только против сотен заявок."
                        + " Сломается: на малой доске пропадут заявки. Проверить при смене: HEAD + TAIL");
    }
}

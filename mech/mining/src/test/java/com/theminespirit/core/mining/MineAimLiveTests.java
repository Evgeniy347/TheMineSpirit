package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineAimLiveTests {
    private static final Path TICKET = Path.of("src/test/resources/live/observe11/task-build_mine.json");

    @Test
    void aimNext_LiveEntranceOccupied_RequiresAim() throws IOException {
        Observe10Window window = Observe10Window.load("mine-head.json");
        MineCellPlan.Plan plan = MineCellPlan.of(
                window.cell(),
                window.spirit(),
                window,
                new MineCellPlan.Seen(window.occupantType(), false, true, true),
                stand -> true);

        assertEquals(
                MineCellPlan.Kind.WAIT,
                plan.kind(),
                "Живой вход шахты занят существом: тест обязан воспроизвести ожидание, а не подход — получили "
                        + plan.kind() + ": " + plan.reason());
        assertEquals(
                MineAimFlow.Next.AIM,
                MineAimFlow.next(plan.kind()),
                "Шахта стояла 3600 тиков на входе, потому что ожидание не переводило скрипт в фазу AIM: "
                        + "она сбрасывалась каждым тиком и дух не двигался");
    }

    @Test
    void aimNext_LiveWallNotVisible_RequiresAim() throws IOException {
        Observe10Window window = Observe10Window.load("mine-wall.json");
        assertFalse(
                MineDigOrder.visible(window.spirit(), window.cell(), List.of(), window),
                "Тест обязан взять живой отказ видимости: клетка " + window.cell() + " обязана быть невидимой");
        MineCellPlan.Plan plan = MineCellPlan.of(
                window.cell(),
                window.spirit(),
                window,
                new MineCellPlan.Seen(null, false, false, false),
                stand -> true);

        assertEquals(
                MineCellPlan.Kind.APPROACH,
                plan.kind(),
                "Невидимая клетка шахты требует подхода, а не отказа — получили " + plan.kind() + ": " + plan.reason());
        assertEquals(
                MineAimFlow.Next.AIM,
                MineAimFlow.next(plan.kind()),
                "Шахта не подходила к невидимой клетке, потому что aimAt не включал фазу AIM: подход начинался "
                        + "и сбрасывался в том же тике, дух стоял до отмены доской");
    }

    @Test
    void aimNext_ReadyCell_StaysReady() {
        assertEquals(
                MineAimFlow.Next.READY,
                MineAimFlow.next(MineCellPlan.Kind.READY),
                "Готовая к ломке клетка не должна включать подход: это лишний тик ожидания на каждой клетке шахты");
    }

    @Test
    void aimNext_RefusedCell_Fails() {
        assertEquals(
                MineAimFlow.Next.REFUSE,
                MineAimFlow.next(MineCellPlan.Kind.REFUSE),
                "Клетка без стоянки обязана проваливать проходку с причиной, а не стоять молча");
    }

    @Test
    void liveTicket_BuildMineLevelOne_RequiresCobblestone() throws IOException {
        JsonNode ticket = new ObjectMapper().readTree(Files.readString(TICKET, StandardCharsets.UTF_8));

        assertEquals("first:build_mine", ticket.get("id").asText(), "Фикстура обязана быть живым тикетом шахты");
        assertEquals("BUILD_MINE", ticket.get("case").asText(), "Кейс живой задачи — шахта");
        assertEquals(1, ticket.get("level").asInt(), "Первая ступень шахты — уровень 1 спуска");
        assertTrue(
                ticket.get("requiredItems").has("cobblestone"),
                "Спуск в шахту требует булыжник на стенки и пол: без него живой тикет проваливался "
                        + "на «нет булыжника на стенка»");
    }
}

package com.theminespirit.neoforge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class BoardTileTextsTests {
    private static BoardTile tile(String title) {
        return new BoardTile(
                "rest:t-1",
                "ACTIVITY_EXTERNAL",
                1,
                95,
                null,
                "ASSIGNED",
                BoardTileGroup.AVAILABLE,
                Map.of(),
                List.of(),
                0,
                0,
                0,
                0,
                null,
                0,
                List.of(),
                Map.of(),
                Map.of(),
                title);
    }

    @Test
    void taskText_WithTitle_ShowsTitleInsteadOfCaseName() {
        assertEquals(
                Component.literal("Поход за деревом"),
                BoardTileTexts.taskText(tile("Поход за деревом")),
                "Название задачи обязано заменять имя кейса на плитке: без этого пользователь видит "
                        + "«ACTIVITY_EXTERNAL» и не узнаёт свою задачу стратега. При смене выбора подписи в "
                        + "BoardTileTexts проверь непустое название");
    }
}

package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ChatNamesTests {
    @Test
    void display_ItemId_ShowsDisplayName() {
        String shown = ChatNames.display(
                        "невозможно: нет источника minecraft:dirt в данных графа: нужда 1 не снимется",
                        token -> "minecraft:dirt".equals(token) ? Component.literal("Земля") : null)
                .getString();
        assertEquals(
                "невозможно: нет источника «Земля» в данных графа: нужда 1 не снимется",
                shown,
                "Системный id предмета обязан стать отображаемым названием, остальной текст не меняется");
    }

    @Test
    void display_UnknownToken_StaysRaw() {
        String shown = ChatNames.display("нет источника unobtainium в данных графа", token -> null)
                .getString();
        assertEquals(
                "нет источника unobtainium в данных графа",
                shown,
                "Неизвестный токен остаётся как есть: выдумывать название нельзя");
    }

    @Test
    void display_BuildingId_ReplacedAndEventKept() {
        String shown = ChatNames.display(
                        "нужна постройка field_2 (событие more7_field_full), её нет в плане",
                        token -> "field_2".equals(token) ? Component.literal("Пашня") : null)
                .getString();
        assertEquals(
                "нужна постройка «Пашня» (событие more7_field_full), её нет в плане",
                shown,
                "Id постройки заменяется названием, id события без имени остаётся техническим");
    }

    @Test
    void display_RussianTextWithSigns_NotTouched() {
        String text = "нужно ×1, тира 2: всё по-русски";
        assertEquals(
                text,
                ChatNames.display(text, token -> null).getString(),
                "Русский текст, знаки и числа без известного id остаются дословно");
    }
}

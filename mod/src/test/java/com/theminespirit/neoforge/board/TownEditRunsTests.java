package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.theminespirit.core.town.ColumnMask;
import org.junit.jupiter.api.Test;

class TownEditRunsTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void runs_SingleColumnTwoRows_EmitsTwoSegments() throws Exception {
        ColumnMask mask = ColumnMask.rect(4, 2, 4, 3);
        ArrayNode target = JsonNodeFactory.instance.arrayNode();

        TownEditCheck.runs(target, mask);

        JsonNode json = JSON.readTree(target.toString());
        assertEquals(
                2,
                json.size(),
                "Колонка из двух клеток даёт по отрезку на строку: " + json
                        + " Иначе тест runs_SingleColumnTwoRows_EmitsTwoSegments перестанет ловить эту поломку. При смене runs_SingleColumnTwoRows_EmitsTwoSegments проверь это утверждение.");
        assertEquals(
                "[2,4,4]",
                json.get(0).toString(),
                "Первый отрезок обязан быть клеткой в первой строке маски: " + json
                        + " Иначе тест runs_SingleColumnTwoRows_EmitsTwoSegments перестанет ловить эту поломку. При смене runs_SingleColumnTwoRows_EmitsTwoSegments проверь это утверждение.");
        assertEquals(
                "[3,4,4]",
                json.get(1).toString(),
                "Второй отрезок обязан быть клеткой во второй строке маски: " + json
                        + " Иначе тест runs_SingleColumnTwoRows_EmitsTwoSegments перестанет ловить эту поломку. При смене runs_SingleColumnTwoRows_EmitsTwoSegments проверь это утверждение.");
    }

    @Test
    void runs_TwoDisjointRectangles_EmitsRowSegments() throws Exception {
        ColumnMask mask = ColumnMask.rect(0, 0, 1, 1).union(ColumnMask.rect(5, 0, 6, 0));
        ArrayNode target = JsonNodeFactory.instance.arrayNode();

        TownEditCheck.runs(target, mask);

        JsonNode json = JSON.readTree(target.toString());
        assertEquals(
                3,
                json.size(),
                "Две фигуры в одной строке дают два отрезка плюс один в следующей: " + json
                        + " Иначе тест runs_TwoDisjointRectangles_EmitsRowSegments перестанет ловить эту поломку. При смене runs_TwoDisjointRectangles_EmitsRowSegments проверь это утверждение.");
        assertEquals(
                "[0,0,1]",
                json.get(0).toString(),
                "Первый отрезок обязан начинаться у левого края и кончаться до разрыва: " + json
                        + " Иначе тест runs_TwoDisjointRectangles_EmitsRowSegments перестанет ловить эту поломку. При смене runs_TwoDisjointRectangles_EmitsRowSegments проверь это утверждение.");
        assertEquals(
                "[0,5,6]",
                json.get(1).toString(),
                "Второй отрезок обязан покрывать правую фигуру целиком: " + json
                        + " Иначе тест runs_TwoDisjointRectangles_EmitsRowSegments перестанет ловить эту поломку. При смене runs_TwoDisjointRectangles_EmitsRowSegments проверь это утверждение.");
        assertEquals(
                "[1,0,1]",
                json.get(2).toString(),
                "Следующая строка обязана дать свой отрезок с номером z: " + json
                        + " Иначе тест runs_TwoDisjointRectangles_EmitsRowSegments перестанет ловить эту поломку. При смене runs_TwoDisjointRectangles_EmitsRowSegments проверь это утверждение.");
    }
}

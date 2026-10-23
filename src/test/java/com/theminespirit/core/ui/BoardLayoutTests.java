package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BoardLayoutTests {
    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"854, 480", "1280, 720", "1920, 1030"})
    void columns_EveryScreenSize_StayApartInsideWindow(int screenWidth, int screenHeight) {
        BoardLayout layout = BoardLayout.of(screenWidth, screenHeight);
        BoardLayout.Rect window = layout.window();
        List<BoardLayout.Rect> columns = layout.columns();

        assertEquals(
                BoardLayout.COLUMN_COUNT,
                columns.size(),
                "Доска обязана раскладываться в четыре колонки при любом размере окна");
        assertEquals(
                screenWidth - 2 * BoardLayout.MARGIN,
                window.width(),
                "Окно обязано занимать всю ширину экрана с одинаковыми отступами слева и справа");
        int width = columns.get(0).width();
        for (int i = 0; i < columns.size(); i++) {
            BoardLayout.Rect column = columns.get(i);
            assertEquals(width, column.width(), "Колонки обязаны быть равной ширины: иначе плитки разъезжаются");
            assertTrue(
                    column.x() >= window.x() && column.right() <= window.right(),
                    "Колонка " + i + " обязана лежать внутри окна: иначе заголовок и плитки уходят за рамку");
            assertTrue(
                    column.y() >= window.y() && column.bottom() <= window.bottom(),
                    "Колонка " + i + " обязана лежать внутри окна по высоте");
            if (i > 0) {
                BoardLayout.Rect previous = columns.get(i - 1);
                assertTrue(
                        previous.right() <= column.x(),
                        "Колонки " + (i - 1) + " и " + i
                                + " обязаны не перекрываться: иначе плитки наезжают друг на друга");
            }
        }
    }

    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"854, 480", "1280, 720", "1920, 1030"})
    void tiles_EveryColumn_StayInsideTheirColumn(int screenWidth, int screenHeight) {
        BoardLayout layout = BoardLayout.of(screenWidth, screenHeight);
        for (int column = 0; column < BoardLayout.COLUMN_COUNT; column++) {
            BoardLayout.Rect area = layout.columns().get(column);
            for (int index = 0; index < 3; index++) {
                BoardLayout.Rect tile = layout.tile(column, index);
                assertEquals(area.x(), tile.x(), "Плитка обязана начинаться у левого края своей колонки");
                assertEquals(area.width(), tile.width(), "Плитка обязана занимать всю ширину своей колонки");
                assertEquals(area.right(), tile.right(), "Плитка не обязана вылезать в соседнюю колонку");
                assertTrue(
                        tile.y() >= area.y() && tile.bottom() <= area.bottom(),
                        "Плитка " + index + " колонки " + column + " обязана лежать в области списка: иначе она "
                                + "рисуется поверх заголовка или за низом окна");
            }
        }
    }

    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"854, 480", "1280, 720", "1920, 1030"})
    void headers_EveryScreenSize_StayInsideWindow(int screenWidth, int screenHeight) {
        BoardLayout layout = BoardLayout.of(screenWidth, screenHeight);
        BoardLayout.Rect window = layout.window();
        List<BoardLayout.Rect> headers = layout.headers();
        List<BoardLayout.Rect> columns = layout.columns();

        assertEquals(
                BoardLayout.COLUMN_COUNT, headers.size(), "У каждой из четырёх колонок обязан быть свой заголовок");
        for (int i = 0; i < headers.size(); i++) {
            BoardLayout.Rect header = headers.get(i);
            assertTrue(
                    header.x() >= window.x() && header.right() <= window.right(),
                    "Заголовок " + i + " обязан целиком помещаться в окне по ширине: иначе он обрезан");
            assertTrue(
                    header.y() >= window.y() && header.bottom() <= window.bottom(),
                    "Заголовок " + i + " обязан целиком помещаться в окне по высоте");
            assertTrue(
                    header.bottom() <= layout.list().y(),
                    "Заголовок " + i + " обязан стоять над списком своей колонки, а не поверх плиток");
            assertEquals(columns.get(i).x(), header.x(), "Заголовок обязан стоять ровно над своей колонкой");
            assertEquals(columns.get(i).width(), header.width(), "Ширина заголовка обязана совпадать с колонкой");
        }
    }
}

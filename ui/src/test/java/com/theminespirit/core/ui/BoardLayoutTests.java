package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
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
                "Доска обязана раскладываться ровно в четыре колонки при любом размере окна: иначе группы заданий "
                        + "смешаются, а прокрутка одной колонки сдвинет соседнюю. При смене COLUMN_COUNT проверь "
                        + "число и ширину колонок в этом же тесте");
        assertEquals(
                screenWidth - 2 * BoardLayout.MARGIN,
                window.width(),
                "Окно доски обязано занимать всю ширину экрана с равными отступами: иначе крайняя колонка "
                        + "обрежется или появится полоса фона, а плитки уедут за рамку. При смене MARGIN или "
                        + "расчёта window проверь координаты и ширину колонок здесь же");
        int width = columns.get(0).width();
        for (int i = 0; i < columns.size(); i++) {
            BoardLayout.Rect column = columns.get(i);
            assertEquals(
                    width,
                    column.width(),
                    "Колонки обязаны быть равной ширины: иначе плитки в узкой колонке обрезаются, а в широкой "
                            + "текст задания оставляет пустую полосу. При смене формулы columnWidth проверь "
                            + "равенство ширин и отсутствие наложений в этом же тесте");
            assertTrue(
                    column.x() >= window.x() && column.right() <= window.right(),
                    "Колонка " + i + " обязана лежать внутри окна: иначе заголовок и плитки уходят за рамку "
                            + "доски и их не видно. При смене BORDER_LEFT/BORDER_RIGHT или MARGIN проверь "
                            + "левую границу первой колонки и правую границу последней");
            assertTrue(
                    column.y() >= window.y() && column.bottom() <= window.bottom(),
                    "Колонка " + i + " обязана лежать внутри окна по высоте: иначе список заданий вылезает под "
                            + "кнопки фильтров или за нижний край экрана. При смене BORDER_TOP/BORDER_BOTTOM "
                            + "проверь верх первой колонки и низ последней");
            if (i > 0) {
                BoardLayout.Rect previous = columns.get(i - 1);
                assertTrue(
                        previous.right() <= column.x(),
                        "Колонки " + (i - 1) + " и " + i + " обязаны не перекрываться: иначе плитки наезжают "
                                + "друг на друга, а клик по карточке попадает в соседнюю группу. При смене "
                                + "COLUMN_GAP проверь зазор между соседними колонками здесь же");
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
                assertEquals(
                        area.x(),
                        tile.x(),
                        "Плитка обязана начинаться у левого края своей колонки: иначе она заезжает на соседнюю "
                                + "группу и её полоса-статус читается не там. При смене tile() или CONTENT_PAD "
                                + "проверь x плитки и x её колонки");
                assertEquals(
                        area.width(),
                        tile.width(),
                        "Плитка обязана занимать всю ширину своей колонки: иначе текст и значки ресурсов "
                                + "обрежутся раньше срока, а подсказка наведётся мимо карточки. При смене расчёта "
                                + "колонки проверь ширину плиток во всех четырёх колонках");
                assertEquals(
                        area.right(),
                        tile.right(),
                        "Правый край плитки обязан совпадать с правым краем колонки: иначе значок «+N» и "
                                + "последняя иконка уезжают за карточку. При смене ширины колонки или отступов "
                                + "плитки проверь right() плитки и колонки");
                assertTrue(
                        tile.y() >= area.y() && tile.bottom() <= area.bottom(),
                        "Плитка " + index + " колонки " + column + " обязана лежать в области списка: иначе она "
                                + "рисуется поверх заголовка или за низом окна, и её нельзя прокрутить. При "
                                + "смене TILE_HEIGHT, TILE_GAP или высоты списка проверь низ третьей плитки");
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
                BoardLayout.COLUMN_COUNT,
                headers.size(),
                "У каждой колонки обязан быть свой заголовок: иначе группа заданий остаётся без подписи, и "
                        + "игрок путает «Ждут» с «Доступны». При смене COLUMN_COUNT проверь число заголовков "
                        + "и их цвета в BoardColumnView");
        for (int i = 0; i < headers.size(); i++) {
            BoardLayout.Rect header = headers.get(i);
            assertTrue(
                    header.x() >= window.x() && header.right() <= window.right(),
                    "Заголовок " + i + " обязан помещаться в окне по ширине: иначе он обрезан, и счётчик "
                            + "группы не виден. При смене MARGIN или ширины колонки проверь левую и правую "
                            + "границы заголовка");
            assertTrue(
                    header.y() >= window.y() && header.bottom() <= window.bottom(),
                    "Заголовок " + i + " обязан помещаться в окне по высоте: иначе он наезжает на верхнюю "
                            + "рамку доски или уходит под кнопки. При смене BORDER_TOP или COLUMN_HEADER "
                            + "проверь верх и низ заголовка");
            assertTrue(
                    header.bottom() <= layout.list().y(),
                    "Заголовок " + i + " обязан стоять над списком своей колонки: иначе он рисуется поверх "
                            + "первой карточки, и клик по заголовку перекрывает задание. При смене "
                            + "COLUMN_HEADER или CONTENT_PAD проверь стык заголовка и списка");
            assertEquals(
                    columns.get(i).x(),
                    header.x(),
                    "Заголовок обязан стоять ровно над своей колонкой: иначе подпись группы сдвинута, и игрок "
                            + "не понимает, к какой колонке она относится. При смене расчёта x колонок проверь "
                            + "x заголовка в этом же тесте");
            assertEquals(
                    columns.get(i).width(),
                    header.width(),
                    "Ширина заголовка обязана совпадать с колонкой: иначе фон заголовка не покрывает группу "
                            + "целиком и граница колонки читается криво. При смене columnWidth проверь ширину "
                            + "заголовка и колонки здесь же");
        }
    }

    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"854, 480", "1280, 720", "1920, 1030"})
    void stage_EveryScreenSize_SitsAboveButtonsAndWindow(int screenWidth, int screenHeight) {
        BoardLayout layout = BoardLayout.of(screenWidth, screenHeight);
        BoardLayout.Rect stage = layout.stage();
        BoardLayout.Rect window = layout.window();

        assertEquals(
                window.x(),
                stage.x(),
                "Надпись этапа обязана стоять по краю окна, а не уезжать вбок: иначе она не совпадает с рамкой "
                        + "доски и выглядит отдельным элементом. При смене MARGIN или stage() проверь x этапа");
        assertEquals(
                window.width(),
                stage.width(),
                "Надпись этапа обязана занимать всю ширину окна: иначе длинный текст этапа обрезается раньше "
                        + "края и игрок не видит, какой шаг идёт. При смене расчёта stage() проверь ширину этапа");
        assertTrue(
                stage.y() >= BoardLayout.MARGIN,
                "Надпись этапа обязана быть в самом верху экрана, но не под обрезом: иначе строка этапа "
                        + "пропадает за краем монитора. При смене MARGIN или STAGE_HEIGHT проверь y этапа");
        assertTrue(
                stage.bottom() <= layout.statusButton().y(),
                "Надпись этапа обязана стоять над кнопками фильтров: иначе она наезжает на них, и клик по "
                        + "кнопке перекрыт текстом. При смене STAGE_HEIGHT или BUTTON_HEIGHT проверь зазор "
                        + "между этапом и кнопками");
        assertTrue(
                stage.bottom() <= window.y(),
                "Надпись этапа обязана стоять над окном доски: иначе она попадает на рамку или внутрь списка "
                        + "заданий и путается с карточками. При смене BUTTON_GAP или BORDER_TOP проверь низ "
                        + "этапа и верх окна");
    }

    @Test
    void tile_FiveTextLines_FitIntoHeight() {
        int lines = 5;
        int needed = BoardLayout.TILE_TEXT_TOP + lines * BoardLayout.TILE_LINE_STEP;

        assertTrue(
                BoardLayout.TILE_HEIGHT >= needed,
                "Плитка несёт пять строк (задача, исполнитель, начало/конец, затрата, причина), и их высота "
                        + needed + " не должна превышать TILE_HEIGHT " + BoardLayout.TILE_HEIGHT
                        + ": иначе последняя строка обрезается, и причина ожидания пропадает с карточки. "
                        + "При смене TILE_LINE_STEP или TILE_TEXT_TOP проверь этот лимит и tile() в этом классе");
    }

    @Test
    void iconGrid_NoMaterials_IsEmpty() {
        BoardLayout.IconGrid grid = BoardLayout.iconGrid(200, 0);

        assertEquals(
                0,
                grid.capacity(),
                "Задание без ресурсов не рисует ни одного значка: иначе пустая сетка резервирует место справа "
                        + "и обрезает название задания без причины. При смене iconGrid проверь нулевую вместимость");
        assertEquals(
                0,
                grid.width(),
                "Пустая сетка значков не отнимает ширину у текста: иначе карточка без ресурсов показывает "
                        + "обрезанное название и пустое поле справа. При смене расчёта textWidth в BoardTileView "
                        + "проверь нулевую ширину сетки");
    }

    @Test
    void iconGrid_FewMaterials_FitEveryIcon() {
        BoardLayout.IconGrid grid = BoardLayout.iconGrid(200, 3);

        assertEquals(
                3,
                grid.columns(),
                "Мало предметов — каждый значок стоит в своём столбце: иначе иконки сжимаются в лишние ряды и "
                        + "числа налезают друг на друга. При смене iconGrid проверь столбцы для трёх ресурсов");
        assertTrue(
                grid.capacity() >= 3,
                "Три значка обязаны поместиться целиком, без сокращения в «+N»: иначе на маленьком задании "
                        + "пропадает ресурс, и дух выглядит готовым к работе. При смене числа рядов проверь "
                        + "вместимость сетки");
        assertTrue(
                grid.width() <= 200 / 2,
                "Значки не должны заслонять название задания: им отдана не больше половины карточки, иначе "
                        + "игрок не понимает, что за работа. При смене ограничения blockLimit проверь ширину "
                        + "блока значков");
    }

    @Test
    void iconGrid_ManyMaterials_UseRowsAndLeaveOverflow() {
        BoardLayout.IconGrid grid = BoardLayout.iconGrid(200, 30);

        assertEquals(
                2,
                grid.rows(),
                "Карточка вмещает два ряда значков, над каждым стоит число: иначе подпись ложится на иконку "
                        + "или вылезает за карточку. При смене ICON_LABEL или TILE_HEIGHT проверь ряды и место "
                        + "метки над значком");
        assertTrue(
                grid.capacity() < 30,
                "Лишние значки сворачиваются в «+N», а не пропадают молча: иначе игрок не знает, что заданию "
                        + "нужно больше ресурсов, и не догадается открыть подсказку. При смене вместимости "
                        + "проверь «+N» и полный список в tooltip карточки");
        assertTrue(
                grid.width() <= 200 / 2,
                "Даже при обилии ресурсов значки не заслоняют название задания: иначе карточка превращается "
                        + "в палитру иконок без смысла работы. При смене blockLimit проверь ширину блока при "
                        + "тридцати предметах");
    }

    @Test
    void iconGrid_NarrowTile_ShowsSingleColumn() {
        BoardLayout.IconGrid grid = BoardLayout.iconGrid(20, 5);

        assertEquals(
                1,
                grid.columns(),
                "В узкой карточке значок стоит один в ряд: иначе расчёт уходит в минус и сетка ломается, "
                        + "а иконки рисуются вне карточки. При смене blockLimit проверь столбцы для ширины 20");
        assertEquals(
                BoardLayout.ICON_SIZE,
                grid.width(),
                "Значок обязан целиком влезть в узкую карточку: иначе он обрезается правым краем, и число "
                        + "над иконкой не читается. При смене ICON_SIZE проверь ширину сетки в этом тесте");
    }
}

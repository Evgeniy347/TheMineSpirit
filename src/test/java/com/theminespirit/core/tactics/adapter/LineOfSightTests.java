package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.tactics.Point;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LineOfSightTests {
    private static final Point FROM = new Point(0.5, 2.6, 0.5);

    record Row(String name, Consumer<FakeTerrain> build, Point to, int budget, Visibility expected) {}

    static Stream<Row> rows() {
        Point far = new Point(0.5, 2.6, 8.5);
        return Stream.of(
                new Row("пустое поле", terrain -> {}, far, 100, Visibility.CLEAR),
                new Row("стена высотой два на пути", terrain -> terrain.wall(0, 4, 2), far, 100, Visibility.BLOCKED),
                new Row(
                        "столб высотой один ниже линии взгляда",
                        terrain -> terrain.wall(0, 4, 1),
                        far,
                        100,
                        Visibility.CLEAR),
                new Row("стена в стороне от луча", terrain -> terrain.wall(3, 4, 2), far, 100, Visibility.CLEAR),
                new Row(
                        "вода на пути не закрывает обзор",
                        terrain -> terrain.set(0, 2, 4, Terrain.WATER),
                        far,
                        100,
                        Visibility.CLEAR),
                new Row("чанк на пути не загружен", terrain -> terrain.unloaded(0, 4), far, 100, Visibility.UNKNOWN),
                new Row("бюджета не хватило на весь луч", terrain -> {}, far, 3, Visibility.UNKNOWN),
                new Row(
                        "соседние клетки: читать нечего",
                        terrain -> {},
                        new Point(0.5, 2.6, 1.2),
                        0,
                        Visibility.CLEAR));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void trace_Terrain_GivesExpectedVisibility(Row row) {
        FakeTerrain terrain = FakeTerrain.flat();
        row.build().accept(terrain);
        CellReader reader = new CellReader(terrain, new CellCache(), new ReadBudget(row.budget()), 0);

        Visibility visibility = LineOfSight.trace(FROM, row.to(), reader);

        assertEquals(
                row.expected(),
                visibility,
                "Видимость луча («" + row.name() + "»): стена на высоте глаз закрывает обзор, "
                        + "лишение бюджета или незагруженный чанк — UNKNOWN, а не догадка");
    }
}

package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Point;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EnvScannerTests {
    private static final Point SELF = new Point(0.5, 1, 0.5);
    private static final BlockCoord FEET = new BlockCoord(0, 1, 0);
    private static final Point THREAT = new Point(0.5, 1, -9.5);
    private static final int BUDGET = TacticsAdapterSettings.defaults().factChunkBudget();
    private static final int BIG_BUDGET = 5000;

    interface Check {
        void verify(String name, EnvReading reading);
    }

    record Row(String name, Consumer<FakeTerrain> build, Optional<Point> threat, int budget, int cycles, Check check) {
    }

    static Stream<Row> rows() {
        Optional<Point> threat = Optional.of(THREAT);
        return Stream.of(
                new Row("ровное поле: ничего нет", terrain -> {
                }, threat, BIG_BUDGET, 1, (name, r) -> {
                    assertEquals(EnvFacts.FAR_DISTANCE, r.lavaDistance(), "Лавы нет («" + name + "»): расстояние — FAR");
                    assertEquals(EnvFacts.FAR_DISTANCE, r.waterDistance(), "Воды нет («" + name + "»): расстояние — FAR");
                    assertEquals(EnvFacts.FAR_DISTANCE, r.coverDistance(), "Укрытия на ровном поле нет («" + name + "»)");
                    assertEquals(0.0, r.dropHeight(), "Обрыва на ровном поле нет («" + name + "»)");
                }),
                new Row("лава в 3 блоках", terrain -> terrain.set(3, 0, 0, Terrain.LAVA), threat, BIG_BUDGET, 1,
                        (name, r) -> assertEquals(3.2, r.lavaDistance(), 0.1,
                                "Расстояние до лавы («" + name + "») считается до центра клетки под ногами: ждали около 3.2")),
                new Row("вода в 5 блоках", terrain -> terrain.set(0, 0, 5, Terrain.WATER), threat, BIG_BUDGET, 1,
                        (name, r) -> {
                            assertEquals(Optional.of(new BlockCoord(0, 0, 5)), r.water(),
                                    "Клетка воды («" + name + "») — водная клетка под уровнем ног");
                            assertEquals(5.1, r.waterDistance(), 0.1,
                                    "Расстояние до воды («" + name + "») считается до центра клетки под ногами: ждали около 5.1, получили " + r.waterDistance());
                        }),
                new Row("две лужи: берётся ближняя", terrain -> terrain.set(0, 0, 6, Terrain.WATER).set(2, 0, 2, Terrain.WATER),
                        threat, BIG_BUDGET, 1, (name, r) -> assertEquals(Optional.of(new BlockCoord(2, 0, 2)), r.water(),
                                "Из двух водоёмов («" + name + "») цель — ближний: дух не бежит дальше нужного")),
                new Row("обрыв рядом с ногами", terrain -> {
                    for (int y = -4; y <= 0; y++) {
                        terrain.set(1, y, 0, Terrain.AIR);
                    }
                }, threat, BIG_BUDGET, 1, (name, r) -> assertEquals(5.0, r.dropHeight(), "Глубина обрыва («" + name
                        + "»): пять пустых клеток под соседней колонкой")),
                new Row("пропасть глубже проверки обрезается", terrain -> {
                    for (int y = -40; y <= 0; y++) {
                        terrain.set(-1, y, 0, Terrain.AIR);
                    }
                }, threat, BIG_BUDGET, 1, (name, r) -> assertEquals((double) EnvScanner.MAX_DROP_PROBE, r.dropHeight(),
                        "Глубина («" + name + "») не больше MAX_DROP_PROBE: чтение шахты не должно расти без предела")),
                new Row("стена между духом и врагом в 3 блоках: укрытие за стеной", terrain ->
                        terrain.wall(0, -3, 2).wall(1, -3, 2).wall(-1, -3, 2), threat, BIG_BUDGET, 1, (name, r) -> {
                    assertTrue(r.cover().isPresent(), "Укрытие («" + name + "») обязано найтись за стеной");
                    assertTrue(r.cover().get().z() > -3,
                            "Клетка укрытия («" + name + "») лежит за стеной со стороны духа, а не врага");
                }),
                new Row("стена за спиной духа: укрытие по ту сторону стены, дальше неё", terrain ->
                        terrain.wall(0, 5, 2).wall(1, 5, 2).wall(-1, 5, 2), threat, BIG_BUDGET, 1,
                        (name, r) -> assertTrue(r.cover().isPresent() && r.cover().get().z() > 5,
                                "Стена за спиной духа («" + name + "») прячет только тех, кто стоит за ней: клетка укрытия обязана лежать дальше стены, получили " + r.cover())),
                new Row("низкий столб в один блок не укрытие", terrain -> terrain.wall(0, -3, 1), threat, BIG_BUDGET, 1,
                        (name, r) -> assertTrue(r.cover().isEmpty(),
                                "Блок высотой один не закрывает духа («" + name + "»): укрытие — стена высотой два")),
                new Row("дух уже за стеной: расстояние до укрытия ноль", terrain ->
                        terrain.wall(0, -2, 2).wall(1, -2, 2).wall(-1, -2, 2), threat, BIG_BUDGET, 1,
                        (name, r) -> assertEquals(0.0, r.coverDistance(),
                                "Если врага уже не видно («" + name + "»), укрытие — прямо тут")),
                new Row("враг неизвестен: укрытие не ищется", terrain -> terrain.wall(0, -3, 2), Optional.empty(), BIG_BUDGET, 1,
                        (name, r) -> assertTrue(r.cover().isEmpty(),
                                "Без врага («" + name + "») от кого прятаться — неизвестно")),
                new Row("вода в незагруженном чанке пропускается", terrain ->
                        terrain.set(0, 0, 5, Terrain.WATER).unloaded(0, 5), threat, BIG_BUDGET, 1,
                        (name, r) -> assertTrue(r.water().isEmpty(),
                                "Незагруженную клетку («" + name + "») нельзя читать как воду: пропуск, а не догадка")),
                new Row("бюджет 64 режет скан и помечает это", terrain -> {
                }, threat, BUDGET, 1, (name, r) -> {
                    assertTrue(r.cutByBudget(),
                            "Скан поля радиуса 8 не умещается в 64 чтения («" + name + "»): срез обязан быть помечен");
                    assertTrue(r.reads() <= BUDGET,
                            "Чтений за цикл («" + name + "») не больше бюджета " + BUDGET + ", было " + r.reads());
                }),
                new Row("лужа 2x2 в 7 блоках находится за три цикла по 64 чтения", terrain ->
                        terrain.set(6, 0, 0, Terrain.WATER).set(7, 0, 0, Terrain.WATER).set(6, 0, 1, Terrain.WATER).set(7, 0, 1, Terrain.WATER),
                        threat, BUDGET, 3, (name, r) -> assertTrue(r.water().isPresent() && r.water().get().x() >= 6,
                                "Кэш клеток копит скан («" + name + "»): за три цикла по 64 чтения лужа в 7 блоках обязана найтись, получили " + r.water())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void scan_Terrain_GivesExpectedFacts(Row row) {
        FakeTerrain terrain = FakeTerrain.flat();
        row.build().accept(terrain);
        EnvScanner scanner = new EnvScanner();
        EnvReading reading = null;
        for (int cycle = 0; cycle < row.cycles(); cycle++) {
            reading = scanner.scan(SELF, FEET, row.threat(), terrain, new ReadBudget(row.budget()), cycle * 10L);
        }

        row.check().verify(row.name(), reading);
    }

    @Test
    void scan_SecondCycleOnSameCells_ReadsNothingNew() {
        FakeTerrain terrain = FakeTerrain.flat();
        EnvScanner scanner = new EnvScanner();
        scanner.scan(SELF, FEET, Optional.of(THREAT), terrain, new ReadBudget(BIG_BUDGET), 0);
        int afterFirst = terrain.reads();

        EnvReading second = scanner.scan(SELF, FEET, Optional.of(THREAT), terrain, new ReadBudget(BIG_BUDGET), 10);

        assertEquals(afterFirst, terrain.reads(),
                "Кэш клеток держит скан дешёвым: повтор через 10 тиков по тем же клеткам не читает мир заново");
        assertEquals(0, second.reads(), "Чтений во втором цикле нет: все клетки свежие в кэше");
    }

    @Test
    void scan_AfterCacheTtl_ReadsWorldAgain() {
        FakeTerrain terrain = FakeTerrain.flat();
        EnvScanner scanner = new EnvScanner();
        scanner.scan(SELF, FEET, Optional.of(THREAT), terrain, new ReadBudget(BIG_BUDGET), 0);
        int afterFirst = terrain.reads();

        scanner.scan(SELF, FEET, Optional.of(THREAT), terrain, new ReadBudget(BIG_BUDGET), CellCache.TTL_TICKS);

        assertTrue(terrain.reads() > afterFirst, "Мир меняется: после TTL " + CellCache.TTL_TICKS
                + " тиков клетки читаются заново, иначе дух бежал бы к воде, которой уже нет");
    }
}

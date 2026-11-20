package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ProtectedZones.ProtectedZone;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtectedZonesTests {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final BlockBox BOX = new BlockBox(new BlockCoord(-5, 60, 10), new BlockCoord(20, 80, 30));

    @Test
    void contains_AllEightCorners_AreInside() {
        ProtectedZones zones = ProtectedZones.of(List.of(new ProtectedZone(OVERWORLD, BOX)));

        for (int x : new int[] {-5, 20}) {
            for (int y : new int[] {60, 80}) {
                for (int z : new int[] {10, 30}) {
                    assertTrue(
                            zones.contains(OVERWORLD, new BlockCoord(x, y, z)),
                            "Угол коробки " + x + " " + y + " " + z
                                    + " входит в зону: границы включены, иначе по углу остаётся дыра в защите. "
                                    + "При смене contains проверь все восемь углов");
                }
            }
        }
    }

    @Test
    void contains_OneStepOutsideEachFace_IsOutside() {
        ProtectedZones zones = ProtectedZones.of(List.of(new ProtectedZone(OVERWORLD, BOX)));
        BlockCoord[] outside = {
            new BlockCoord(-6, 70, 20),
            new BlockCoord(21, 70, 20),
            new BlockCoord(5, 59, 20),
            new BlockCoord(5, 81, 20),
            new BlockCoord(5, 70, 9),
            new BlockCoord(5, 70, 31)
        };

        for (BlockCoord pos : outside) {
            assertFalse(
                    zones.contains(OVERWORLD, pos),
                    "Блок " + pos + " на шаг за гранью коробки не защищён: зона не шире, чем записано в "
                            + "конфиге, иначе защита расползается. При смене contains проверь шаг за каждой гранью");
        }
    }

    @Test
    void contains_OtherDimension_IsOutside() {
        ProtectedZones zones = ProtectedZones.of(List.of(new ProtectedZone(OVERWORLD, BOX)));

        assertFalse(
                zones.contains("minecraft:the_nether", new BlockCoord(5, 70, 20)),
                "Зона принадлежит одному измерению: те же координаты в Незере не защищены, иначе разметка "
                        + "Оверворлда блокирует работу в другом мире. При смене contains проверь ключ измерения");
    }

    @Test
    void contains_OverlappingBoxes_ProtectsUnionOfBoth() {
        BlockBox other = new BlockBox(new BlockCoord(15, 60, 25), new BlockCoord(40, 80, 50));
        ProtectedZones zones =
                ProtectedZones.of(List.of(new ProtectedZone(OVERWORLD, BOX), new ProtectedZone(OVERWORLD, other)));

        assertTrue(
                zones.contains(OVERWORLD, new BlockCoord(18, 70, 28)),
                "Точка в пересечении коробок защищена, иначе общая часть зон теряется. "
                        + "При смене contains проверь пересечение двух коробок");
        assertTrue(
                zones.contains(OVERWORLD, new BlockCoord(-5, 60, 10)),
                "Точка только в первой коробке защищена, иначе вторая зона затирает первую и блоки теряют защиту. "
                        + "При смене of проверь объединение списка зон");
        assertTrue(
                zones.contains(OVERWORLD, new BlockCoord(40, 80, 50)),
                "Точка только во второй коробке защищена, иначе объединение читается как пересечение и дальняя "
                        + "зона теряется. При смене contains проверь дальний угол второй коробки");
        assertFalse(
                zones.contains(OVERWORLD, new BlockCoord(40, 80, 51)),
                "Точка вне обеих коробок не защищена, иначе зона покрывает лишнее и дух отказывается от работы "
                        + "за пределами разметки. При смене contains проверь клетку сразу за второй коробкой");
    }

    @Test
    void of_SixtyFourZones_Accepted() {
        List<ProtectedZone> list = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            list.add(new ProtectedZone(
                    OVERWORLD, new BlockBox(new BlockCoord(i * 100, 0, 0), new BlockCoord(i * 100 + 10, 10, 10))));
        }

        ProtectedZones zones = ProtectedZones.of(list);

        assertEquals(
                64,
                zones.size(),
                "Предел 64 коробок включён: ровно столько можно записать в конфиге, иначе 64-я зона молча "
                        + "теряется. При смене of проверь границу 64");
        assertTrue(
                zones.contains(OVERWORLD, new BlockCoord(6300, 5, 5)),
                "Последняя из 64 коробок работает, иначе зона принимается, но не защищает. "
                        + "При смене хранения списка проверь последний элемент");
    }

    @Test
    void of_SixtyFiveZones_ThrowsWithCount() {
        List<ProtectedZone> list = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            list.add(new ProtectedZone(OVERWORLD, BOX));
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ProtectedZones.of(list),
                "65 коробок сверх предела 64 — ошибка конфигурации, а не тихое усечение, иначе часть зон "
                        + "выпадает из защиты. При смене of проверь отказ на 65-й зоне");

        assertTrue(
                error.getMessage().startsWith("Защищённых зон 65, допустимо не больше 64"),
                "Ошибка называет число зон и предел, иначе не понять, насколько разметку сократить. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь оба числа");
    }

    @Test
    void intersects_SameDimensionBoxInsideAProtectedZone_IsReported() {
        ProtectedZones zones = ProtectedZones.of(List.of(new ProtectedZone(OVERWORLD, BOX)));
        BlockBox area = new BlockBox(new BlockCoord(0, 65, 15), new BlockCoord(5, 70, 20));

        assertTrue(
                zones.intersects(OVERWORLD, area),
                "Куб внутри защищённой зоны пересекается с ней: снятие защиты там запрещено, иначе постройки "
                        + "внутри зоны можно разобрать. При смене intersects проверь куб строго внутри");
    }

    @Test
    void intersects_OtherDimensionSameCoords_IsNotReported() {
        ProtectedZones zones = ProtectedZones.of(List.of(new ProtectedZone(OVERWORLD, BOX)));

        assertFalse(
                zones.intersects("minecraft:the_nether", BOX),
                "Зона принадлежит одному миру: те же координаты в Незере её не задевают, иначе снятие защиты "
                        + "в Незере блокируется чужой разметкой. При смене intersects проверь ключ измерения");
    }

    @Test
    void intersects_AdjacentBoxes_AreNotReportedButOverlapIs() {
        ProtectedZones zones = ProtectedZones.of(List.of(new ProtectedZone(OVERWORLD, BOX)));
        BlockBox overlap = new BlockBox(new BlockCoord(20, 60, 10), new BlockCoord(25, 65, 15));
        BlockBox adjacent = new BlockBox(new BlockCoord(21, 60, 10), new BlockCoord(25, 65, 15));

        assertTrue(
                zones.intersects(OVERWORLD, overlap),
                "Коробка, задевшая грань зоны по x=20, пересекается с ней, иначе общий блок пропускается. "
                        + "При смене intersects проверь касание гранью");
        assertFalse(
                zones.intersects(OVERWORLD, adjacent),
                "Коробка в соседнем блоке x=21 не задевает зону: пересечение — общие блоки, а не касание, "
                        + "иначе снятие защиты блокируется впустую. При смене проверь соседний блок");
    }

    @Test
    void of_ZoneListIsCopied_LaterChangesDoNotLeakIn() {
        List<ProtectedZone> list = new ArrayList<>();
        ProtectedZones zones = ProtectedZones.of(list);

        list.add(new ProtectedZone(OVERWORLD, BOX));

        assertFalse(
                zones.contains(OVERWORLD, new BlockCoord(5, 70, 20)),
                "Список защищённых зон копируется: правка исходного списка не меняет защиту втихую, иначе "
                        + "источник правды расходится с состоянием. При смене of проверь копирование списка");
    }

    @Test
    void protectedZone_BlankDimension_ThrowsWithHint() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ProtectedZone(" ", BOX),
                "пустое измерение — опечатка в конфиге, иначе зона привязывается к несуществующему миру. "
                        + "При смене проверки измерения сверь строку из пробелов");

        assertTrue(
                error.getMessage().contains("minecraft:overworld"),
                "Ошибка подсказывает формат измерения, иначе игрок не исправит разметку. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь пример minecraft:overworld");
    }
}

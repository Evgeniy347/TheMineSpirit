package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.reload.ContentSource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchematicResourcesTests {
    private static final String HOUSE = "data/theminespirit/schematics/fixture_plate.txt";

    @Test
    void load_RegisteredSchematic_ReturnsHolderCurrent() {
        assertSame(
                houseHolder().current(),
                SchematicResources.load("fixture_plate"),
                "Потребитель обязан брать тело схемы из держателя, а не из classpath-кэша: иначе /reload "
                        + "не поменяет постройку дома, хотя таблица перечитана");
    }

    @Test
    void reload_ChangedSchematic_ConsumerSeesNewSize() {
        TableHolder<Schematic> holder = houseHolder();
        Schematic original = holder.current();
        String changed = """
                size: 1x1x1
                legend:
                  's': stone
                layers:
                  - y0: |
                      s
                """;
        try {
            DataTables.INSTANCE.reload(ContentSource.of(
                    Map.of(HOUSE, new LoadedContent(changed, "test datapack", TableStatus.Source.DATAPACK))));

            assertEquals(
                    1,
                    SchematicResources.load("fixture_plate").sizeX(),
                    "После /reload потребитель обязан видеть новое тело схемы: иначе датапак мира не поменяет "
                            + "постройку без перезапуска, а схема числится горячей ложно");
            assertEquals(
                    List.of(new Schematic.Entry(0, 0, 0, "minecraft:stone")),
                    SchematicResources.load("fixture_plate").blocks(),
                    "Новое тело схемы обязано подменяться целиком, а не смешиваться со старым: блоки берутся "
                            + "из перечитанного файла");
        } finally {
            holder.replace(
                    original,
                    TableStatus.Source.MOD,
                    "classpath (мод)",
                    holder.status().hash(),
                    Clock.systemUTC());
        }
    }

    @Test
    void reload_BrokenSchematic_KeepsPreviousVersion() {
        TableHolder<Schematic> holder = houseHolder();
        Schematic original = holder.current();
        try {
            DataTables.INSTANCE.reload(ContentSource.of(
                    Map.of(HOUSE, new LoadedContent("not a schematic", "test datapack", TableStatus.Source.DATAPACK))));

            assertSame(
                    original,
                    holder.current(),
                    "Битый файл схемы не должен подменять тело: прежняя схема остаётся в силе, иначе /reload "
                            + "сломает постройку дома у игроков");
            assertTrue(
                    holder.status().failed(),
                    "Ошибка чтения схемы обязана быть видна в /spirit tables и логе, а не проглочена молча");
        } finally {
            holder.replace(
                    original,
                    TableStatus.Source.MOD,
                    "classpath (мод)",
                    holder.status().hash(),
                    Clock.systemUTC());
        }
    }

    private static TableHolder<Schematic> houseHolder() {
        return DataTables.INSTANCE
                .schematic("fixture_plate")
                .orElseThrow(() ->
                        new AssertionError("схема house не зарегистрирована в DataTables: /reload её не перечитает"));
    }
}

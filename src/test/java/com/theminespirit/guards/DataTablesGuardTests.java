package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableDefinition;
import com.theminespirit.core.reload.TableRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Сторож: каждый json-файл в data/theminespirit зарегистрирован в DataTables (или в исключениях с причиной),
 * и каждая зарегистрированная таблица существует как файл.
 *
 * <p>Правило: таблица без регистрации не перечитается на лету по /reload.
 * Цена нарушения: изменение файла в датапаке мира игнорируется, сервер работает со старой версией без уведомления.
 * Как исправить: зарегистрировать таблицу в DataTables через TableRegistry.registerLazy или добавить в исключения
 * внутри сторожа с явной причиной (например, схемы построек, если они грузятся иначе).
 */
class DataTablesGuardTests {
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/theminespirit");
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "data/theminespirit/tactics/priorities.json (orphan: не читается кодом, подлежит удалению или интеграции)",
            "data/theminespirit/schematics/family_house.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/house.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/mine_entrance_v1.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/palisade_section.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/pen_7x7.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/stone_house.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/storage_hut.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/torch_post.json (тело схемы: грузится динамически через indexed-таблицу presets)",
            "data/theminespirit/schematics/well.json (тело схемы: грузится динамически через indexed-таблицу presets)"
    );

    @Test
    void dataTables_EveryJsonFile_RegisteredOrExcluded() throws IOException {
        assertTrue(Files.isDirectory(DATA_ROOT),
                "Сторож не нашёл " + DATA_ROOT + ": тесты запускаются из корня проекта (./gradlew test)");

        List<String> jsonFiles = Files.walk(DATA_ROOT)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> "data/theminespirit/" + DATA_ROOT.relativize(p).toString().replace('\\', '/'))
                .sorted()
                .collect(Collectors.toList());

        Set<String> registeredPaths = registeredPaths();
        List<String> unregistered = new ArrayList<>();

        for (String path : jsonFiles) {
            if (!registeredPaths.contains(path) && !EXCLUDED_PATHS.stream().anyMatch(e -> e.startsWith(path + " "))) {
                unregistered.add(path);
            }
        }

        assertEquals(List.of(), unregistered,
                "JSON-файл данных не зарегистрирован в DataTables и не в исключениях: "
                        + "таблица без регистрации не перечитается на лету по /reload, изменение в датапаке мира игнорируется. "
                        + "Как исправить: зарегистрируйте таблицу в DataTables (TableRegistry.registerLazy) или добавьте в EXCLUDED_PATHS "
                        + "внутри DataTablesGuardTests с явной причиной. Правило: каждый json в data/theminespirit "
                        + "должен быть в DataTables или в исключениях. Незарегистрированные файлы");
    }

    @Test
    void dataTables_EveryRegisteredTable_ExistsAsFile() {
        Set<String> registeredPaths = registeredPaths();
        List<String> missing = new ArrayList<>();

        for (String path : registeredPaths) {
            Path filePath = Path.of("src/main/resources/" + path);
            if (!Files.isRegularFile(filePath)) {
                missing.add(path);
            }
        }

        assertEquals(List.of(), missing,
                "Зарегистрированная в DataTables таблица не существует как файл: "
                        + "загрузчик DataReload упадёт или вернёт ошибку при /reload, таблица не обновится. "
                        + "Как исправить: создайте файл по указанному пути или удалите регистрацию из DataTables. "
                        + "Правило: каждая зарегистрированная таблица обязана существовать как файл. Отсутствующие файлы");
    }

    private static Set<String> registeredPaths() {
        Set<String> paths = new HashSet<>();
        for (TableDefinition<?> def : DataTables.INSTANCE.registry().definitions()) {
            for (String path : def.resourcePaths()) {
                paths.add(path);
            }
        }
        return paths;
    }

    @Test
    void descent_WhenRegistered_ReturnsDefaultsFromJson() {
        // Доказательство: descent.json зарегистрирован в DataTables и доступен через hot-reload API
        var descentHolder = DataTables.INSTANCE.descent();
        assertEquals("descent", descentHolder.id(), "id таблицы descent должен совпадать с именем в DataTables");
        assertEquals(-58, descentHolder.current().targetY(), "целевой этаж по умолчанию из descent.json");
        assertEquals(4, descentHolder.current().torchInterval(), "интервал факелов по умолчанию из descent.json");
    }
}

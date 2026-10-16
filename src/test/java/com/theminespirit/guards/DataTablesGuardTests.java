package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableDefinition;
import com.theminespirit.core.reload.TableRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    /** Не таблицы /reload — путь и причина. Тела пресетов из индекса presets сторож берёт сам (presetBodies). */
    private static final Map<String, String> EXCLUDED = Map.of(
            "data/theminespirit/tactics/priorities.json", "не читается кодом: удалить или подключить",
            "data/theminespirit/schematics/house.json", "схема грузится по имени, не на /reload (todo: горячие схемы)",
            "data/theminespirit/schematics/fence.json", "схема доски грузится по имени, не на /reload (todo: горячие схемы)",
            "data/theminespirit/schematics/pen.json", "схема доски грузится по имени, не на /reload (todo: горячие схемы)",
            "data/theminespirit/schematics/enchant_room.json",
            "схема доски грузится по имени, не на /reload (todo: горячие схемы)",
            "data/theminespirit/hunger/hunger.json", "HungerRules читается с classpath, не на /reload (todo: горячие таблицы)",
            "data/theminespirit/mining/descent.json", "DescentData.defaults() с classpath, не на /reload (todo: горячие таблицы)",
            "data/theminespirit/mining/descent_levels.json",
            "DescentLevels с classpath, не на /reload (todo: горячие таблицы)",
            "data/theminespirit/fishing/fishing.json",
            "FishingData.load() с classpath, не на /reload (todo: горячие таблицы)");

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
        Set<String> presetBodies = presetBodies();
        List<String> unregistered = new ArrayList<>();

        for (String path : jsonFiles) {
            if (!registeredPaths.contains(path) && !EXCLUDED.containsKey(path)
                    && !presetBodies.contains(path)) {
                unregistered.add(path);
            }
        }

        assertEquals(List.of(), unregistered,
                "JSON-файл данных не зарегистрирован в DataTables и не в исключениях: "
                        + "таблица без регистрации не перечитается на лету по /reload, изменение в датапаке мира игнорируется. "
                        + "Как исправить: зарегистрируйте таблицу в DataTables (TableRegistry.registerLazy) или добавьте в EXCLUDED "
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

    private static Set<String> presetBodies() {
        String indexPath = PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json";
        return Set.copyOf(PresetLibrary.bodyPaths(JsonResource.readClasspath(indexPath), indexPath));
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
}

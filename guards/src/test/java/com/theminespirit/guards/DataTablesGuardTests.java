package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DataTablesGuardTests {
    private static final Path DATA_ROOT = SourceFiles.resource("data/theminespirit");

    private static final Map<String, String> EXCLUDED = Map.of(
            "data/theminespirit/schematics/buildings.json",
            "каталог семей построек читается один раз при регистрации команд /spirit demo: подкоманды по /reload "
                    + "не перерегистрируются, горячая таблица дала бы каталог, расходящийся с командами",
            "data/theminespirit/board/store_minimum.json",
            "настройки склада читаются на каждом входе графа, а не из датапака мира: /reload им не нужен",
            "data/theminespirit/ui/tree.json",
            "иконки и подписи вкладки древа — оформление экрана, а не данные мира: /reload мира их не меняет, "
                    + "таблица читается один раз вместе с клиентом");
    private static final String GRAPH_FRAGMENTS = "data/theminespirit/board/graph.d/";

    @Test
    void dataTables_EveryJsonFile_RegisteredOrExcluded() throws IOException {
        assertTrue(
                Files.isDirectory(DATA_ROOT),
                "Сторож не нашёл " + DATA_ROOT + ": тесты запускаются из корня проекта (./gradlew test)");

        List<String> jsonFiles;
        try (Stream<Path> stream = Files.walk(DATA_ROOT)) {
            jsonFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".txt"))
                    .map(p -> "data/theminespirit/"
                            + DATA_ROOT.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }

        Set<String> registeredPaths = registeredPaths();
        Set<String> presetBodies = presetBodies();
        List<String> unregistered = new ArrayList<>();

        for (String path : jsonFiles) {
            if (path.startsWith(GRAPH_FRAGMENTS)) {
                continue;
            }
            if (!registeredPaths.contains(path) && !EXCLUDED.containsKey(path) && !presetBodies.contains(path)) {
                unregistered.add(path);
            }
        }

        assertEquals(
                List.of(),
                unregistered,
                "Файл данных (json или текстовая постройка) не зарегистрирован в DataTables и не в исключениях: "
                        + "таблица без регистрации не перечитается на лету по /reload, изменение в датапаке мира игнорируется. "
                        + "Как исправить: зарегистрируйте таблицу в DataTables (TableRegistry.registerLazy) или добавьте в EXCLUDED "
                        + "внутри DataTablesGuardTests с явной причиной. Фрагменты board/graph.d/* читает "
                        + "GraphFragments вместе с graph.json, поэтому DataTables их не регистрирует. "
                        + "Правило: каждый json в data/theminespirit должен быть в DataTables или в исключениях. "
                        + "Незарегистрированные файлы");
    }

    @Test
    void dataTables_EveryRegisteredTable_ExistsAsFile() {
        Set<String> registeredPaths = registeredPaths();
        List<String> missing = new ArrayList<>();

        for (String path : registeredPaths) {
            Path filePath = SourceFiles.resource(path);
            if (!Files.isRegularFile(filePath)) {
                missing.add(path);
            }
        }

        assertEquals(
                List.of(),
                missing,
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

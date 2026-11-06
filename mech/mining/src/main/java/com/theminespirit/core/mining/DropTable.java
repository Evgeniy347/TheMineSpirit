package com.theminespirit.core.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class DropTable {

    public static final String RESOURCE = "data/theminespirit/acquire/drops.json";

    private static final TableSlot<DropTable> SOURCE = new TableSlot<>(DropTable::load);

    private final Map<String, List<String>> sources;

    private DropTable(Map<String, List<String>> sources) {
        this.sources = sources;
    }

    public static void useSource(Supplier<DropTable> supplier) {
        SOURCE.use(supplier);
    }

    public static DropTable shared() {
        return SOURCE.current();
    }

    public static DropTable load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static DropTable parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        Map<String, List<String>> sources = new LinkedHashMap<>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String drop = names.next();
            if (drop.isBlank() || drop.indexOf('*') >= 0) {
                throw new IllegalStateException("Файл '" + origin + "': ключ '" + drop
                        + "' не является предметом: таблица описывает конкретные дропы без шаблонов и пустых имён");
            }
            List<String> blocks = JsonResource.strings(root.get(drop), origin, "дроп '" + drop + "'");
            for (String block : blocks) {
                if (block.isBlank() || block.indexOf('*') >= 0) {
                    throw new IllegalStateException("Файл '" + origin + "': блок-источник '" + block
                            + "' у дропа '" + drop + "' не является конкретным id: шаблоны в списке источников "
                            + "дали бы духу несуществующий блок добычи");
                }
            }
            sources.put(normalize(drop), List.copyOf(blocks));
        }
        if (sources.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin + "': таблица пуста: без пары «дроп → блок» "
                    + "знание не помогает acquire и файл не должен существовать");
        }
        return new DropTable(Map.copyOf(sources));
    }

    public Optional<List<String>> blocksFor(String item) {
        Objects.requireNonNull(item, "Предмет не может быть null: по нему ищется блок-источник");
        return Optional.ofNullable(sources.get(normalize(item)));
    }

    public Map<String, List<String>> entries() {
        return sources;
    }

    private static String normalize(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}

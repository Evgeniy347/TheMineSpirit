package com.theminespirit.core.building;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.LoadedContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Библиотека готовых построек (пресетов): метаданные из индекса плюс тела схем, прочитанные заранее.
 * Загрузка проверяет, что габариты в метаданных совпадают с содержимым схемы, что у постройки есть вход
 * и что материалы посчитаны из блоков. Поиск — по типу строения и этапу развития поселения.
 *
 * <p>Индекс — {@code data/theminespirit/schematics/presets.json}: порядок id пресетов; тело и метаданные каждого
 * (название, тип, этап, уровень, требование, вход, исключения сторожей) — в текстовом файле постройки
 * {@code <id>.txt} того же формата, что читает {@code build_schematic}. Тела читаются тем же {@code ContentSource},
 * поэтому датапак мира может переопределить и индекс, и любую постройку; библиотека перезагружается по /reload.
 */
public final class PresetLibrary {
    public static final String INDEX_ID = "presets";
    public static final String SCHEMATICS_ROOT = "data/theminespirit/schematics/";
    private static final int INDEX_VERSION = 2;
    private static final Set<String> ROOT_FIELDS = Set.of("version", "presets", "planned");
    private static final Set<String> PLANNED_FIELDS = Set.of("level", "name", "requires");
    private static final Set<String> ENTRANCE_SUFFIXES = Set.of("_door", "_fence_gate", "_trapdoor", "_gate");

    private final List<Preset> presets;
    private final List<PlannedLevel> planned;
    private final Map<String, Preset> byId;

    private PresetLibrary(List<Preset> presets, List<PlannedLevel> planned) {
        this.presets = List.copyOf(presets);
        Map<String, Preset> index = new LinkedHashMap<>();
        for (Preset preset : presets) {
            if (index.put(preset.id(), preset) != null) {
                throw new IllegalStateException("Пресет с id '" + preset.id()
                        + "' объявлен дважды: два тела на один id разошлись бы содержимым");
            }
        }
        this.byId = Collections.unmodifiableMap(index);
        this.planned = List.copyOf(planned);
        for (Preset preset : this.presets) {
            checkRequires("Пресета '" + preset.id() + "'", preset.requires());
        }
        for (PlannedLevel level : this.planned) {
            checkRequires("Запланированного уровня '" + level.name() + "'", level.requires());
        }
    }

    private void checkRequires(String what, Optional<String> requires) {
        if (requires.isEmpty() || byId.containsKey(requires.get())) {
            return;
        }
        throw new IllegalStateException("У " + what + " требование '" + requires.get()
                + "' ссылается на неизвестный пресет: условие доступности «предыдущий уровень построен» "
                + "не находит предыдущую постройку");
    }

    public static PresetLibrary empty() {
        return new PresetLibrary(List.of(), List.of());
    }

    public List<Preset> all() {
        return presets;
    }

    public List<PlannedLevel> planned() {
        return planned;
    }

    public Optional<Preset> byId(String id) {
        Objects.requireNonNull(id, "id пресета не может быть null: по нему ищут постройку");
        return Optional.ofNullable(byId.get(id));
    }

    public List<Preset> byType(BuildingType type) {
        Objects.requireNonNull(type, "Тип строения не может быть null: по нему ищут постройки");
        List<Preset> found = new ArrayList<>();
        for (Preset preset : presets) {
            if (preset.type() == type) {
                found.add(preset);
            }
        }
        return List.copyOf(found);
    }

    public List<Preset> byStage(int stage) {
        List<Preset> found = new ArrayList<>();
        for (Preset preset : presets) {
            if (preset.stage() == stage) {
                found.add(preset);
            }
        }
        return List.copyOf(found);
    }

    public List<Preset> byTypeAndStage(BuildingType type, int stage) {
        Objects.requireNonNull(type, "Тип строения не может быть null: по нему ищут постройки");
        List<Preset> found = new ArrayList<>();
        for (Preset preset : presets) {
            if (preset.type() == type && preset.stage() == stage) {
                found.add(preset);
            }
        }
        return List.copyOf(found);
    }

    public static List<String> bodyPaths(String indexJson, String origin) {
        JsonNode root = JsonResource.parse(indexJson, origin);
        List<JsonNode> entries = JsonResource.array(root.get("presets"), origin, "списка presets");
        List<String> paths = new ArrayList<>(entries.size());
        for (JsonNode entry : entries) {
            if (!entry.isTextual() || entry.asText().isBlank()) {
                throw new IllegalStateException("Файл '" + origin + "': пресет в индексе задаётся строкой id, "
                        + "метаданные живут в файле постройки <id>" + SchematicTextParser.EXTENSION);
            }
            paths.add(SCHEMATICS_ROOT + entry.asText() + SchematicTextParser.EXTENSION);
        }
        return List.copyOf(paths);
    }

    public static PresetLibrary parse(List<LoadedContent> contents) {
        Objects.requireNonNull(contents, "Содержимое библиотеки не может быть null: разбирать нечего");
        if (contents.isEmpty()) {
            throw new IllegalStateException("Библиотека пресетов пуста: нет даже индекса " + INDEX_ID);
        }
        LoadedContent index = contents.get(0);
        JsonNode root = JsonResource.parse(index.json(), index.origin());
        JsonResource.checkFields(root, ROOT_FIELDS, index.origin(), "индекса пресетов");
        int version = JsonResource.integer(root.get("version"), index.origin(), "поля version индекса пресетов");
        if (version != INDEX_VERSION) {
            throw new IllegalStateException("Файл '" + index.origin() + "': версия индекса " + version + ", ожидается "
                    + INDEX_VERSION + ": индекс другой версии читается иначе");
        }
        List<JsonNode> entries = JsonResource.array(root.get("presets"), index.origin(), "списка presets");
        if (entries.size() != contents.size() - 1) {
            throw new IllegalStateException("Файл '" + index.origin() + "': в индексе " + entries.size()
                    + " пресетов, а тел схем прочитано " + (contents.size() - 1)
                    + ": число ссылок и файлов обязано совпадать");
        }
        List<Preset> presets = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            presets.add(parsePreset(JsonResource.text(entries.get(i), index.origin(), "id пресета"),
                    contents.get(i + 1)));
        }
        return new PresetLibrary(presets, parsePlanned(root, index.origin()));
    }

    private static List<PlannedLevel> parsePlanned(JsonNode root, String origin) {
        JsonNode node = root.get("planned");
        if (node == null) {
            return List.of();
        }
        List<PlannedLevel> planned = new ArrayList<>();
        for (JsonNode item : JsonResource.array(node, origin, "списка planned")) {
            if (!item.isObject()) {
                throw new IllegalStateException("Файл '" + origin
                        + "': запланированный уровень обязан быть объектом с метаданными");
            }
            JsonResource.checkFields(item, PLANNED_FIELDS, origin, "запланированного уровня");
            int level = JsonResource.integer(item.get("level"), origin, "поля level запланированного уровня");
            String name = JsonResource.text(item.get("name"), origin, "поля name запланированного уровня");
            planned.add(new PlannedLevel(level, name,
                    optionalText(item.get("requires"), origin, "поля requires запланированного уровня")));
        }
        return List.copyOf(planned);
    }

    private static Preset parsePreset(String id, LoadedContent body) {
        SchematicDocument document = SchematicTextParser.parse(body.json(), body.origin());
        SchematicDocument.Header header = document.header();
        String name = header.name().orElseThrow(() -> missing(body, id, "name"));
        BuildingType type = header.type().orElseThrow(() -> missing(body, id, "type"));
        int stage = header.stage().orElseThrow(() -> missing(body, id, "stage"));
        BlockCoord entryPoint = header.entrance().orElseThrow(() -> missing(body, id, "entrance"));
        Schematic schematic = document.schematic();
        checkEntry(id, entryPoint, schematic, body.origin());
        Map<String, Integer> materials = countMaterials(schematic);
        return new Preset(id, name, stage, type, header.level(), header.requires(), schematic, entryPoint, materials);
    }

    private static IllegalStateException missing(LoadedContent body, String id, String key) {
        return new IllegalStateException("Файл '" + body.origin() + "': у пресета '" + id + "' нет ключа " + key
                + ": пресет обязан назвать себя, тип, этап и вход, иначе стратегу не из чего выбирать");
    }

    private static Optional<String> optionalText(JsonNode node, String origin, String what) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        return Optional.of(JsonResource.text(node, origin, what));
    }

    private static void checkEntry(String id, BlockCoord entry, Schematic schematic, String origin) {
        if (entry.x() < 0 || entry.y() < 0 || entry.z() < 0
                || entry.x() >= schematic.sizeX() || entry.y() >= schematic.sizeY() || entry.z() >= schematic.sizeZ()) {
            throw new IllegalStateException("Файл '" + origin + "': у пресета '" + id + "' точка входа "
                    + "[" + entry.x() + "," + entry.y() + "," + entry.z() + "] вне габаритов "
                    + schematic.sizeX() + "×" + schematic.sizeY() + "×" + schematic.sizeZ()
                    + ": через такую точку в постройку не войти");
        }
        String block = blockAt(schematic, entry);
        if (block != null && !isEntranceBlock(block)) {
            throw new IllegalStateException("Файл '" + origin + "': у пресета '" + id + "' точка входа занята блоком '"
                    + block + "': входом бывает проём, дверь или калитка, а не сплошной блок");
        }
    }

    private static boolean isEntranceBlock(String blockId) {
        String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        for (String suffix : ENTRANCE_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String blockAt(Schematic schematic, BlockCoord at) {
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dx() == at.x() && entry.dy() == at.y() && entry.dz() == at.z()) {
                return entry.block();
            }
        }
        return null;
    }

    public static Map<String, Integer> countMaterials(Schematic schematic) {
        Objects.requireNonNull(schematic, "Схема не может быть null: материалы считают из её блоков");
        Map<String, Integer> counts = new TreeMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (!SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                counts.merge(SchematicBlocks.itemOf(entry.block()), 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    public int size() {
        return presets.size();
    }

    @Override
    public String toString() {
        return "PresetLibrary[" + presets.size() + " пресетов]";
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(presets.size());
        for (Preset preset : presets) {
            ids.add(preset.id());
        }
        return List.copyOf(ids);
    }
}
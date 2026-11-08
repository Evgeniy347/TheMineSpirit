package com.theminespirit.core.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SettlementTreeIcons {
    public static final String RESOURCE = "data/theminespirit/ui/tree.json";
    private static final Set<String> ROOT_FIELDS = Set.of("root", "gate", "cases", "plans", "aliases");
    private static final Set<String> ENTRY_FIELDS = Set.of("title", "icon");
    private static final TableSlot<SettlementTreeIcons> SOURCE = new TableSlot<>(SettlementTreeIcons::load);

    public record Entry(String title, String icon) {
        public Entry {
            Objects.requireNonNull(title, "Название рамки не может быть null: его показывает подсказка");
            Objects.requireNonNull(icon, "Иконка рамки не может быть null: без неё рамка пустая");
            if (title.isBlank() || icon.isBlank()) {
                throw new IllegalArgumentException("Рамка древа пуста: название и иконка задаются непусто");
            }
            if (!icon.contains(":")) {
                throw new IllegalArgumentException(
                        "Иконка '" + icon + "' рамки '" + title + "' — не id предмета: ожидается minecraft:<имя>");
            }
        }
    }

    private final String rootIcon;
    private final String gateIcon;
    private final Map<String, Entry> cases;
    private final Map<String, Entry> plans;
    private final Map<String, BoardCase> aliases;

    private SettlementTreeIcons(
            String rootIcon,
            String gateIcon,
            Map<String, Entry> cases,
            Map<String, Entry> plans,
            Map<String, BoardCase> aliases) {
        this.rootIcon = new Entry("Корень", rootIcon).icon();
        this.gateIcon = new Entry("Ворота этапа", gateIcon).icon();
        this.cases = Map.copyOf(Objects.requireNonNull(cases, "Иконки кейсов не могут быть null"));
        this.plans = Map.copyOf(Objects.requireNonNull(plans, "Иконки построек плана не могут быть null"));
        this.aliases = Map.copyOf(Objects.requireNonNull(aliases, "Псевдонимы плана не могут быть null"));
    }

    public String rootIcon() {
        return rootIcon;
    }

    public String gateIcon() {
        return gateIcon;
    }

    public Entry caseEntry(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс постройки не может быть null: по нему ищут иконку");
        Entry entry = cases.get(boardCase.name());
        if (entry == null) {
            throw new IllegalStateException("В " + RESOURCE + " нет иконки кейса '" + boardCase.name()
                    + "': добавьте название и предмет, иначе рамка этапа останется пустой");
        }
        return entry;
    }

    public Entry planEntry(String planId) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут иконку");
        Entry entry = plans.get(planId);
        if (entry == null) {
            throw new IllegalStateException("В " + RESOURCE + " нет иконки постройки плана '" + planId
                    + "': добавьте название и предмет, иначе рамка этапа останется пустой");
        }
        return entry;
    }

    public Optional<BoardCase> alias(String planId) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут псевдоним");
        return Optional.ofNullable(aliases.get(planId));
    }

    public static SettlementTreeIcons current() {
        return SOURCE.current();
    }

    public static SettlementTreeIcons load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static SettlementTreeIcons parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "иконок древа поселения");
        return new SettlementTreeIcons(
                JsonResource.text(root.get("root"), origin, "иконки корня"),
                JsonResource.text(root.get("gate"), origin, "иконки ворот этапа"),
                entries(root.get("cases"), origin, "иконок кейсов"),
                entries(root.get("plans"), origin, "иконок построек плана"),
                aliases(root.get("aliases"), origin));
    }

    private static Map<String, BoardCase> aliases(JsonNode node, String origin) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': поле 'aliases' — не объект «постройка плана → кейс доски»");
        }
        Map<String, BoardCase> aliases = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = JsonResource.text(field.getValue(), origin, "псевдонима '" + field.getKey() + "'");
            try {
                aliases.put(field.getKey(), BoardCase.valueOf(name));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalStateException(
                        "Файл '" + origin + "': псевдоним '" + field.getKey() + "' ссылается на неизвестный кейс '"
                                + name + "'",
                        unknown);
            }
        }
        return aliases;
    }

    private static Map<String, Entry> entries(JsonNode node, String origin, String what) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin + "': " + what
                    + " — непустой объект «id → название и предмет»: пустая таблица оставит рамки без иконок");
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            JsonResource.checkFields(value, ENTRY_FIELDS, origin, "рамки '" + field.getKey() + "'");
            entries.put(
                    field.getKey(),
                    new Entry(
                            JsonResource.text(value.get("title"), origin, "названия '" + field.getKey() + "'"),
                            JsonResource.text(value.get("icon"), origin, "иконки '" + field.getKey() + "'")));
        }
        return entries;
    }
}

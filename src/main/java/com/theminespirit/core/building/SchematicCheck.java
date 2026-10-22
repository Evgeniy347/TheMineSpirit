package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Проверка текстовой постройки сторожами {@link SchematicRules} с её собственными исключениями: какие нарушения
 * не прощены и какие исключения устарели (правило уже проходит). Общая для сторожа данных, инструмента
 * {@code scripts/schematic.sh} и загрузки библиотеки — одни и те же правила везде.
 */
public final class SchematicCheck {
    public record Report(String id, List<String> unexcused, List<String> stale) {
        public Report {
            unexcused = List.copyOf(unexcused);
            stale = List.copyOf(stale);
        }

        public boolean ok() {
            return unexcused.isEmpty() && stale.isEmpty();
        }

        public List<String> problems() {
            List<String> all = new ArrayList<>(unexcused);
            all.addAll(stale);
            return List.copyOf(all);
        }
    }

    // Условие repeat до мирового y проверяется вне игры от номинальной поверхности: иначе сторожа схемы
    // не увидели бы ни одной повторённой ступени.
    static final int AUDIT_TOP_Y = 64;

    private SchematicCheck() {}

    public static Report check(String id, SchematicDocument document) {
        Objects.requireNonNull(id, "Id постройки не может быть null: по нему называют нарушителя");
        Objects.requireNonNull(document, "Постройка не может быть null: проверять нечего");
        boolean descending = document.schematic().repeat() != null
                && document.schematic().repeat().descends();
        document = document.expanded(AUDIT_TOP_Y);
        SchematicDocument.Header header = document.header();
        if (header.type().isEmpty()) {
            List<String> stale = new ArrayList<>();
            for (SchematicDocument.Exemption exemption : document.exemptions()) {
                stale.add(id + "|" + exemption.rule().id() + ": исключение у постройки без type — правила к ней "
                        + "не применяются, исключение лишнее");
            }
            return new Report(id, List.of(), stale);
        }
        if (header.entrance().isEmpty()) {
            return new Report(
                    id,
                    List.of(id + ": у постройки с type нет entrance — вход обязателен (front или x,y,z)"),
                    List.of());
        }
        SchematicRules.Structure structure = new SchematicRules.Structure(
                id,
                header.type().orElseThrow(),
                document.schematic(),
                header.entrance().orElseThrow(),
                header.level());
        Set<SchematicRules.RuleId> excused = EnumSet.noneOf(SchematicRules.RuleId.class);
        for (SchematicDocument.Exemption exemption : document.exemptions()) {
            excused.add(exemption.rule());
        }
        Set<SchematicRules.RuleId> used = EnumSet.noneOf(SchematicRules.RuleId.class);
        List<String> unexcused = new ArrayList<>();
        List<SchematicRules.Violation> violations = header.type().orElseThrow() == BuildingType.UNDERGROUND
                ? SchematicUndergroundRules.check(structure, descending)
                : SchematicRules.check(structure);
        for (SchematicRules.Violation violation : violations) {
            if (excused.contains(violation.rule())) {
                used.add(violation.rule());
            } else {
                unexcused.add(id + "|" + violation.rule().id() + ": " + violation.message());
            }
        }
        List<String> stale = new ArrayList<>();
        for (SchematicRules.RuleId rule : excused) {
            if (!used.contains(rule)) {
                stale.add(id + "|" + rule.id() + ": исключение устарело — правило уже проходит, уберите его из файла");
            }
        }
        stale.sort(String::compareTo);
        return new Report(id, unexcused, stale);
    }
}

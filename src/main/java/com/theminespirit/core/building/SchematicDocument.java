package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Текстовый файл постройки целиком: тело схемы, шапка (метаданные пресета) и исключения сторожей с причинами.
 * Один файл — единственный источник правды о постройке: габариты, вход и материалы выводятся из него же.
 */
public record SchematicDocument(Schematic schematic, Header header, List<Exemption> exemptions) {
    public record Header(Optional<String> name, Optional<BuildingType> type, Optional<Integer> stage, int level,
            Optional<String> requires, Optional<BlockCoord> entrance) {
        public static final Header EMPTY = new Header(Optional.empty(), Optional.empty(), Optional.empty(),
                Preset.LEVEL_NONE, Optional.empty(), Optional.empty());

        public Header {
            Objects.requireNonNull(name, "Название не может быть null: отсутствие задают Optional.empty()");
            Objects.requireNonNull(type, "Тип не может быть null: отсутствие задают Optional.empty()");
            Objects.requireNonNull(stage, "Этап не может быть null: отсутствие задают Optional.empty()");
            Objects.requireNonNull(requires, "Требование не может быть null: отсутствие задают Optional.empty()");
            Objects.requireNonNull(entrance, "Вход не может быть null: отсутствие задают Optional.empty()");
        }
    }

    public record Exemption(SchematicRules.RuleId rule, String reason) {
        public Exemption {
            Objects.requireNonNull(rule, "Правило исключения не может быть null: без него неизвестно, что прощается");
            Objects.requireNonNull(reason, "Причина исключения не может быть null: без неё правило ослабляется молча");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("Причина исключения правила '" + rule.id()
                        + "' пуста: без причины никто не знает, почему постройка не соответствует требованию");
            }
        }
    }

    public SchematicDocument {
        Objects.requireNonNull(schematic, "Схема не может быть null: в файле постройки нечего строить");
        Objects.requireNonNull(header, "Шапка не может быть null: пустую шапку задают Header.EMPTY");
        Objects.requireNonNull(exemptions, "Исключения не могут быть null: без исключений — пустой список");
        exemptions = List.copyOf(exemptions);
    }
}

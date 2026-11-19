package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.api.ToolsConfig;
import com.theminespirit.core.inventory.api.JsonResource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class ToolsConfigJson {
    private static final Set<String> FIELDS = Set.of("set", "tier", "repairTiers");

    static ToolsConfig fromJson(@Nullable JsonNode node, String origin) {
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение об ошибке называет файл");
        if (node == null || node.isNull()) {
            return ToolsConfig.defaults();
        }
        if (!node.isObject()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': поле 'tools' — не объект, ожидается " + "«set → tier → repairTiers»");
        }
        JsonResource.checkFields(node, FIELDS, origin, "поля 'tools'");
        List<String> set = node.has("set")
                ? JsonResource.strings(node.get("set"), origin, "поля 'tools.set'")
                : ToolsConfig.defaults().set();
        String tier = node.has("tier")
                ? JsonResource.text(node.get("tier"), origin, "поля 'tools.tier'")
                : ToolsConfig.defaults().tier();
        List<String> repairTiers = node.has("repairTiers")
                ? JsonResource.strings(node.get("repairTiers"), origin, "поля 'tools.repairTiers'")
                : ToolsConfig.defaults().repairTiers();
        try {
            return new ToolsConfig(set, tier, repairTiers);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private ToolsConfigJson() {}
}

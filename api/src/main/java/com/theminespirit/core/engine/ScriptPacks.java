package com.theminespirit.core.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * lib: сборка набора скриптов из наборов механик. Один id обязан принадлежать ровно одному набору:
 * молчаливая перезапись оставляла бы работающим старый скрипт из чужой механики.
 */
public final class ScriptPacks {
    private ScriptPacks() {}

    public static Set<String> ids(Collection<? extends ScriptPack<?>> packs) {
        Objects.requireNonNull(packs, "packs не может быть null: пустой реестр задают пустым списком");
        Set<String> ids = new LinkedHashSet<>();
        Map<String, String> declaredBy = new LinkedHashMap<>();
        for (ScriptPack<?> pack : packs) {
            String packId = requirePackId(pack);
            for (String id : pack.scripts()) {
                String scriptId = requireScriptId(id, packId);
                String previous = declaredBy.putIfAbsent(scriptId, packId);
                if (previous != null) {
                    throw new IllegalStateException("Скрипт '" + scriptId + "' заявлен двумя наборами: '" + previous
                            + "' и '" + packId + "'. Как надо: оставить скрипт в одной механике");
                }
                ids.add(scriptId);
            }
        }
        return ids;
    }

    public static <T> Map<String, ScriptFactory> factories(Collection<? extends ScriptPack<T>> packs, T owner) {
        Objects.requireNonNull(packs, "packs не может быть null: пустой реестр задают пустым списком");
        Objects.requireNonNull(owner, "владелец скриптов не может быть null: фабрики замыкаются на нём");
        Map<String, ScriptFactory> merged = new LinkedHashMap<>();
        Map<String, String> declaredBy = new LinkedHashMap<>();
        for (ScriptPack<T> pack : packs) {
            String packId = requirePackId(pack);
            Map<String, ScriptFactory> produced =
                    Objects.requireNonNull(pack.factories(owner), "Набор '" + packId + "' вернул null вместо карты");
            Set<String> declared = new LinkedHashSet<>();
            for (String id : pack.scripts()) {
                declared.add(requireScriptId(id, packId));
            }
            if (!declared.equals(produced.keySet())) {
                throw new IllegalStateException("Набор '" + packId + "' объявил скрипты " + declared + ", а собрал "
                        + produced.keySet() + ". Как надо: scripts() и factories() называют один набор");
            }
            for (Map.Entry<String, ScriptFactory> entry : produced.entrySet()) {
                String scriptId = requireScriptId(entry.getKey(), packId);
                ScriptFactory factory = Objects.requireNonNull(
                        entry.getValue(),
                        "Фабрика скрипта '" + scriptId + "' набора '" + packId + "' не может быть null");
                String previous = declaredBy.putIfAbsent(scriptId, packId);
                if (previous != null) {
                    throw new IllegalStateException("Скрипт '" + scriptId + "' заявлен двумя наборами: '" + previous
                            + "' и '" + packId + "'. Как надо: оставить скрипт в одной механике");
                }
                merged.put(scriptId, factory);
            }
        }
        return merged;
    }

    private static String requirePackId(ScriptPack<?> pack) {
        Objects.requireNonNull(pack, "набор скриптов не может быть null: пустой набор всё равно надо назвать");
        String id = pack.id();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Набор скриптов без id: по имени не назвать виновника дубля");
        }
        return id;
    }

    private static String requireScriptId(String id, String packId) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Набор '" + packId + "' объявил скрипт без id: движок не найдёт его");
        }
        return id;
    }
}

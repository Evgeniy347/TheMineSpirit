package com.theminespirit.core.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableRegistryTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    private static TableDefinition<String> textTable(String id, String path) {
        return TableDefinition.single(id, path, (json, origin) -> json.trim());
    }

    private static LoadedContent content(String json, String origin, TableStatus.Source source) {
        return new LoadedContent(json, origin, source);
    }

    @Test
    void holder_Replaced_SwapsCurrentAndUpdatesStatus() {
        TableHolder<String> holder = new TableHolder<>("demo", "v1", TableStatus.Source.MOD, "mod", "hash1", CLOCK);
        holder.replace("v2", TableStatus.Source.DATAPACK, "world/datapacks/p/data/demo.json", "hash2", CLOCK);

        assertEquals("v2", holder.current(), "После replace читатель обязан видеть новую версию таблицы");
        assertEquals(
                TableStatus.Source.DATAPACK,
                holder.status().source(),
                "Источник версии после замены датапаком обязан быть datapack: по нему /spirit tables показывает, кто переопределил");
        assertEquals(
                "hash2",
                holder.status().hash(),
                "Хэш содержимого обязан обновиться вместе с версией: иначе не видно, что таблица поменялась");
    }

    @Test
    void holder_FailedReload_KeepsPreviousVersionAndRecordsError() {
        TableHolder<String> holder = new TableHolder<>("demo", "v1", TableStatus.Source.MOD, "mod", "hash1", CLOCK);
        TableStatus status = holder.failed("demo.json: битый JSON. Исправьте синтаксис");

        assertEquals(
                "v1",
                holder.current(),
                "Неудачная загрузка обязана оставить прежнюю таблицу в силе: подмена пустой/сломанной версией — авария");
        assertTrue(status.failed(), "Неудачная загрузка обязана пометить статус ошибкой: это не тихий fallback");
        assertTrue(
                status.error().contains("битый JSON"),
                "Текст ошибки обязан называть причину: получили " + status.error());
        assertEquals(
                "hash1",
                status.hash(),
                "У неудачной загрузки хэш остаётся от прежней версии: старой таблицей и продолжают пользоваться");
    }

    @Test
    void reload_GoodContent_ReplacesHolder() {
        TableRegistry registry = new TableRegistry(CLOCK);
        TableHolder<String> holder = registry.register(textTable("demo", "data/theminespirit/demo.json"), "builtin");
        ContentSource source = ContentSource.of(Map.of(
                "data/theminespirit/demo.json",
                content("from_datapack", "data/theminespirit/demo.json", TableStatus.Source.DATAPACK)));

        List<TableRegistry.Report> reports = registry.reload(source);

        assertEquals("from_datapack", holder.current(), "Успешная перезагрузка обязана заменить содержимое таблицы");
        assertEquals(1, reports.size(), "Отчёт по каждой таблице: одна таблица — один отчёт");
        assertFalse(reports.get(0).failed(), "Успешная перезагрузка не должна нести ошибку");
    }

    @Test
    void reload_BrokenContent_KeepsOldAndReportsError() {
        TableRegistry registry = new TableRegistry(CLOCK);
        TableDefinition<String> strict =
                TableDefinition.single("strict", "data/theminespirit/strict.json", (json, origin) -> {
                    if (!json.startsWith("ok")) {
                        throw new IllegalStateException(origin + ": битый json. Исправьте синтаксис");
                    }
                    return json;
                });
        TableHolder<String> holder = registry.register(strict, "ok_builtin");
        ContentSource source = ContentSource.of(Map.of(
                "data/theminespirit/strict.json",
                content("broken", "data/theminespirit/strict.json", TableStatus.Source.DATAPACK)));

        List<TableRegistry.Report> reports = registry.reload(source);

        assertEquals(
                "ok_builtin",
                holder.current(),
                "Битый json при /reload не должен подменять таблицу: старая версия остаётся в силе");
        assertTrue(holder.status().failed(), "Ошибка загрузки обязана быть видна в статусе таблицы");
        assertTrue(
                reports.get(0).failed() && reports.get(0).error().contains("битый json"),
                "Отчёт обязан назвать ошибку с причиной: получили "
                        + reports.get(0).error());
    }

    @Test
    void reload_OneBrokenTable_DoesNotStopOthers() {
        TableRegistry registry = new TableRegistry(CLOCK);
        TableDefinition<String> strict =
                TableDefinition.single("strict", "data/theminespirit/strict.json", (json, origin) -> {
                    if (!json.startsWith("ok")) {
                        throw new IllegalStateException("битый json");
                    }
                    return json;
                });
        TableHolder<String> broken = registry.register(strict, "ok_old");
        TableHolder<String> good = registry.register(textTable("good", "data/theminespirit/good.json"), "good_old");
        ContentSource source = ContentSource.of(Map.of(
                "data/theminespirit/strict.json", content("broken", "strict.json", TableStatus.Source.DATAPACK),
                "data/theminespirit/good.json", content("good_new", "good.json", TableStatus.Source.DATAPACK)));

        registry.reload(source);

        assertEquals("ok_old", broken.current(), "Сломанная таблица держит прежнюю версию");
        assertEquals(
                "good_new",
                good.current(),
                "Ошибка в одной таблице не мешает перезагрузить остальные: таблицы грузятся независимо");
    }

    @Test
    void register_DuplicateId_FailsLoudly() {
        TableRegistry registry = new TableRegistry(CLOCK);
        registry.register(textTable("demo", "data/theminespirit/demo.json"), "a");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> registry.register(textTable("demo", "data/theminespirit/demo2.json"), "b"),
                "Повторная регистрация одного id обязана падать: два держателя на таблицу разошлись бы версиями");

        assertTrue(
                error.getMessage().contains("demo"),
                "Повторная регистрация id обязана падать с именем таблицы: два держателя разошлись бы версиями; "
                        + "получили " + error.getMessage());
    }

    @Test
    void reload_SameContent_KeepsSameHashAndUpdatesSource() {
        TableRegistry registry = new TableRegistry(CLOCK);
        TableHolder<String> holder = registry.register(textTable("demo", "data/theminespirit/demo.json"), "x");
        String before = holder.status().hash();
        ContentSource same = ContentSource.of(
                Map.of("data/theminespirit/demo.json", content("x", "demo.json", TableStatus.Source.DATAPACK)));

        registry.reload(same);

        assertEquals(
                before,
                holder.status().hash(),
                "Хэш неизменного содержимого обязан совпасть: по нему человек видит, что файл не менялся");
        assertNotEquals(
                TableStatus.Source.BUILTIN,
                holder.status().source(),
                "Даже при том же содержимом источник стал datapack: файл перечитан из мира");
    }
}

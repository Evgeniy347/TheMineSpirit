package com.theminespirit.core.observation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record WorldObservation(
        String botId,
        long tick,
        BlockPos position,
        List<InventoryEntry> inventory,
        List<InventoryEntry> blocks,
        List<EntityRef> entities,
        List<ContainerRef> containers,
        int radius) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final char RECORD_SEPARATOR = '\u001e';

    public WorldObservation {
        Objects.requireNonNull(botId, "botId не может быть null: наблюдение принадлежит конкретному духу");
        Objects.requireNonNull(position, "position не может быть null: наблюдение без позиции духа бессмысленно");
        inventory = sortedInventory(inventory);
        blocks = sortedInventory(blocks);
        entities = sortedEntities(entities);
        containers = sortedContainers(containers);
    }

    public String hash() {
        return sha256Hex(canonicalForm());
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать наблюдение в JSON: проверьте типы полей", e);
        }
    }

    public static WorldObservation fromJson(String json) {
        Objects.requireNonNull(json, "json не может быть null: наблюдение разбирается из текста JSON");
        try {
            return MAPPER.readValue(json, WorldObservation.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Наблюдение не разобрано из JSON: проверьте набор и типы полей наблюдения", e);
        }
    }

    private String canonicalForm() {
        StringBuilder builder = new StringBuilder();
        builder.append(botId).append(FIELD_SEPARATOR);
        builder.append(tick).append(FIELD_SEPARATOR);
        appendPos(builder, position);
        builder.append(FIELD_SEPARATOR);
        builder.append(radius).append(FIELD_SEPARATOR);
        appendInventory(builder, inventory);
        builder.append(FIELD_SEPARATOR);
        appendInventory(builder, blocks);
        builder.append(FIELD_SEPARATOR);
        appendEntities(builder);
        builder.append(FIELD_SEPARATOR);
        appendContainers(builder);
        return builder.toString();
    }

    private void appendInventory(StringBuilder builder, List<InventoryEntry> entries) {
        for (InventoryEntry entry : entries) {
            builder.append(entry.item()).append(':').append(entry.count()).append(RECORD_SEPARATOR);
        }
    }

    private void appendEntities(StringBuilder builder) {
        for (EntityRef entity : entities) {
            builder.append(entity.type()).append(':');
            appendPos(builder, entity.pos());
            builder.append(':').append(Double.toString(entity.distance())).append(RECORD_SEPARATOR);
        }
    }

    private void appendContainers(StringBuilder builder) {
        for (ContainerRef container : containers) {
            builder.append(container.type()).append(':');
            appendPos(builder, container.pos());
            builder.append(RECORD_SEPARATOR);
        }
    }

    private static void appendPos(StringBuilder builder, BlockPos pos) {
        builder.append(pos.x()).append(',').append(pos.y()).append(',').append(pos.z());
    }

    private static List<InventoryEntry> sortedInventory(List<InventoryEntry> entries) {
        List<InventoryEntry> copy = new ArrayList<>(requireList(entries, "inventory/blocks"));
        copy.sort(Comparator.comparing(InventoryEntry::item));
        return List.copyOf(copy);
    }

    private static List<EntityRef> sortedEntities(List<EntityRef> entries) {
        List<EntityRef> copy = new ArrayList<>(requireList(entries, "entities"));
        copy.sort(Comparator.comparing(EntityRef::type).thenComparing(EntityRef::pos));
        return List.copyOf(copy);
    }

    private static List<ContainerRef> sortedContainers(List<ContainerRef> entries) {
        List<ContainerRef> copy = new ArrayList<>(requireList(entries, "containers"));
        copy.sort(Comparator.comparing(ContainerRef::type).thenComparing(ContainerRef::pos));
        return List.copyOf(copy);
    }

    private static <T> List<T> requireList(List<T> entries, String field) {
        Objects.requireNonNull(entries, field + " не может быть null: пустой список передаётся как список без элементов");
        for (T entry : entries) {
            Objects.requireNonNull(entry, field + " не может содержать null: список описывает только существующие объекты");
        }
        return entries;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в этой JVM: наблюдение нельзя стабильно сравнить", e);
        }
    }
}

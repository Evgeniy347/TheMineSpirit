package com.theminespirit.core.observation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ObservationHash {
    private static final char FIELD_SEPARATOR = '\u001f';
    private static final char RECORD_SEPARATOR = '\u001e';

    private ObservationHash() {
    }

    public static String compute(WorldObservation obs) {
        String canonical = canonicalForm(obs);
        return sha256Hex(canonical);
    }

    private static String canonicalForm(WorldObservation obs) {
        StringBuilder builder = new StringBuilder();
        builder.append(obs.botId()).append(FIELD_SEPARATOR);
        builder.append(obs.radius()).append(FIELD_SEPARATOR);
        appendCoord(builder, obs.position());
        builder.append(FIELD_SEPARATOR);
        appendInventory(builder, obs.inventory());
        builder.append(FIELD_SEPARATOR);
        appendInventory(builder, obs.blocks());
        builder.append(FIELD_SEPARATOR);
        appendEntities(builder, obs.entities());
        builder.append(FIELD_SEPARATOR);
        appendContainers(builder, obs.containers());
        return builder.toString();
    }



    private static void appendInventory(StringBuilder builder, List<ItemCount> entries) {
        for (ItemCount entry : entries) {
            builder.append(entry.item()).append(':').append(entry.count()).append(RECORD_SEPARATOR);
        }
    }

    private static void appendEntities(StringBuilder builder, List<EntityRef> entities) {
        List<EntityRef> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparing(EntityRef::type)
                .thenComparing(EntityRef::pos)
                .thenComparing(EntityRef::id, Comparator.nullsLast(String::compareTo)));
        for (EntityRef entity : sorted) {
            builder.append(entity.type()).append(':');
            appendCoord(builder, entity.pos());
            if (entity.id() != null) {
                builder.append(':').append(entity.id());
            }
            builder.append(RECORD_SEPARATOR);
        }
    }

    private static void appendContainers(StringBuilder builder, List<ContainerRef> containers) {
        List<ContainerRef> sorted = new ArrayList<>(containers);
        sorted.sort(Comparator.comparing(ContainerRef::type)
                .thenComparing(ContainerRef::pos)
                .thenComparing(ContainerRef::id, Comparator.nullsLast(String::compareTo)));
        for (ContainerRef container : sorted) {
            builder.append(container.type()).append(':');
            appendCoord(builder, container.pos());
            if (container.id() != null) {
                builder.append(':').append(container.id());
            }
            builder.append(RECORD_SEPARATOR);
        }
    }

    private static void appendCoord(StringBuilder builder, BlockCoord pos) {
        builder.append(pos.x()).append(',').append(pos.y()).append(',').append(pos.z());
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

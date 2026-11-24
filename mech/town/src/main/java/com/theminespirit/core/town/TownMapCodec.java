package com.theminespirit.core.town;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class TownMapCodec {
    public static final int FORMAT = 2;
    private static final String FORMAT_KEY = "format";
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private TownMapCodec() {}

    public static TownMap decode(String json) {
        Objects.requireNonNull(json, "Текст карты не может быть null: без него нечего разбирать");
        try {
            TownMap map = MAPPER.readValue(json, TownMap.class);
            if (map == null) {
                throw new IllegalArgumentException(
                        "Карта поселения пуста: ожидался JSON с участками, улицами и стеной");
            }
            return map;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Карта поселения не читается: " + error.getOriginalMessage(), error);
        }
    }

    public static String encode(TownMap map, int revision, ColumnMask region) {
        Objects.requireNonNull(map, "Карта не может быть null: без неё нечего отдавать");
        Objects.requireNonNull(region, "Граница не может быть null: по ней считаются размеры карты");
        ObjectNode root = MAPPER.createObjectNode();
        root.put(FORMAT_KEY, FORMAT);
        root.put("revision", revision);
        ObjectNode anchor = root.putObject("anchor");
        anchor.put("x", map.anchor().x());
        anchor.put("z", map.anchor().z());
        ObjectNode bounds = root.putObject("bounds");
        bounds.put("x1", region.minX());
        bounds.put("z1", region.minZ());
        bounds.put("x2", region.minX() + region.width() - 1);
        bounds.put("z2", region.minZ() + region.depth() - 1);
        ArrayNode plots = root.putArray("plots");
        for (TownMap.MapPlot plot : map.plots()) {
            ObjectNode node = plots.addObject();
            node.put("id", plot.id());
            node.put("building", plot.building());
            node.put("x1", plot.x1());
            node.put("z1", plot.z1());
            node.put("x2", plot.x2());
            node.put("z2", plot.z2());
            node.put("facing", plot.facing());
        }
        ArrayNode streets = root.putArray("streets");
        for (TownMap.MapStreet street : map.streets()) {
            ObjectNode node = streets.addObject();
            node.put("id", street.id());
            node.put("kind", street.kind());
            if (!street.ring()) {
                node.put("x1", street.x1());
                node.put("z1", street.z1());
                node.put("x2", street.x2());
                node.put("z2", street.z2());
            }
        }
        ArrayNode wall = root.putArray("wall");
        for (TownMap.WallSegment segment : map.wall()) {
            ObjectNode node = wall.addObject();
            node.put("x1", segment.x1());
            node.put("z1", segment.z1());
            node.put("x2", segment.x2());
            node.put("z2", segment.z2());
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Карта поселения не записана в JSON: " + error.getOriginalMessage(), error);
        }
    }

    public static int format(String json) {
        Objects.requireNonNull(json, "Текст карты не может быть null: формат берётся из него");
        try {
            return MAPPER.readTree(json).path(FORMAT_KEY).asInt(1);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Карта поселения не читается: " + error.getOriginalMessage(), error);
        }
    }

    public static int revision(String json) {
        Objects.requireNonNull(json, "Текст карты не может быть null: ревизия берётся из него");
        try {
            return MAPPER.readTree(json).path("revision").asInt(-1);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Карта поселения не читается: " + error.getOriginalMessage(), error);
        }
    }

    public static byte[] pack(String json) {
        Objects.requireNonNull(json, "JSON карты не может быть null: сжимать нечего");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Карта поселения не сжата: " + error.getMessage(), error);
        }
    }

    public static String unpack(byte[] packed) {
        Objects.requireNonNull(packed, "Сжатая карта не может быть null: читать нечего");
        if (packed.length == 0) {
            throw new IllegalArgumentException("Сжатая карта пуста: сохранённый план потерян");
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Сжатая карта не распакована: " + error.getMessage(), error);
        }
    }
}

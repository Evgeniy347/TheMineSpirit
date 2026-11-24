package com.theminespirit.core.town;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

public final class TownMapCodec {
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
}

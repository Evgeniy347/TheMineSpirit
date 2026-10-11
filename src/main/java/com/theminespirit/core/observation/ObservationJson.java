package com.theminespirit.core.observation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ObservationJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(BlockCoord.class, new BlockCoordSerializer());
        module.addDeserializer(BlockCoord.class, new BlockCoordDeserializer());
        MAPPER.registerModule(module);
    }

    private ObservationJson() {
    }

    public static String toJson(WorldObservation obs) {
        try {
            return MAPPER.writeValueAsString(new Dto(obs));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать наблюдение в JSON: проверьте типы полей", e);
        }
    }

    public static WorldObservation fromJson(String json) {
        Objects.requireNonNull(json, "json не может быть null: наблюдение разбирается из текста JSON");
        try {
            Dto dto = MAPPER.readValue(json, Dto.class);
            return new WorldObservation(dto.bot, dto.tick, dto.pos, dto.inventory, dto.blocks, toEntities(dto.entities),
                    toContainers(dto.containers), dto.radius);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Наблюдение не разобрано из JSON: проверьте набор и типы полей наблюдения", e);
        }
    }

    private static List<EntityRef> toEntities(List<EntityDto> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .map(d -> new EntityRef(d.type, d.pos, d.id, d.distance))
                .toList();
    }

    private static List<ContainerRef> toContainers(List<ContainerDto> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .map(d -> new ContainerRef(d.type, d.pos, d.id))
                .toList();
    }

    public record Dto(
            String bot,
            long tick,
            BlockCoord pos,
            int radius,
            List<ItemCount> inventory,
            List<ItemCount> blocks,
            List<EntityDto> entities,
            List<ContainerDto> containers,
            String hash) {
        Dto(WorldObservation obs) {
            this(obs.botId(), obs.tick(), obs.position(), obs.radius(), obs.inventory(), obs.blocks(),
                    obs.entities().stream()
                            .map(e -> new EntityDto(e.type(), e.pos(), e.id(), e.distance()))
                            .toList(),
                    obs.containers().stream().map(c -> new ContainerDto(c.type(), c.pos(), c.id())).toList(),
                    ObservationHash.compute(obs));
        }
    }

    public record EntityDto(String type, BlockCoord pos, String id, double distance) {
    }

    public record ContainerDto(String type, BlockCoord pos, String id) {
    }

    static class BlockCoordSerializer extends StdSerializer<BlockCoord> {
        private static final long serialVersionUID = 1L;

        BlockCoordSerializer() {
            super(BlockCoord.class);
        }

        @Override
        public void serialize(BlockCoord value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartArray();
            gen.writeNumber(value.x());
            gen.writeNumber(value.y());
            gen.writeNumber(value.z());
            gen.writeEndArray();
        }
    }

    static class BlockCoordDeserializer extends JsonDeserializer<BlockCoord> {
        @Override
        public BlockCoord deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (!(node instanceof ArrayNode array)) {
                throw new JsonParseException(p, "pos должен быть массивом [x, y, z]");
            }
            if (array.size() != 3) {
                throw new JsonParseException(p, "pos должен содержать ровно 3 элемента [x, y, z]");
            }
            for (int i = 0; i < 3; i++) {
                if (!array.get(i).isNumber()) {
                    throw new JsonParseException(p, "pos содержит нечисловой элемент");
                }
            }
            return new BlockCoord(array.get(0).asInt(), array.get(1).asInt(), array.get(2).asInt());
        }
    }
}

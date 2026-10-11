package com.theminespirit.core.observation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class ObservationJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(BlockCoord.class, new BlockCoordSerializer());
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
            return new WorldObservation(dto.bot, dto.tick, new BlockCoord(dto.pos[0], dto.pos[1], dto.pos[2]),
                    dto.inventory, dto.blocks, toEntities(dto.entities), toContainers(dto.containers), dto.radius);
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
                .map(d -> new EntityRef(d.type, new BlockCoord(d.pos[0], d.pos[1], d.pos[2]), d.id, d.distance))
                .toList();
    }

    private static List<ContainerRef> toContainers(List<ContainerDto> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .map(d -> new ContainerRef(d.type, new BlockCoord(d.pos[0], d.pos[1], d.pos[2]), d.id))
                .toList();
    }

    public record Dto(
            String bot,
            long tick,
            int[] pos,
            int radius,
            List<ItemCount> inventory,
            List<ItemCount> blocks,
            List<EntityDto> entities,
            List<ContainerDto> containers,
            String hash) {
        Dto(WorldObservation obs) {
            this(obs.botId(), obs.tick(), new int[]{obs.position().x(), obs.position().y(), obs.position().z()},
                    obs.radius(), obs.inventory(), obs.blocks(),
                    obs.entities().stream().map(e -> new EntityDto(e.type(), new int[]{e.pos().x(), e.pos().y(), e.pos().z()},
                            e.id(), e.distance())).toList(),
                    obs.containers().stream()
                            .map(c -> new ContainerDto(c.type(), new int[]{c.pos().x(), c.pos().y(), c.pos().z()}, c.id()))
                            .toList(),
                    ObservationHash.compute(obs));
        }
    }

    public record EntityDto(String type, int[] pos, String id, double distance) {
    }

    public record ContainerDto(String type, int[] pos, String id) {
    }

    static class BlockCoordSerializer extends StdSerializer<BlockCoord> {
        private static final long serialVersionUID = 1L;
        BlockCoordSerializer() {
            super(BlockCoord.class);
        }

        @Override
        public void serialize(BlockCoord value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
            gen.writeStartArray();
            gen.writeNumber(value.x());
            gen.writeNumber(value.y());
            gen.writeNumber(value.z());
            gen.writeEndArray();
        }
    }
}

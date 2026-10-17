package com.theminespirit.core.observation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
                    toContainers(dto.containers), dto.radius, toSpirit(dto), toGroundItems(dto.groundItems),
                    toContainerItems(dto.containerItems), toTime(dto.time));
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

    private static List<GroundItem> toGroundItems(List<GroundItemDto> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(d -> new GroundItem(d.item, d.count)).toList();
    }

    private static List<ContainerContents> toContainerItems(List<ContainerItemDto> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().map(d -> new ContainerContents(d.pos, d.items)).toList();
    }

    private static SpiritStatus toSpirit(Dto dto) {
        if (dto.held == null && dto.health == null && dto.maxHealth == null && dto.food == null
                && dto.boardTask == null) {
            return null;
        }
        HeldItem held = dto.held == null ? null : new HeldItem(dto.held.item, dto.held.durability);
        BoardTaskRef boardTask = dto.boardTask == null ? null
                : new BoardTaskRef(dto.boardTask.caseName, dto.boardTask.status);
        return new SpiritStatus(held, dto.health, dto.maxHealth, dto.food, boardTask);
    }

    private static WorldTime toTime(WorldTimeDto dto) {
        return dto == null ? null : new WorldTime(dto.dayTime, dto.day);
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
            HeldItemDto held,
            Double health,
            @JsonProperty("max_health") Double maxHealth,
            Double food,
            @JsonProperty("board_task") BoardTaskDto boardTask,
            @JsonProperty("ground_items") List<GroundItemDto> groundItems,
            @JsonProperty("container_items") List<ContainerItemDto> containerItems,
            WorldTimeDto time,
            String hash) {
        Dto(WorldObservation obs) {
            this(obs.botId(), obs.tick(), obs.position(), obs.radius(), obs.inventory(), obs.blocks(),
                    obs.entities().stream()
                            .map(e -> new EntityDto(e.type(), e.pos(), e.id(), e.distance()))
                            .toList(),
                    obs.containers().stream().map(c -> new ContainerDto(c.type(), c.pos(), c.id())).toList(),
                    held(obs.spirit()),
                    obs.spirit() == null ? null : obs.spirit().health(),
                    obs.spirit() == null ? null : obs.spirit().maxHealth(),
                    obs.spirit() == null ? null : obs.spirit().food(),
                    boardTask(obs.spirit()),
                    obs.groundItems().isEmpty() ? null
                            : obs.groundItems().stream().map(g -> new GroundItemDto(g.item(), g.count())).toList(),
                    obs.containerItems().isEmpty() ? null
                            : obs.containerItems().stream()
                                    .map(c -> new ContainerItemDto(c.pos(), c.items())).toList(),
                    obs.time() == null ? null : new WorldTimeDto(obs.time().day(), obs.time().dayTime()),
                    ObservationHash.compute(obs));
        }

        private static HeldItemDto held(SpiritStatus spirit) {
            return spirit == null || spirit.held() == null ? null
                    : new HeldItemDto(spirit.held().item(), spirit.held().durability());
        }

        private static BoardTaskDto boardTask(SpiritStatus spirit) {
            return spirit == null || spirit.boardTask() == null ? null
                    : new BoardTaskDto(spirit.boardTask().caseName(), spirit.boardTask().status());
        }
    }

    public record EntityDto(String type, BlockCoord pos, String id, double distance) {
    }

    public record ContainerDto(String type, BlockCoord pos, String id) {
    }

    public record HeldItemDto(String item, Integer durability) {
    }

    public record BoardTaskDto(@JsonProperty("case") String caseName, String status) {
    }

    public record GroundItemDto(String item, int count) {
    }

    public record ContainerItemDto(BlockCoord pos, List<ItemCount> items) {
    }

    public record WorldTimeDto(boolean day, @JsonProperty("day_time") long dayTime) {
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

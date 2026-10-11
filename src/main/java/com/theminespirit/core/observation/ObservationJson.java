package com.theminespirit.core.observation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;

public final class ObservationJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            return new WorldObservation(dto.bot, dto.tick, dto.pos, dto.inventory, dto.blocks, dto.entities,
                    dto.containers, dto.radius);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Наблюдение не разобрано из JSON: проверьте набор и типы полей наблюдения", e);
        }
    }

    public record Dto(
            @JsonProperty("bot") String bot,
            @JsonProperty("tick") long tick,
            @JsonProperty("pos") BlockCoord pos,
            @JsonProperty("radius") int radius,
            @JsonProperty("inventory") List<ItemCount> inventory,
            @JsonProperty("blocks") List<ItemCount> blocks,
            @JsonProperty("entities") List<EntityRef> entities,
            @JsonProperty("containers") List<ContainerRef> containers,
            @JsonProperty("hash") String hash) {

        Dto(WorldObservation obs) {
            this(obs.botId(), obs.tick(), obs.position(), obs.radius(), obs.inventory(), obs.blocks(),
                    obs.entities(), obs.containers(), ObservationHash.compute(obs));
        }
    }
}

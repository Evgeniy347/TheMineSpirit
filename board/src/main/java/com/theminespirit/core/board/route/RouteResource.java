package com.theminespirit.core.board.route;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public enum RouteResource {
    SAND("sand", "sand", "desert", "route_desert", "path_desert", Set.of("sand", "red_sand")),
    SNOW(
            "snowball",
            "snow",
            "snowy",
            "route_snow",
            "path_snowy",
            Set.of("snow", "snow_block", "powder_snow", "ice", "packed_ice", "blue_ice")),
    DIRT(
            "dirt",
            "dirt",
            "plains",
            "route_dirt",
            "path_plains",
            Set.of("dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt", "mycelium", "moss_block"));

    private final String item;
    private final String event;
    private final String family;
    private final String routeBuilding;
    private final String pathBuilding;
    private final Set<String> blocks;

    RouteResource(
            String item, String event, String family, String routeBuilding, String pathBuilding, Set<String> blocks) {
        this.item = Objects.requireNonNull(item, "Предмет ресурса не может быть null: из него собирают задание");
        this.event = Objects.requireNonNull(event, "Имя события не может быть null: по нему граф просит маршрут");
        this.family = Objects.requireNonNull(family, "Семейство биома не может быть null: по нему тег мира");
        this.routeBuilding =
                Objects.requireNonNull(routeBuilding, "Постройка маршрута не может быть null: точку ставит граф");
        this.pathBuilding =
                Objects.requireNonNull(pathBuilding, "Постройка тропы не может быть null: до точки ведёт дорога");
        this.blocks = Set.copyOf(blocks);
    }

    public String item() {
        return item;
    }

    public String family() {
        return family;
    }

    public String routeBuilding() {
        return routeBuilding;
    }

    public String pathBuilding() {
        return pathBuilding;
    }

    public String worldTag() {
        return "biome:" + family;
    }

    public String foundEvent() {
        return "biome_found:" + family;
    }

    public String depletedEvent() {
        return "source_depleted:" + event;
    }

    public Set<String> blocks() {
        return blocks;
    }

    public boolean matchesBlock(String block) {
        Objects.requireNonNull(block, "Блок не может быть null: по нему ищут место добычи");
        String name = block.startsWith("minecraft:") ? block.substring("minecraft:".length()) : block;
        return blocks.contains(name);
    }

    public static Optional<RouteResource> ofBlock(String block) {
        Objects.requireNonNull(block, "Блок не может быть null: по нему ищут ресурс маршрута");
        for (RouteResource resource : values()) {
            if (resource.matchesBlock(block)) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    public static Optional<RouteResource> ofBuilding(String building) {
        Objects.requireNonNull(building, "Постройка не может быть null: по ней узнают ресурс маршрута");
        for (RouteResource resource : values()) {
            if (resource.routeBuilding.equals(building) || resource.pathBuilding.equals(building)) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    public boolean isPath(String building) {
        return pathBuilding.equals(building);
    }

    public boolean isRoute(String building) {
        return routeBuilding.equals(building);
    }
}

package com.theminespirit.core.script;

import static com.theminespirit.core.script.ScriptCatalogFixturesData.*;

import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

class ScriptCatalogTestsSupport {
    static final long WORLD_MIN = -30_000_000L;
    static final long WORLD_MAX = 30_000_000L;

    record ScriptExpectation(String id, List<String> required, Map<String, long[]> bounds) {}

    static Stream<Arguments> scriptSpecRows() {
        List<ScriptExpectation> rows = List.of(
                spec("scan", List.of(), Map.of("radius", new long[] {1, 32})),
                spec("scan_blocks", List.of(), Map.of("radius", new long[] {1, 32})),
                spec("scan_entities", List.of(), Map.of("radius", new long[] {1, 32})),
                spec("sense_hazard", List.of(), Map.of("radius", new long[] {1, 32})),
                spec("locate_block", List.of("filter"), Map.of("radius", new long[] {1, 32})),
                spec("locate_tree", List.of(), Map.of("radius", new long[] {1, 32})),
                spec("locate_container", List.of(), Map.of("radius", new long[] {1, 32})),
                spec(
                        "look_at",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "remember",
                        List.of("name", "x", "y", "z", "type"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec("recall", List.of("name"), Map.of()),
                spec("recall_all", List.of("type"), Map.of()),
                spec("forget", List.of("name"), Map.of()),
                spec(
                        "goto",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec("follow", List.of("target"), Map.of("radius", new long[] {0, 16})),
                spec("escort", List.of("target"), Map.of("radius", new long[] {0, 16}, "until", new long[] {0, 6000})),
                spec("return_home", List.of(), Map.of()),
                spec("patrol", List.of("points"), Map.of("loops", new long[] {1, 16})),
                spec("goto_block", List.of("block"), Map.of("radius", new long[] {1, 32}, "count", new long[] {1, 256
                })),
                spec("come_to_player", List.of(), Map.of()),
                spec("wander", List.of(), Map.of("radius", new long[] {1, 32}, "duration_ticks", new long[] {20, 6000
                })),
                spec("explore", List.of(), Map.of("radius", new long[] {1, 32}, "budget", new long[] {1, 64})),
                spec("keep_distance", List.of("target"), Map.of("distance", new long[] {1, 16})),
                spec(
                        "walk_careful",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec("step_over", List.of("dx", "dz"), Map.of("dx", new long[] {-1, 1}, "dz", new long[] {-1, 1})),
                spec(
                        "approach",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "swim",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "cross_water",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "bridge",
                        List.of("x", "y", "z", "length"),
                        Map.of(
                                "x",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y",
                                new long[] {-64, 319},
                                "z",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "length",
                                new long[] {CatalogLimits.BRIDGE_LENGTH_MIN, CatalogLimits.BRIDGE_LENGTH_MAX})),
                spec("flee", List.of(), Map.of()),
                spec("chop_tree", List.of(), Map.of("count", new long[] {1, 256}, "radius", new long[] {
                    1, CatalogLimits.CHOP_RADIUS_MAX
                })),
                spec(
                        "chop_target",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec("mine_vein", List.of(), Map.of("count", new long[] {1, 256})),
                spec(
                        "dig_area",
                        List.of("x", "y", "z", "size"),
                        Map.of(
                                "x",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y",
                                new long[] {-64, 319},
                                "z",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "size",
                                new long[] {1, 16})),
                spec("dig_descent", List.of("direction"), Map.of("y", new long[] {-64, 319})),
                spec("dig_descent_widen", List.of("direction"), Map.of("y", new long[] {-64, 319})),
                spec("place_descent_stairs", List.of("direction"), Map.of("y", new long[] {-64, 319})),
                spec(
                        "safe_mine",
                        List.of("mode", "direction"),
                        Map.of(
                                "y",
                                new long[] {-64, 319},
                                "length",
                                new long[] {CatalogLimits.MINE_LENGTH_MIN, CatalogLimits.MINE_LENGTH_MAX},
                                "torchInterval",
                                new long[] {
                                    MiningTuning.DESCENT_TORCH_INTERVAL_MIN, MiningTuning.DESCENT_TORCH_INTERVAL_MAX
                                })),
                spec("gather", List.of("block", "count"), Map.of("count", new long[] {1, 256})),
                spec("collect_drops", List.of(), Map.of("radius", new long[] {1, 16})),
                spec("harvest_crop", List.of(), Map.of("radius", new long[] {1, 16})),
                spec("plant_crop", List.of("crop"), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 256})),
                spec("till", List.of(), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 256})),
                spec("bone_meal", List.of(), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 256})),
                spec("breed", List.of("species"), Map.of("radius", new long[] {1, 16}, "pairs", new long[] {1, 8})),
                spec("feed", List.of("species"), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 64})),
                spec("shear", List.of(), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 64})),
                spec("milk", List.of(), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 64})),
                spec("collect_eggs", List.of(), Map.of("radius", new long[] {1, 16})),
                spec("harvest_hive", List.of(), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 64})),
                spec("brush", List.of(), Map.of("radius", new long[] {1, 16}, "count", new long[] {1, 64})),
                spec("cast_obsidian", List.of(), Map.of("count", new long[] {1, 256}, "radius", new long[] {1, 32})),
                spec("light_portal", List.of(), Map.of("radius", new long[] {1, 32})),
                spec("fill_bucket", List.of("fluid"), Map.of("radius", new long[] {1, 32})),
                spec(
                        "pour_bucket",
                        List.of("fluid", "x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec("cull", List.of("species"), Map.of("keep", new long[] {2, 64}, "radius", new long[] {1, 16})),
                spec("herd_check", List.of(), Map.of("radius", new long[] {1, 16})),
                spec("hunt", List.of("species"), Map.of("radius", new long[] {1, 48}, "count", new long[] {1, 64})),
                spec("capture", List.of("species"), Map.of("radius", new long[] {1, 16})),
                spec(
                        "fish",
                        List.of(),
                        Map.of(
                                "count",
                                new long[] {1, 256},
                                "duration_ticks",
                                new long[] {CatalogLimits.FISH_DURATION_MIN, CatalogLimits.FISH_DURATION_MAX},
                                "radius",
                                new long[] {1, 32})),
                spec("bucket_fish", List.of(), Map.of("count", new long[] {1, 256}, "radius", new long[] {1, 32})),
                spec("craft", List.of("item", "count"), Map.of("count", new long[] {1, 256}, "radius", new long[] {1, 32
                })),
                spec("smelt", List.of("item", "count"), Map.of("count", new long[] {1, 256}, "radius", new long[] {1, 32
                })),
                spec("equip", List.of("item"), Map.of()),
                spec("deposit", List.of("item", "count"), Map.of("count", new long[] {1, 256}, "radius", new long[] {
                    1, 16
                })),
                spec("withdraw", List.of("item", "count"), Map.of("count", new long[] {1, 256}, "radius", new long[] {
                    1, 16
                })),
                spec("eat", List.of(), Map.of()),
                spec("sort_chest", List.of(), Map.of("radius", new long[] {1, 16})),
                spec("unload", List.of(), Map.of("radius", new long[] {1, 16})),
                spec("acquire", List.of("item", "count"), Map.of("count", new long[] {1, 256})),
                spec("acquire_at", List.of("item", "count", "x", "y", "z"), Map.of("count", new long[] {1, 256})),
                spec("attack", List.of(), Map.of()),
                spec("defend", List.of("target"), Map.of()),
                spec("retreat", List.of(), Map.of()),
                spec("disengage", List.of(), Map.of("distance", new long[] {1, 16})),
                spec("pillar_up", List.of("height"), Map.of("height", new long[] {1, 256})),
                spec(
                        "dig_out",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec("pillar_down", List.of(), Map.of()),
                spec("climb", List.of(), Map.of()),
                spec(
                        "place_block",
                        List.of("block", "x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "light_area",
                        List.of("x", "y", "z"),
                        Map.of(
                                "x",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y",
                                new long[] {-64, 319},
                                "z",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "radius",
                                new long[] {CatalogLimits.LIGHT_RADIUS_MIN, CatalogLimits.LIGHT_RADIUS_MAX})),
                spec(
                        "fill",
                        List.of("block", "x1", "y1", "z1", "x2", "y2", "z2"),
                        Map.of(
                                "x1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y1",
                                new long[] {-64, 319},
                                "z1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "x2",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y2",
                                new long[] {-64, 319},
                                "z2",
                                new long[] {WORLD_MIN, WORLD_MAX})),
                spec(
                        "clear_area",
                        List.of("x1", "y1", "z1", "x2", "y2", "z2"),
                        Map.of(
                                "x1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y1",
                                new long[] {-64, 319},
                                "z1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "x2",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y2",
                                new long[] {-64, 319},
                                "z2",
                                new long[] {WORLD_MIN, WORLD_MAX})),
                spec(
                        "build_schematic",
                        List.of("file", "x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "build_farm",
                        List.of("crop", "x1", "z1", "x2", "z2"),
                        Map.of(
                                "x1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "z1",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "x2",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "z2",
                                new long[] {WORLD_MIN, WORLD_MAX})),
                spec("build_fence", List.of("block", "points"), Map.of()),
                spec("build_shelter", List.of(), Map.of()),
                spec("fill_crater", List.of("crater"), Map.of()),
                spec("deliver", List.of("item", "count", "to"), Map.of("count", new long[] {1, 256})),
                spec("meet", List.of("target"), Map.of()),
                spec("say", List.of("text"), Map.of()),
                spec("wait", List.of("ticks"), Map.of("ticks", new long[] {1, 6000})),
                spec("sleep", List.of(), Map.of()),
                spec(
                        "close_door",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "set_camp",
                        List.of(),
                        Map.of(
                                "x",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "y",
                                new long[] {-64, 319},
                                "z",
                                new long[] {WORLD_MIN, WORLD_MAX},
                                "radius",
                                new long[] {CatalogLimits.CAMP_RADIUS_MIN, CatalogLimits.CAMP_RADIUS_MAX})),
                spec("clear_camp", List.of(), Map.of()),
                spec("repair_tool", List.of("item"), Map.of()),
                spec(
                        "use_block",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec(
                        "open",
                        List.of("x", "y", "z"),
                        Map.of("x", new long[] {WORLD_MIN, WORLD_MAX}, "y", new long[] {-64, 319}, "z", new long[] {
                            WORLD_MIN, WORLD_MAX
                        })),
                spec("set_combat_mode", List.of("mode"), Map.of()),
                spec("drop_to_player", List.of("item", "count", "to"), Map.of("count", new long[] {1, 256})),
                spec("give", List.of("item", "count", "to"), Map.of("count", new long[] {1, 256})),
                spec("take", List.of("item", "count", "from"), Map.of("count", new long[] {1, 256})),
                spec("report", List.of(), Map.of()),
                spec("swap_tool", List.of("block"), Map.of()),
                spec("drink_potion", List.of("type"), Map.of()));
        return Stream.of(rows, GatherScriptSpecRows.rows(), BrewScriptSpecsRows.rows())
                .flatMap(List::stream)
                .map(Arguments::of);
    }
}

package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * lib: реестр скриптов разложен по наборам механик через контракт :api. Сторож держит список
 * зарегистрированных id: перенос набора в другой модуль не имеет права молча потерять скрипт.
 */
class ScriptRegistryGuardTests {
    private static final Path NEOFORGE = SourceFiles.path("neoforge");
    private static final Pattern REGISTERED = Pattern.compile("\\.?put\\(\\s*\"([a-z][a-z_]*)\"\\s*,\\s*spirit\\s*->");
    private static final Set<String> EXPECTED = Set.of(
            "acquire",
            "acquire_at",
            "approach",
            "attack",
            "bone_meal",
            "breed",
            "brew",
            "bridge",
            "brush",
            "bucket_fish",
            "build_farm",
            "build_fence",
            "build_schematic",
            "build_shelter",
            "capture",
            "cast_obsidian",
            "chop_target",
            "chop_tree",
            "clear_area",
            "clear_camp",
            "climb",
            "close_door",
            "collect_drops",
            "collect_eggs",
            "come_to_player",
            "craft",
            "cross_water",
            "cull",
            "defend",
            "deliver",
            "deposit",
            "dig_area",
            "dig_descent",
            "dig_descent_widen",
            "dig_out",
            "disengage",
            "drink_potion",
            "drop_to_player",
            "eat",
            "equip",
            "escort",
            "explore",
            "farm_nether_wart",
            "feed",
            "fill",
            "fill_bucket",
            "fill_crater",
            "fish",
            "flee",
            "follow",
            "forget",
            "gather",
            "give",
            "goto",
            "goto_block",
            "harvest_crop",
            "harvest_hive",
            "herd_check",
            "hunt",
            "keep_distance",
            "light_area",
            "light_portal",
            "locate_block",
            "locate_container",
            "locate_tree",
            "look_at",
            "meet",
            "milk",
            "mine_vein",
            "open",
            "patrol",
            "pillar_down",
            "pillar_up",
            "place_block",
            "place_descent_stairs",
            "plant_crop",
            "pour_bucket",
            "recall",
            "recall_all",
            "remember",
            "repair_tool",
            "report",
            "retreat",
            "return_home",
            "safe_mine",
            "say",
            "scan",
            "scan_blocks",
            "scan_entities",
            "sense_hazard",
            "set_camp",
            "set_combat_mode",
            "shear",
            "sleep",
            "smelt",
            "sort_chest",
            "step_over",
            "strip_log",
            "swap_tool",
            "swim",
            "take",
            "till",
            "unload",
            "undo_lease",
            "use_block",
            "wait",
            "walk_careful",
            "wander",
            "withdraw");

    @Test
    void registeredScripts_ComparedToExpectedSet_Match() {
        assertEquals(
                EXPECTED,
                new TreeSet<>(registered()),
                "Реестр скриптов разъехался со списком: пропавший id означает, что граф вызовет несуществующее "
                        + "действие, а лишний — действие без карточки и описания. Как надо: скрипт объявляется "
                        + "в наборе своей механики (SpiritScriptPack) ровно один раз");
    }

    @Test
    void registered_ParsesContractRegistration_AndIgnoresOtherPut() {
        assertEquals(
                Set.of("goto"),
                registeredIn("""
                        final class SampleScriptPack extends SpiritScriptPack {
                            void register() {
                                put("goto", spirit -> params -> new GotoScript(spirit));
                                cache.put("not_script", value);
                            }
                        }
                        """),
                "Сторож ослеп: объявление скрипта в наборе обязано попадать в список, а прочие put — нет");
    }

    private static Set<String> registered() {
        Set<String> ids = new LinkedHashSet<>();
        for (String text : SourceFiles.javaUnder(NEOFORGE).values()) {
            ids.addAll(registeredIn(text));
        }
        return ids;
    }

    private static Set<String> registeredIn(String text) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = REGISTERED.matcher(text);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }
}

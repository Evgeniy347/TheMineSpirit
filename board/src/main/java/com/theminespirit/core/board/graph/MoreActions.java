package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import java.util.Map;

public final class MoreActions {
    private static final Map<String, BoardCase> CASES = Map.ofEntries(
            Map.entry("life_protect_player", BoardCase.ACTIVITY_DEFEND),
            Map.entry("life_feed_player", BoardCase.ACTIVITY_SOCIAL),
            Map.entry("more5_light_crops", BoardCase.LIGHTING),
            Map.entry("more5_light_saplings", BoardCase.LIGHTING),
            Map.entry("more5_water_crops", BoardCase.ACTIVITY_FARM),
            Map.entry("more5_fertilize_growth", BoardCase.ACTIVITY_FARM),
            Map.entry("more5_gather_fuel", BoardCase.GATHER_MATERIALS),
            Map.entry("more5_make_charcoal", BoardCase.SMELT_CHARCOAL),
            Map.entry("more5_gather_build_material", BoardCase.GATHER_MATERIALS),
            Map.entry("more5_craft_build_material", BoardCase.GATHER_MATERIALS),
            Map.entry("more5_restore_block", BoardCase.ACTIVITY_BUILD),
            Map.entry("more5_bridge_route", BoardCase.ACTIVITY_BUILD),
            Map.entry("more5_place_site_chest", BoardCase.ACTIVITY_BUILD),
            Map.entry("more5_carry_to_site", BoardCase.SUPPLY_SITE),
            Map.entry("more5_light_mine", BoardCase.LIGHTING),
            Map.entry("more5_craft_mine_torch", BoardCase.CRAFT_TORCHES),
            Map.entry("more5_pickup_wood_drop", BoardCase.COLLECT_DROPS),
            Map.entry("more5_plant_sapling", BoardCase.ACTIVITY_BUILD),
            Map.entry("more6_refuel_station", BoardCase.ACTIVITY_SMELT),
            Map.entry("more6_place_station", BoardCase.CRAFTING_STATION),
            Map.entry("more6_pickup_items", BoardCase.COLLECT_DROPS),
            Map.entry("more6_fetch_water", BoardCase.ACTIVITY_UNLOAD),
            Map.entry("more6_fetch_lava", BoardCase.ACTIVITY_UNLOAD),
            Map.entry("more6_deposit_materials", BoardCase.ACTIVITY_UNLOAD),
            Map.entry("more6_maintain_stock", BoardCase.GATHER_MATERIALS),
            Map.entry("more6_assign_idle", BoardCase.ACTIVITY_SENSE),
            Map.entry("more7_harvest_early", BoardCase.HARVEST_ORCHARD),
            Map.entry("more7_expand_pen", BoardCase.BUILD_PEN),
            Map.entry("more7_return_home", BoardCase.ACTIVITY_RETURN),
            Map.entry("more7_order_to_chest", BoardCase.ACTIVITY_UNLOAD));

    private MoreActions() {}

    public static Map<String, BoardCase> cases() {
        return CASES;
    }

    public static boolean handles(GraphTickets.Ticket ticket) {
        return CASES.containsKey(key(ticket));
    }

    public static String key(GraphTickets.Ticket ticket) {
        if (ticket.kind() == GraphTickets.Kind.WAY) {
            return ticket.node();
        }
        String id = ticket.task().id();
        int at = id.lastIndexOf(':');
        return at < 0 ? id : id.substring(at + 1);
    }
}

package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphKits;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.More4Rules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class More4Post {
    private static final String BREAD = More4Scripts.MINECRAFT + "bread";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";
    private static final String DROP_TO_PLAYER = "drop_to_player";
    private static final int REST_TICKS = 100;

    private static final Set<String> NODES = Set.of(
            "more4_guard_settlement",
            "more4_watch_chests",
            "more4_bring_player_food",
            "more4_lend_player_tool",
            "more4_switch_tool",
            "more4_guard_portal",
            "more4_fence_portal",
            "more4_guard_with_pet",
            "more4_store_rare",
            "more4_sort_rare",
            "more4_feed_hungry",
            "more4_rest_injured");

    private More4Post() {}

    static boolean handles(String node) {
        return NODES.contains(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        return switch (ticket.node()) {
            case "more4_guard_settlement", "more4_guard_with_pet" -> guard(post, ticket, post.scan.home());
            case "more4_watch_chests" -> guard(post, ticket, chestPoint(post));
            case "more4_guard_portal" -> guard(post, ticket, portal(post));
            case "more4_bring_player_food" -> bringFood(post);
            case "more4_lend_player_tool" -> lendTool(post);
            case "more4_switch_tool" -> switchTool(post);
            case "more4_fence_portal" -> fencePortal(post, ticket);
            case "more4_store_rare" -> storeRare(post);
            case "more4_sort_rare" -> sortRare(post);
            case "more4_feed_hungry" -> feedHungry(post);
            case "more4_rest_injured" -> restInjured(post);
            default -> false;
        };
    }

    private static boolean guard(GraphPost post, GraphTickets.Ticket ticket, @Nullable BlockCoord point) {
        if (point == null) {
            return false;
        }
        return ActivityLaunch.launchOrQueue(
                post,
                ticket.id(),
                ticket.task().priority(),
                BoardCase.ACTIVITY_GUARD,
                "patrol",
                new ScriptCall("patrol", More4Scripts.patrolParams(point)));
    }

    private static @Nullable BlockCoord chestPoint(GraphPost post) {
        BlockCoord home = post.scan.home();
        return post.state.facts().chests().stream()
                .map(GraphFacts.Chest::position)
                .map(position -> new BlockCoord(position.x(), position.y(), position.z()))
                .min((one, two) -> Double.compare(More4Scripts.distance(one, home), More4Scripts.distance(two, home)))
                .orElse(null);
    }

    private static @Nullable BlockCoord portal(GraphPost post) {
        return More4World.portalPoint(post.level, post.scan.home()).orElse(null);
    }

    private static boolean fencePortal(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord point = portal(post);
        if (point == null) {
            return false;
        }
        String id = "more4_fence:" + More4Scripts.point(point);
        if (post.done(id) || post.open(id)) {
            return true;
        }
        post.submit(
                post.ticket(
                        id,
                        BoardCase.BUILD_FENCE,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        post.items(ticket.task())),
                TicketTarget.at(point));
        return true;
    }

    private static boolean bringFood(GraphPost post) {
        ServerPlayer player = hungryPlayer(post.level);
        if (player == null) {
            return false;
        }
        TaskNode task = new SequenceNode(List.of(
                new FallbackNode(List.of(
                        new ScriptCall("withdraw", More4Scripts.withdraw(BREAD)),
                        new ScriptCall(WAIT, Map.of(TICKS, 1)))),
                new ScriptCall(DROP_TO_PLAYER, More4Scripts.drop(BREAD, player))));
        return ActivityLaunch.launch(post, BoardCase.ACTIVITY_SOCIAL, DROP_TO_PLAYER, task);
    }

    private static boolean lendTool(GraphPost post) {
        ServerPlayer player = brokenToolPlayer(post.level);
        if (player == null) {
            return false;
        }
        String item = BuiltInRegistries.ITEM
                .getKey(player.getMainHandItem().getItem())
                .toString();
        TaskNode task = new SequenceNode(List.of(
                new FallbackNode(List.of(
                        new ScriptCall("withdraw", More4Scripts.withdraw(item)),
                        new ScriptCall(WAIT, Map.of(TICKS, 1)))),
                new ScriptCall(DROP_TO_PLAYER, More4Scripts.drop(item, player))));
        return ActivityLaunch.launch(post, BoardCase.ACTIVITY_SOCIAL, DROP_TO_PLAYER, task);
    }

    private static boolean switchTool(GraphPost post) {
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            String required = More4Rules.requiredKind(spirit.currentActivity().orElse(null));
            if (required == null) {
                continue;
            }
            if (!GraphKits.of(More4Scripts.itemIds(spirit), post.graph).containsKey(required)) {
                continue;
            }
            if (required.equals(More4Scripts.heldKind(spirit, post.graph))) {
                continue;
            }
            TaskNode task = new ScriptCall("swap_tool", Map.of("block", More4Scripts.toolBlock(required)));
            if (ActivityLaunch.launchOn(post, spirit, BoardCase.ACTIVITY_ARM, "swap_tool", task)) {
                return true;
            }
        }
        return false;
    }

    private static boolean storeRare(GraphPost post) {
        Set<String> rares = new LinkedHashSet<>(post.graph.members(More4Rules.RARE));
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            Map<String, Integer> items = More4Scripts.inventory(spirit);
            Optional<String> rare = More4Scripts.rareItem(items, rares);
            if (rare.isEmpty()) {
                continue;
            }
            String item = rare.orElseThrow();
            int count = Math.min(More4Scripts.MAX_COUNT, items.getOrDefault(item, 1));
            TaskNode task = new ScriptCall(
                    "deposit",
                    Map.of(
                            "container",
                            "warehouse",
                            "item",
                            item,
                            "count",
                            count,
                            "radius",
                            More4Scripts.CONTAINER_RADIUS));
            return ActivityLaunch.launchOn(post, spirit, BoardCase.ACTIVITY_UNLOAD, "deposit", task);
        }
        return false;
    }

    private static boolean sortRare(GraphPost post) {
        TaskNode task = new ScriptCall(
                "sort_chest", Map.of("container", More4Scripts.NEAREST, "radius", More4Scripts.CONTAINER_RADIUS));
        return ActivityLaunch.launch(post, BoardCase.ACTIVITY_UNLOAD, "sort_chest", task);
    }

    private static boolean feedHungry(GraphPost post) {
        SpiritEntity hungry = needy(post, true);
        if (hungry == null) {
            return false;
        }
        TaskNode task = new SequenceNode(List.of(
                new FallbackNode(List.of(
                        new ScriptCall("withdraw", More4Scripts.withdraw(BREAD)),
                        new ScriptCall(WAIT, Map.of(TICKS, 1)))),
                new ScriptCall("eat", Map.of())));
        return ActivityLaunch.launchOn(post, hungry, BoardCase.ACTIVITY_REST, "eat", task);
    }

    private static boolean restInjured(GraphPost post) {
        SpiritEntity injured = needy(post, false);
        if (injured == null) {
            return false;
        }
        TaskNode task = new ScriptCall(WAIT, Map.of(TICKS, REST_TICKS));
        return ActivityLaunch.launchOn(post, injured, BoardCase.ACTIVITY_REST, WAIT, task);
    }

    private static @Nullable SpiritEntity needy(GraphPost post, boolean food) {
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            GraphFacts.Spirit fact =
                    post.state.facts().spirits().get(spirit.getUUID().toString());
            if (fact == null) {
                continue;
            }
            boolean hungry = fact.food() <= More4Rules.HUNGRY_FOOD;
            boolean injured = fact.health() < More4Rules.INJURED_HEALTH;
            if (food ? hungry : injured) {
                return spirit;
            }
        }
        return null;
    }

    private static @Nullable ServerPlayer hungryPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.getFoodData().getFoodLevel() <= More4Rules.HUNGRY_FOOD) {
                return player;
            }
        }
        return null;
    }

    private static @Nullable ServerPlayer brokenToolPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            ItemStack hand = player.getMainHandItem();
            if (!hand.isEmpty() && hand.getMaxDamage() > 0 && hand.getDamageValue() >= hand.getMaxDamage() - 1) {
                return player;
            }
        }
        return null;
    }
}

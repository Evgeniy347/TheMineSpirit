package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.MorePlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

final class MorePost {
    private static final int PLAYER_RADIUS = 16;
    private static final int HOME_RADIUS = 16;
    private static final int HOME_BELOW = 4;
    private static final int HOME_ABOVE = 16;
    private static final String COBBLESTONE = "minecraft:cobblestone";

    private MorePost() {}

    static boolean handles(String node) {
        return node.startsWith("more_") || node.startsWith("more2_");
    }

    static boolean event(GraphPost post, GraphTickets.Ticket ticket) {
        return handles(ticket.node()) && (repeating(post, ticket) || act(post, ticket));
    }

    static boolean way(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        return handles(ticket.node()) && (repeating(post, ticket) || act(post, ticket));
    }

    static boolean building(GraphPost post, GraphTickets.Ticket ticket) {
        return More2Post.building(post, ticket);
    }

    private static boolean repeating(GraphPost post, GraphTickets.Ticket ticket) {
        return post.open(ticket.id()) || post.done(ticket.id());
    }

    private static boolean act(GraphPost post, GraphTickets.Ticket ticket) {
        return switch (ticket.node()) {
            case "more_help_player" -> helpPlayer(post, ticket);
            case "more_keep_clear" -> keepClear(post, ticket);
            case "more_light_dark_spot" -> lightDarkSpot(post, ticket);
            case "more_return_before_night" ->
                MoreActions.script(post, ticket, BoardCase.ACTIVITY_RETURN, home(post), MorePlans.returnHome());
            case "more_pickup_drops" -> GraphCollect.post(post, post.around, ticket);
            case "more_replant" -> new GraphHarvestPost(post).orchard(ticket, ticket.task());
            case "more_sustain_mine" -> sustainMine(post, ticket);
            case "more_secure_fence" -> new GraphHarvestPost(post).fences(ticket);
            case "more_secure_door" -> secureDoor(post, ticket);
            case "more_secure_hopper" -> secureHopper(post, ticket);
            case "more_share_reserve" -> shareReserve(post, ticket);
            default -> More2Post.way(post, ticket);
        };
    }

    private static boolean helpPlayer(GraphPost post, GraphTickets.Ticket ticket) {
        ServerPlayer player = player(post);
        if (player == null) {
            return false;
        }
        BlockCoord at = MoreScan.coord(player.blockPosition());
        String name = "player:" + player.getGameProfile().getName();
        return MoreActions.script(post, ticket, BoardCase.ACTIVITY_SOCIAL, at, MorePlans.meet(at, name));
    }

    private static boolean keepClear(GraphPost post, GraphTickets.Ticket ticket) {
        ServerPlayer player = player(post);
        if (player == null) {
            return false;
        }
        BlockCoord at = MoreScan.coord(player.blockPosition());
        return MoreActions.script(
                post,
                ticket,
                BoardCase.ACTIVITY_EXPEDITION,
                at,
                MorePlans.keepDistance(at, "player:" + player.getGameProfile().getName()));
    }

    private static @Nullable ServerPlayer player(GraphPost post) {
        BlockPos home = MoreScan.pos(home(post));
        return post.level.players().stream()
                .filter(candidate -> candidate.blockPosition().distSqr(home) <= (long) PLAYER_RADIUS * PLAYER_RADIUS)
                .findFirst()
                .orElse(null);
    }

    private static boolean lightDarkSpot(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord dark =
                MoreScan.darkest(post.level, post.scan.stage().enclosure().cells());
        if (dark == null) {
            return false;
        }
        return MoreActions.script(post, ticket, BoardCase.LIGHTING, dark, null);
    }

    private static boolean sustainMine(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord at = post.scan.stage().mineEntrance();
        return MoreActions.script(post, ticket, BoardCase.ACTIVITY_MINE, at, MorePlans.safeMine(at));
    }

    private static boolean secureDoor(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<BlockCoord> door = MoreScan.find(
                post.level,
                home(post),
                HOME_RADIUS,
                HOME_BELOW,
                HOME_ABOVE,
                (at, state) -> state.getBlock() instanceof DoorBlock
                        && state.getValue(DoorBlock.OPEN)
                        && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
        if (door.isEmpty()) {
            return false;
        }
        BlockCoord at = door.orElseThrow();
        return MoreActions.script(post, ticket, BoardCase.ACTIVITY_EXTERNAL, at, MorePlans.closeDoor(at));
    }

    private static boolean secureHopper(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<BlockCoord> hopper = MoreScan.find(
                post.level,
                home(post),
                HOME_RADIUS,
                HOME_BELOW,
                HOME_ABOVE,
                (at, state) -> state.getBlock() instanceof HopperBlock);
        if (hopper.isEmpty()) {
            return false;
        }
        BlockCoord cover = new BlockCoord(
                hopper.get().x(), hopper.get().y() + 1, hopper.get().z());
        if (!post.level.getBlockState(MoreScan.pos(cover)).isAir()) {
            return false;
        }
        return MoreActions.script(post, ticket, BoardCase.ACTIVITY_BUILD, cover, MorePlans.cover(cover, COBBLESTONE));
    }

    private static boolean shareReserve(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<UUID> unprotected = post.spirits.stream()
                .filter(MorePost::unarmored)
                .map(SpiritEntity::getUUID)
                .findFirst();
        String armor = post.stock.available().keySet().stream()
                .filter(MorePost::armor)
                .findFirst()
                .orElse(null);
        if (unprotected.isEmpty() || armor == null) {
            return false;
        }
        TicketTarget target = TicketTarget.at(home(post)).withTransfer(null, unprotected.orElseThrow(), armor, 1);
        return MoreActions.submit(post, ticket, BoardCase.TRANSFER_ITEM, target);
    }

    private static boolean unarmored(SpiritEntity spirit) {
        return spirit.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && spirit.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && spirit.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && spirit.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static boolean armor(String item) {
        return item.endsWith("_helmet")
                || item.endsWith("_chestplate")
                || item.endsWith("_leggings")
                || item.endsWith("_boots");
    }

    private static BlockCoord home(GraphPost post) {
        return post.scan.home();
    }
}

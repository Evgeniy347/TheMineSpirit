package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StagePlans;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class StageBlocks {
    public static final String SUPPORT = "minecraft:dirt";
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int CLEAR_ABOVE = 2;
    private static final int POST_DRIFT = 3;

    private StageBlocks() {}

    public static List<PlannedBlock> of(ServerLevel level, BoardCase boardCase, StageSite site, int part) {
        return switch (boardCase) {
            case BUILD_FENCE -> fence(level, site, part);
            case LIGHTING -> torches(level, site);
            case PLANT_GROVE -> grove(level, site);
            case STOCK_CHESTS -> StagePlans.yardBlocks(site, part);
            case CRAFT_FURNACE -> List.of(new PlannedBlock(site.furnace(), StagePlans.FURNACE));
            case BUILD_HOUSE -> palisade(level, site);
            default -> List.of();
        };
    }

    public static int postY(ServerLevel level, StageSite site, ColumnMask.Cell post) {
        int base = GroundProbe.feet(level, post.x(), post.z(), site.anchor().y());
        for (int[] side : SIDES) {
            int x = post.x() + side[0];
            int z = post.z() + side[1];
            if (!site.inside(x, z)) {
                base = Math.max(base, GroundProbe.feet(level, x, z, base));
            }
        }
        return base;
    }

    private static List<PlannedBlock> fence(ServerLevel level, StageSite site, int part) {
        FenceLine ring = site.ring();
        int size = ring.posts().size();
        int parts = TicketPlans.FENCE_PARTS;
        int from = part >= 1 && part <= parts ? (part - 1) * size / parts : 0;
        int to = part >= 1 && part <= parts ? part * size / parts : size;
        List<PlannedBlock> blocks = new ArrayList<>();
        Set<ColumnMask.Cell> torchPosts = Set.copyOf(site.torchPosts());
        for (int index = from; index < to; index++) {
            ColumnMask.Cell post = ring.posts().get(index);
            int ground =
                    GroundProbe.feet(level, post.x(), post.z(), site.anchor().y());
            int planned = postY(level, site, post);
            int base = builtPost(level, post, planned);
            if (base != planned) {
                ground = base;
            }
            for (int y = base; y <= base + CLEAR_ABOVE; y++) {
                if (blocking(level.getBlockState(new BlockPos(post.x(), y, post.z())))) {
                    blocks.add(new PlannedBlock(new BlockCoord(post.x(), y, post.z()), StagePlans.AIR));
                }
            }
            for (int y = ground; y < base; y++) {
                blocks.add(new PlannedBlock(new BlockCoord(post.x(), y, post.z()), SUPPORT));
            }
            String block = !ring.isGate(index)
                    ? StagePlans.FENCE
                    : ring.alongX(index) ? StagePlans.GATE_NORTH : StagePlans.GATE_EAST;
            blocks.add(new PlannedBlock(new BlockCoord(post.x(), base, post.z()), block));
            if (torchPosts.contains(post)) {
                blocks.add(new PlannedBlock(new BlockCoord(post.x(), base + 1, post.z()), StagePlans.TORCH));
            }
        }
        return List.copyOf(blocks);
    }

    private static List<PlannedBlock> torches(ServerLevel level, StageSite site) {
        return grounded(
                level,
                site,
                site.groundTorches().stream()
                        .map(cell -> new PlannedBlock(cell, StagePlans.TORCH))
                        .toList());
    }

    private static List<PlannedBlock> palisade(ServerLevel level, StageSite site) {
        List<PlannedBlock> blocks = new ArrayList<>(StagePlans.palisade(site));
        blocks.addAll(grounded(
                level,
                site,
                site.palisadeTorches().stream()
                        .map(cell -> new PlannedBlock(cell, StagePlans.TORCH))
                        .toList()));
        return List.copyOf(blocks);
    }

    public static int builtPost(ServerLevel level, ColumnMask.Cell post, int planned) {
        for (int shift = 0; shift <= POST_DRIFT; shift++) {
            for (int y : new int[] {planned - shift, planned + shift}) {
                BlockState state = level.getBlockState(new BlockPos(post.x(), y, post.z()));
                if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)) {
                    return y;
                }
            }
        }
        return planned;
    }

    private static List<PlannedBlock> grove(ServerLevel level, StageSite site) {
        String wood =
                SettlementPlotStore.get(level).plot().map(plot -> plot.wood()).orElseThrow();
        List<PlannedBlock> blocks = new ArrayList<>();
        for (BlockCoord spot : site.saplings()) {
            int y = GroundProbe.feet(level, spot.x(), spot.z(), site.anchor().y());
            blocks.add(new PlannedBlock(new BlockCoord(spot.x(), y, spot.z()), StagePlans.sapling(wood)));
        }
        return List.copyOf(blocks);
    }

    private static List<PlannedBlock> grounded(ServerLevel level, StageSite site, List<PlannedBlock> blocks) {
        return blocks.stream()
                .map(block -> new PlannedBlock(
                        new BlockCoord(
                                block.at().x(),
                                GroundProbe.feet(
                                                level,
                                                block.at().x(),
                                                block.at().z(),
                                                site.anchor().y())
                                        + block.at().y()
                                        - site.anchor().y(),
                                block.at().z()),
                        block.block()))
                .toList();
    }

    private static boolean blocking(BlockState state) {
        return !state.isAir()
                && !state.canBeReplaced()
                && !state.is(BlockTags.FENCES)
                && !state.is(BlockTags.FENCE_GATES)
                && !state.is(Blocks.TORCH);
    }
}

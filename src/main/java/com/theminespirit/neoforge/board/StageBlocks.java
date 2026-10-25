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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class StageBlocks {
    public static final String SUPPORT = "minecraft:dirt";
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int CLEAR_ABOVE = 2;

    private StageBlocks() {}

    public static List<PlannedBlock> of(ServerLevel level, BoardCase boardCase, StageSite site, int part) {
        return switch (boardCase) {
            case BUILD_FENCE -> fence(level, site, part);
            case LIGHTING -> torches(level, site);
            case PLANT_GROVE -> grove(level, site);
            case STOCK_CHESTS -> grounded(level, site, StagePlans.yardBlocks(site, part));
            case CRAFT_FURNACE -> grounded(level, site, List.of(new PlannedBlock(site.furnace(), StagePlans.FURNACE)));
            case BUILD_HOUSE -> grounded(level, site, StagePlans.palisade(site));
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
        for (int index = from; index < to; index++) {
            ColumnMask.Cell post = ring.posts().get(index);
            int ground =
                    GroundProbe.feet(level, post.x(), post.z(), site.anchor().y());
            int base = postY(level, site, post);
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
        }
        return List.copyOf(blocks);
    }

    private static List<PlannedBlock> torches(ServerLevel level, StageSite site) {
        List<PlannedBlock> blocks = new ArrayList<>();
        for (ColumnMask.Cell post : site.torchPosts()) {
            int base = postY(level, site, post);
            if (level.getBlockState(new BlockPos(post.x(), base, post.z())).is(BlockTags.FENCES)) {
                blocks.add(new PlannedBlock(new BlockCoord(post.x(), base + 1, post.z()), StagePlans.TORCH));
            }
        }
        List<PlannedBlock> planned = site.groundTorches().stream()
                .map(cell -> new PlannedBlock(cell, StagePlans.TORCH))
                .toList();
        for (PlannedBlock torch : grounded(level, site, planned)) {
            BlockCoord ground = torch.at();
            BlockPos below = new BlockPos(ground.x(), ground.y() - 1, ground.z());
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                blocks.add(new PlannedBlock(ground, StagePlans.TORCH));
            }
        }
        return List.copyOf(blocks);
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

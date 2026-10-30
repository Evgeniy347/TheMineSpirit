package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.SchemeBuild;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.StreetWork;
import com.theminespirit.core.town.WallLayout;
import com.theminespirit.core.town.WallPiece;
import com.theminespirit.core.town.YardScheme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class StageBuilds {
    public static final String SUPPORT = "minecraft:dirt";
    private static final String AIR = "minecraft:air";
    private static final int OLD_POST_RANGE = 3;

    private StageBuilds() {}

    public static StageWork of(ServerLevel level, BoardCase boardCase, StageSite site, int part) {
        return switch (boardCase) {
            case BUILD_FENCE -> fence(level, site, part);
            case LIGHTING -> new StageWork(site, List.of(), YardScheme.lighting(site));
            case STOCK_CHESTS -> new StageWork(site, List.of(), YardScheme.stock(site, part));
            case CRAFT_FURNACE -> new StageWork(site, List.of(), List.of(YardScheme.furnace(site)));
            case BUILD_STREETS -> streets(level, site);
            default -> new StageWork(site, List.of());
        };
    }

    private static StageWork streets(ServerLevel level, StageSite site) {
        List<SchemeBuild> builds = SettlementPlotStore.get(level)
                .town()
                .map(town -> StreetWork.of(
                        site,
                        town,
                        DataTables.INSTANCE.town().current().streets(),
                        DataTables.INSTANCE.presets().current(),
                        StreetWork.level(DataTables.INSTANCE.stages().current(), site.stage())))
                .orElseThrow(() -> new IllegalStateException(
                        "Места этапа есть, а плана поселения нет: улицам этапа не по чему лечь"));
        return new StageWork(site, List.of(), builds);
    }

    private static WallLayout fenceLayout(StageSite site) {
        return WallLayout.of(DataTables.INSTANCE.presets().current(), StageSchemes.fence(site.stage()));
    }

    record FencePart(StageWork work, boolean hasPieces) {}

    private static StageWork fence(ServerLevel level, StageSite site, int part) {
        return fencePart(level, site, part).work();
    }

    static FencePart fencePart(ServerLevel level, StageSite site, int part) {
        FenceLine ring = site.ring();
        List<WallPiece> pieces = fenceLayout(site).closed(ring.posts(), ring.gates());
        int size = pieces.size();
        int parts = TicketPlans.FENCE_PARTS;
        int from = part >= 1 && part <= parts ? (part - 1) * size / parts : 0;
        int to = part >= 1 && part <= parts ? part * size / parts : size;
        int levelY = SiteLevels.level(site);
        int fenceY = SiteLevels.fenceY(site);
        IntBinaryOperator tops = (x, z) -> GroundProbe.top(level, x, z, fenceY);
        Set<ColumnMask.Cell> ready = new HashSet<>(SiteLevels.readyPosts(site, tops));
        for (ColumnMask.Cell post : ring.posts()) {
            if (SiteLevels.fenceReady(site, post, tops, fenceAtLevel(level, post, fenceY))) {
                ready.add(post);
            }
        }
        List<PlannedBlock> blocks = new ArrayList<>();
        List<SchemeBuild> builds = new ArrayList<>();
        for (WallPiece piece : pieces.subList(from, to)) {
            List<ColumnMask.Cell> posts = posts(ring, piece);
            if (!ready.containsAll(posts)) {
                continue;
            }
            for (ColumnMask.Cell post : posts) {
                if (SiteLevels.needsSupport(site, post, tops)) {
                    blocks.add(new PlannedBlock(new BlockCoord(post.x(), levelY, post.z()), SUPPORT));
                }
                oldFence(level, post, fenceY)
                        .forEach(at -> blocks.add(new PlannedBlock(at, at.y() == levelY ? SUPPORT : AIR)));
            }
            builds.add(new SchemeBuild(piece.scheme(), new BlockCoord(piece.x(), fenceY, piece.z()), piece.rotation()));
        }
        return new FencePart(new StageWork(site, blocks, builds), to > from);
    }

    private static List<ColumnMask.Cell> posts(FenceLine ring, WallPiece piece) {
        List<ColumnMask.Cell> cells = new ArrayList<>(piece.length());
        for (int offset = 0; offset < piece.length(); offset++) {
            cells.add(ring.posts().get((piece.from() + offset) % ring.posts().size()));
        }
        return cells;
    }

    private static boolean fenceAtLevel(ServerLevel level, ColumnMask.Cell post, int fenceY) {
        BlockState state = level.getBlockState(new BlockPos(post.x(), fenceY, post.z()));
        return state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES);
    }

    private static List<BlockCoord> oldFence(ServerLevel level, ColumnMask.Cell post, int fenceY) {
        List<BlockCoord> found = new ArrayList<>();
        for (int y = fenceY - OLD_POST_RANGE; y <= fenceY + OLD_POST_RANGE; y++) {
            if (y == fenceY) {
                continue;
            }
            BlockState state = level.getBlockState(new BlockPos(post.x(), y, post.z()));
            if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)) {
                found.add(new BlockCoord(post.x(), y, post.z()));
            }
        }
        return found;
    }
}

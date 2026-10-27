package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.SchemeBuild;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.WallLayout;
import com.theminespirit.core.town.WallPiece;
import com.theminespirit.core.town.YardScheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class StageBuilds {
    public static final String SUPPORT = "minecraft:dirt";
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int POST_DRIFT = 3;

    private StageBuilds() {}

    public static StageWork of(ServerLevel level, BoardCase boardCase, StageSite site, int part) {
        return switch (boardCase) {
            case BUILD_FENCE -> fence(level, site, part);
            case LIGHTING -> new StageWork(site, List.of(), YardScheme.lighting(site));
            case STOCK_CHESTS -> new StageWork(site, List.of(), YardScheme.stock(site, part));
            case CRAFT_FURNACE -> new StageWork(site, List.of(), List.of(YardScheme.furnace(site)));
            default -> new StageWork(site, List.of());
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

    private static WallLayout fenceLayout() {
        return WallLayout.of(DataTables.INSTANCE.presets().current(), StageSchemes.FENCE);
    }

    private static StageWork fence(ServerLevel level, StageSite site, int part) {
        FenceLine ring = site.ring();
        List<WallPiece> pieces = fenceLayout().closed(ring.posts(), ring.gates());
        int size = pieces.size();
        int parts = TicketPlans.FENCE_PARTS;
        int from = part >= 1 && part <= parts ? (part - 1) * size / parts : 0;
        int to = part >= 1 && part <= parts ? part * size / parts : size;
        List<PlannedBlock> supports = new ArrayList<>();
        List<SchemeBuild> builds = new ArrayList<>();
        for (WallPiece piece : pieces.subList(from, to)) {
            List<ColumnMask.Cell> cells = new ArrayList<>();
            List<Integer> bases = new ArrayList<>();
            List<Integer> grounds = new ArrayList<>();
            for (int offset = 0; offset < piece.length(); offset++) {
                ColumnMask.Cell post =
                        ring.posts().get((piece.from() + offset) % ring.posts().size());
                int planned = postY(level, site, post);
                int base = builtPost(level, post, planned);
                cells.add(post);
                bases.add(base);
                grounds.add(
                        base != planned
                                ? base
                                : GroundProbe.feet(
                                        level, post.x(), post.z(), site.anchor().y()));
            }
            int y = bases.stream().mapToInt(Integer::intValue).max().orElseThrow();
            for (int index = 0; index < cells.size(); index++) {
                ColumnMask.Cell post = cells.get(index);
                for (int ground = grounds.get(index); ground < y; ground++) {
                    supports.add(new PlannedBlock(new BlockCoord(post.x(), ground, post.z()), SUPPORT));
                }
            }
            builds.add(new SchemeBuild(piece.scheme(), new BlockCoord(piece.x(), y, piece.z()), piece.rotation()));
        }
        return new StageWork(site, supports, builds);
    }
}

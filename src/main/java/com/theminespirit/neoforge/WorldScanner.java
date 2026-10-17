package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ContainerRef;
import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.OpenBlock;
import com.theminespirit.core.observation.OpenBlocks;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.SectionCursor;
import com.theminespirit.core.observation.SectionSlice;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.perception.ScanFocus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WorldScanner {
    private static final int UNLIMITED_SLICES = Integer.MAX_VALUE;

    private WorldScanner() {
    }

    public record Scan(WorldObservation observation, int scannedPositions, int skippedChunks) {
    }

    public static Scan observe(Level level, String botId, BlockCoord center, ScanRadius radius,
            Container inventory) {
        Session session = new Session(level, botId, center, radius, IdFilter.matchAll(), inventory);
        while (!session.done()) {
            session.step(UNLIMITED_SLICES);
        }
        return session.result();
    }

    public static final class Session {
        private final Level level;
        private final String botId;
        private final BlockCoord center;
        private final int radius;
        private final Container inventory;
        private final Optional<BlockBox> box;
        private final SectionCursor cursor;
        private final ScanFocus focus;
        private final IdFilter entityFilter;
        private final Set<Block> filteredBlocks;
        private final Map<Block, int[]> blockCounts = new IdentityHashMap<>();
        private final List<ContainerRef> containers = new ArrayList<>();
        private final List<OpenBlock> openBlocks = new ArrayList<>();
        private final int openBlockLimit;
        private final Set<Long> visitedChunks = new HashSet<>();
        private final Set<Long> skippedChunks = new HashSet<>();
        private int examinedPositions;
        private int stepCalls;
        private int entityQueries;
        private Scan result;

        public Session(Level level, String botId, BlockCoord center, ScanRadius radius, IdFilter blockFilter,
                Container inventory) {
            this(level, botId, center, radius, blockFilter, inventory, ScanFocus.ALL, 0);
        }

        public Session(Level level, String botId, BlockCoord center, ScanRadius radius, IdFilter filter,
                Container inventory, ScanFocus focus) {
            this(level, botId, center, radius, filter, inventory, focus, 0);
        }

        public Session(Level level, String botId, BlockCoord center, ScanRadius radius, IdFilter filter,
                Container inventory, ScanFocus focus, int openBlockLimit) {
            this.level = level;
            this.botId = botId;
            this.center = center;
            this.radius = radius.value();
            this.inventory = inventory;
            this.box = LoadedWorld.cube(level, center, this.radius);
            this.cursor = focus.needsSlices() ? LoadedWorld.cursor(box, center) : SectionCursor.empty();
            this.focus = focus;
            this.entityFilter = focus.filtersEntities() ? filter : IdFilter.matchAll();
            this.filteredBlocks = focus.collectsBlocks() && !filter.matchesAll() ? BlockSets.resolve(filter) : null;
            this.openBlockLimit = Math.max(0, openBlockLimit);
        }

        public void step(int maxSlices) {
            stepCalls++;
            for (SectionSlice slice : cursor.next(maxSlices)) {
                scan(slice);
            }
        }

        public boolean done() {
            return cursor.isDone();
        }

        public int examinedPositions() {
            return examinedPositions;
        }

        public int stepCalls() {
            return stepCalls;
        }

        public int entityQueries() {
            return entityQueries;
        }

        public Scan result() {
            if (!done()) {
                throw new IllegalStateException("Скан не закончен: осталось срезов " + cursor.remaining()
                        + ", результат читают после done()");
            }
            if (result == null) {
                result = finish();
            }
            return result;
        }

        private void scan(SectionSlice slice) {
            LevelChunk chunk = LoadedWorld.chunk(level, slice.sectionX(), slice.sectionZ(), skippedChunks);
            if (chunk == null) {
                return;
            }
            if (visitedChunks.add(chunk.getPos().toLong()) && focus.collectsContainers()) {
                collectContainers(chunk);
            }
            if (!focus.collectsBlocks()) {
                return;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(slice.sectionY()));
            if (section.hasOnlyAir()
                    || (filteredBlocks != null && !section.maybeHas(state -> filteredBlocks.contains(state.getBlock())))) {
                return;
            }
            BlockBox bounds = slice.bounds();
            for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
                for (int y = bounds.min().y(); y <= bounds.max().y(); y++) {
                    for (int z = bounds.min().z(); z <= bounds.max().z(); z++) {
                        examinedPositions++;
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (counts(state)) {
                            blockCounts.computeIfAbsent(state.getBlock(), block -> new int[1])[0]++;
                        }
                        collectOpenBlock(state, x, y, z);
                    }
                }
            }
        }

        private void collectOpenBlock(BlockState state, int x, int y, int z) {
            if (openBlockLimit == 0 || state.isAir()) {
                return;
            }
            String item = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (filteredBlocks != null && !filteredBlocks.contains(state.getBlock())) {
                return;
            }
            if (!OpenBlocks.isImportant(item)) {
                return;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!hasAirNeighbor(pos)) {
                return;
            }
            double dx = x - center.x();
            double dy = y - center.y();
            double dz = z - center.z();
            double distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0) / 10.0;
            OpenBlocks.keepNearest(openBlocks, new OpenBlock(item, toCore(pos), distance), openBlockLimit);
        }

        private boolean hasAirNeighbor(BlockPos pos) {
            return level.getBlockState(pos.offset(1, 0, 0)).isAir()
                    || level.getBlockState(pos.offset(-1, 0, 0)).isAir()
                    || level.getBlockState(pos.offset(0, 1, 0)).isAir()
                    || level.getBlockState(pos.offset(0, -1, 0)).isAir()
                    || level.getBlockState(pos.offset(0, 0, 1)).isAir()
                    || level.getBlockState(pos.offset(0, 0, -1)).isAir();
        }

        private boolean counts(BlockState state) {
            if (filteredBlocks == null) {
                return !state.isAir();
            }
            return filteredBlocks.contains(state.getBlock());
        }

        private void collectContainers(LevelChunk chunk) {
            BlockBox cube = box.orElseThrow();
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                BlockPos pos = entry.getKey();
                if (cube.contains(toCore(pos)) && entry.getValue() instanceof Container) {
                    containers.add(new ContainerRef(
                            BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlockState().getBlock()).toString(),
                            toCore(pos)));
                }
            }
        }

        private Scan finish() {
            List<ItemCount> blocks = new ArrayList<>();
            blockCounts.forEach((block, count) ->
                    blocks.add(new ItemCount(BuiltInRegistries.BLOCK.getKey(block).toString(), count[0])));
            List<EntityRef> entities = focus.collectsEntities() ? findEntities() : List.of();
            WorldObservation observation = new WorldObservation(botId, level.getGameTime(), center,
                    inventoryEntries(inventory), blocks, entities, containers, radius, null, List.of(), List.of(),
                    null, openBlocks);
            return new Scan(observation, examinedPositions, skippedChunks.size());
        }

        private List<EntityRef> findEntities() {
            entityQueries++;
            List<EntityRef> entities = new ArrayList<>();
            if (box.isEmpty()) {
                return entities;
            }
            BlockBox cube = box.get();
            Vec3 centerPos = new Vec3(center.x() + 0.5, center.y() + 0.5, center.z() + 0.5);
            AABB bounds = new AABB(cube.min().x(), cube.min().y(), cube.min().z(),
                    cube.max().x() + 1.0, cube.max().y() + 1.0, cube.max().z() + 1.0);
            for (Entity entity : level.getEntities((Entity) null, bounds, e -> !e.isRemoved())) {
                String type = EntityType.getKey(entity.getType()).toString();
                if (!entityFilter.matches(type)) {
                    continue;
                }
                entities.add(new EntityRef(type, toCore(entity.blockPosition()),
                        entity.position().distanceTo(centerPos)));
            }
            return entities;
        }
    }

    public static List<ItemCount> inventoryEntries(Container inventory) {
        List<ItemCount> entries = new ArrayList<>();
        if (inventory == null) {
            return entries;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                entries.add(new ItemCount(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
            }
        }
        return entries;
    }

    private static BlockCoord toCore(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}

package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StreetLayout {
    public static final String PATCH = "street_patch";
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final boolean[] STRAIGHT = {true, false, true, false};
    private static final boolean[] TURN = {false, true, true, false};
    private static final boolean[] TEE = {false, true, true, true};

    private final int patchX;
    private final int patchZ;

    public StreetLayout(Schematic patch) {
        Objects.requireNonNull(patch, "Схема заплатки улицы не может быть null: ею добиваются края улиц");
        Schematic.Entry cell = patch.blocks().get(0);
        this.patchX = cell.dx();
        this.patchZ = cell.dz();
    }

    public static StreetLayout of(PresetLibrary presets) {
        return new StreetLayout(presets.byId(PATCH)
                .orElseThrow(() ->
                        new IllegalStateException("presets.json: нет схемы '" + PATCH + "' — края улиц нечем добить"))
                .schematic());
    }

    public List<StreetTile> town(
            TownStreets streets, int centerX, int centerZ, TownRules.Streets rules, ColumnMask within) {
        Objects.requireNonNull(streets, "Улицы плана не могут быть null");
        Objects.requireNonNull(rules, "Правила улиц не могут быть null: ширина плитки — ширина улицы");
        List<StreetTile> tiles = new ArrayList<>(lay(
                streets.main().intersect(within),
                centerX - rules.main() / 2,
                centerZ - rules.main() / 2,
                rules.main()));
        tiles.addAll(lay(
                streets.side().intersect(within),
                centerX - rules.side() / 2,
                centerZ - rules.side() / 2,
                rules.side()));
        tiles.addAll(lay(
                streets.ring().intersect(within),
                centerX - rules.ring() / 2,
                centerZ - rules.ring() / 2,
                rules.ring()));
        return List.copyOf(tiles);
    }

    public List<StreetTile> lay(ColumnMask street, int gridX, int gridZ, int size) {
        Objects.requireNonNull(street, "Улица не может быть null: раскладывать нечего");
        if (size < 1) {
            throw new IllegalArgumentException("Размер плитки улицы " + size + " меньше 1");
        }
        List<StreetTile> tiles = new ArrayList<>();
        if (street.isEmpty()) {
            return tiles;
        }
        int fromX = slot(street.minX(), gridX, size);
        int fromZ = slot(street.minZ(), gridZ, size);
        int toX = slot(street.minX() + street.width() - 1, gridX, size);
        int toZ = slot(street.minZ() + street.depth() - 1, gridZ, size);
        for (int slotZ = fromZ; slotZ <= toZ; slotZ++) {
            for (int slotX = fromX; slotX <= toX; slotX++) {
                int x = gridX + slotX * size;
                int z = gridZ + slotZ * size;
                if (count(street, x, z, size) == size * size) {
                    tiles.add(tile(street, x, z, size));
                } else {
                    patches(street, x, z, size, tiles);
                }
            }
        }
        return List.copyOf(tiles);
    }

    private StreetTile tile(ColumnMask street, int x, int z, int size) {
        boolean[] open = new boolean[SIDES.length];
        int sides = 0;
        for (int side = 0; side < SIDES.length; side++) {
            open[side] = count(street, x + SIDES[side][0] * size, z + SIDES[side][1] * size, size) >= size;
            sides += open[side] ? 1 : 0;
        }
        if (sides == SIDES.length) {
            return new StreetTile("street" + size + "_cross", BuildRotation.NONE, x, z);
        }
        boolean[] shape = sides == 3 ? TEE : sides == 2 && open[0] != open[2] ? TURN : STRAIGHT;
        String kind = sides == 3 ? "_t" : sides == 2 && open[0] != open[2] ? "_turn" : "_straight";
        for (BuildRotation rotation : BuildRotation.values()) {
            if (matches(shape, open, rotation.quarterTurns(), sides)) {
                return new StreetTile("street" + size + kind, rotation, x, z);
            }
        }
        return new StreetTile("street" + size + "_straight", BuildRotation.NONE, x, z);
    }

    private static boolean matches(boolean[] shape, boolean[] open, int turns, int sides) {
        if (sides < 2) {
            int axis = open[1] || open[3] ? 1 : 0;
            return Math.floorMod(turns, 2) == axis;
        }
        for (int side = 0; side < SIDES.length; side++) {
            if (shape[side] != open[(side + turns) % SIDES.length]) {
                return false;
            }
        }
        return true;
    }

    private void patches(ColumnMask street, int x, int z, int size, List<StreetTile> tiles) {
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                if (street.contains(x + dx, z + dz)) {
                    tiles.add(new StreetTile(PATCH, BuildRotation.NONE, x + dx - patchX, z + dz - patchZ));
                }
            }
        }
    }

    private static int count(ColumnMask street, int x, int z, int size) {
        int count = 0;
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                count += street.contains(x + dx, z + dz) ? 1 : 0;
            }
        }
        return count;
    }

    private static int slot(int at, int grid, int size) {
        return Math.floorDiv(at - grid, size);
    }
}

package absentialis.world.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * What generation produced and what its validator found (pipeline stages 11 and 12). The quality gates assert on
 * these numbers; the debug views draw the listed cells. A "repair" is a defect the validator fixed and re-checked;
 * a "rejection" is a building that could not be made valid and was removed, its lot returned to open ground.
 */
public final class GenReport {
    public int buildings, landmarks, rooms, doorways, roomsDropped;
    public int roomsUnreachable, buildingsRepaired, buildingsRejected, doorsBlocked, doorsFixed;
    public int roadCellsBlocked, roadCellsCleared, roadSurfaceFixed, clippedWrites;
    public int floatingBlocks, floatingRemoved, overlaps, materialFaults;
    public int stairsUnreachable, navIslands, navIslandCells, coverPoints;
    public int voidColumns, cliffs, trenchCells, edgeCliffs;
    public long planMs, buildMs, validateMs, totalMs;
    /** Cells worth looking at in the invalid-geometry view: {x, y, z, kind}. */
    public final List<int[]> flagged = new ArrayList<>();
    public final List<String> lines = new ArrayList<>();

    public void flag(int x, int y, int z, int kind) { if (flagged.size() < 4000) flagged.add(new int[]{x, y, z, kind}); }
    public void note(String s) { lines.add(s); }

    /** True when nothing is left that the brief calls invalid. */
    public boolean clean() {
        return roomsUnreachable == 0 && roadCellsBlocked == 0 && floatingBlocks == 0 && overlaps == 0 && doorsBlocked == 0 && voidColumns == 0 && stairsUnreachable == 0;
    }

    @Override public String toString() {
        return String.format("%d buildings (%d landmark compounds), %d rooms, %d doorways; repaired %d, rejected %d; unreachable rooms %d, blocked doors %d (fixed %d), "
                        + "road cells cleared %d / still blocked %d, floating removed %d / left %d, overlaps %d, material faults %d, void columns %d, cliffs %d",
                buildings, landmarks, rooms, doorways, buildingsRepaired, buildingsRejected, roomsUnreachable, doorsBlocked, doorsFixed,
                roadCellsCleared, roadCellsBlocked, floatingRemoved, floatingBlocks, overlaps, materialFaults, voidColumns, cliffs);
    }
}

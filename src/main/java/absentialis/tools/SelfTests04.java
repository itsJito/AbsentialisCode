package absentialis.tools;

import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;

/** Version 0.4 quality gates (headless). Each system added in 0.4 registers its checks here. */
public final class SelfTests04 {
    private SelfTests04() {}

    interface Check { void check(String name, boolean ok, String detail); }

    static void run(String which, boolean all, Check c) {
        if (all || which.contains("v04") || which.contains("icon")) SelfTests.guard(() -> { icons(c); });
        if (all || which.contains("v04") || which.contains("vehicle")) SelfTests.guard(() -> { VehicleTests.run(c); });
        if (all || which.contains("v04") || which.contains("system")) SelfTests.guard(() -> { SystemTests04.run(c); });
    }

    /** Every weapon's icon must fill the footprint it occupies: the art spans most of the long side and a fair part of the short one. */
    private static void icons(Check c) {
        System.out.println("[v0.4 inventory: every weapon fills its tile footprint]");
        StringBuilder bad = new StringBuilder();
        int n = 0;
        for (ItemDef d : Content.ITEMS.values()) {
            if (d.weapon == null) continue;
            ItemInstance it = new ItemInstance(-7, d.id);
            int[] size = new int[2];
            int[] img = absentialis.ui.ItemIcons.rasterize(absentialis.ui.ItemModels.of(it), d, it.baseW(), it.baseH(), size);
            float[] cov = absentialis.ui.ItemIcons.coverage(img, size[0], size[1]);
            n++;
            // as large as its proportions allow (it touches the footprint in one direction) and never swimming in the other
            if (Math.max(cov[0], cov[1]) < 0.8f || Math.min(cov[0], cov[1]) < 0.6f) bad.append(' ').append(d.id).append(String.format("(%.2f x %.2f)", cov[0], cov[1]));
        }
        c.check("every weapon icon fills its footprint (>= 80% one way, >= 60% the other)", bad.length() == 0, n + " weapons" + bad);
    }
}

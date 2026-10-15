package absentialis.scene;

import absentialis.Version;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.ui.Ui;
import absentialis.vehicle.CarHandlingData;
import absentialis.vehicle.CarVehicle;
import absentialis.vehicle.CarVisualController;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shop's VEHICLES department and the player's garage in one view. Each of the seven families is a card with
 * the model turning on a plinth and what its handling data means in practice - top speed, pull, grip, how it
 * copes off the road, how much punishment it takes. A vehicle that has been bought stays owned; one of them can
 * be marked to bring on the next operation, where it waits by the insertion point. Drive it out, or leave it
 * parked and extract on foot, and it comes home. Die, and it stays in the Zone.
 */
public final class GaragePanel {
    private final Ctx c;
    private String selected;
    private float spin, scroll;
    private final Map<String, CarVisualController> previews = new HashMap<>();

    public GaragePanel(Ctx c) { this.c = c; }

    private CarVisualController preview(CarHandlingData d) {
        return previews.computeIfAbsent(d.id, k -> { CarVehicle v = new CarVehicle(d); return new CarVisualController(v); });
    }

    /** Handling data as five 0..1 ratings a buyer can compare. */
    public static float[] ratings(CarHandlingData d) {
        float speed = (d.maxVelocity - 26) / 20f;
        float pull = (d.engineAcceleration * d.firstGearBoost - 4.5f) / 5.5f;
        float grip = (d.tractionMultiplier * (0.6f + d.tractionLoss * 0.5f) - 0.85f) / 0.3f + (1f - d.comY) * 0.35f;
        float rough = (d.suspensionTravel() - 0.36f) / 0.32f * 0.6f + (d.wheelRadius - 0.3f) / 0.15f * 0.25f + (d.drive == CarHandlingData.Drive.FOUR ? 0.25f : 0) + (d.improvedSand ? 0.1f : 0);
        float tough = (d.mass - 1000) / 1800f * 0.6f + (1.05f - d.collisionDamage) * 0.9f;
        float[] r = {speed, pull, grip, rough, tough};
        for (int i = 0; i < r.length; i++) r[i] = Math.max(0.06f, Math.min(1, r[i]));
        return r;
    }

    private static final String[] RATING = {"TOP SPEED", "ACCELERATION", "ROAD HOLDING", "OFF-ROAD", "TOUGHNESS"};

    public void draw(Ui ui, Profile p, float x, float y, float w, float h) {
        spin += ui.dt * 0.6f;
        List<CarHandlingData> all = CarHandlingData.all();
        if (selected == null || CarHandlingData.get(selected) == null) selected = p.deployVehicle.isEmpty() ? all.get(0).id : p.deployVehicle;
        float showW = Math.min(ui.s(430), w * 0.38f), listW = w - showW - ui.s(18);
        // ---- the range, grouped by family
        ui.text("VEHICLES", x, y, 18, Ui.TXT_HI);
        ui.textR(p.garage.size() + " owned   -   " + (p.deployVehicle.isEmpty() ? "deploying on foot" : "bringing " + CarHandlingData.get(p.deployVehicle).name), x + listW, y + ui.s(4), 12, p.deployVehicle.isEmpty() ? Ui.TXT_DIM : Ui.AMBER);
        float ly = y + ui.s(34), lh = h - ui.s(34), rowH = ui.s(64);
        float total = 0;
        String fam = "";
        for (CarHandlingData d : sorted(all)) { if (!d.family.equals(fam)) { total += ui.s(26); fam = d.family; } total += rowH + ui.s(6); }
        if (ui.hover(x, ly, listW, lh)) scroll -= (float) c.in.scroll * ui.s(50);
        scroll = Math.max(0, Math.min(Math.max(0, total - lh), scroll));
        ui.b.scissor((int) x - 2, (int) ly, (int) listW + 4, (int) lh);
        float cy = ly - scroll;
        fam = "";
        for (CarHandlingData d : sorted(all)) {
            if (!d.family.equals(fam)) { fam = d.family; ui.text(fam.toUpperCase(), x + ui.s(2), cy + ui.s(6), 11, Ui.AMBER); ui.b.rect(x, cy + ui.s(22), listW, 1, 0xFF3A3424); cy += ui.s(26); }
            boolean sel = d.id.equals(selected), owned = p.garage.contains(d.id), locked = p.prog.rank() < d.rank && !owned, deploy = d.id.equals(p.deployVehicle);
            boolean hov = ui.hover(x, cy, listW, rowH) && ui.my > ly && ui.my < ly + lh;
            float k = ui.ease("gar" + d.id, hov || sel);
            ui.b.rect(x, cy, listW, rowH, sel ? 0xFF2A2416 : Ui.mix(0xFF14161A, 0xFF1E2024, k));
            ui.b.rect(x, cy, ui.s(4), rowH, deploy ? Ui.GREEN : owned ? Ui.AMBER : locked ? 0xFF3A3A3A : 0xFF5A5446);
            ui.text(d.name, x + ui.s(16), cy + ui.s(9), 16, locked ? Ui.TXT_DIM : Ui.TXT_HI);
            ui.text(drive(d) + "   -   " + Math.round(d.maxVelocity * 3.6f) + " km/h   -   " + Math.round(d.mass) + " kg", x + ui.s(16), cy + ui.s(34), 11, Ui.TXT_DIM);
            String tag = deploy ? "DEPLOYING" : owned ? "OWNED" : locked ? "RANK " + d.rank : Version.coins(d.price);
            ui.textR(tag, x + listW - ui.s(14), cy + ui.s(20), 15, deploy ? Ui.GREEN : owned ? Ui.AMBER : locked ? Ui.TXT_DIM : p.scrip >= d.price ? Ui.TXT_HI : Ui.RED);
            if (hov && ui.click && !ui.consumed) { ui.consumed = true; selected = d.id; if (ui.sound != null) ui.sound.accept("ui_tab"); }
            cy += rowH + ui.s(6);
        }
        ui.b.noScissor();

        // ---- showcase
        CarHandlingData d = CarHandlingData.get(selected);
        float sx = x + listW + ui.s(18);
        ui.b.rect(sx, y, showW, h, 0xFF101214);
        ui.b.outline(sx, y, showW, h, 1, 0xFF3A3424);
        float ps = Math.min(showW - ui.s(40), h * 0.36f);
        ui.b.rect(sx + (showW - ps) / 2, y + ui.s(18) + ps * 0.74f, ps, ui.s(4), 0xFF2A2416);
        c.icons.drawModel(ui.b, preview(d).preview(), sx + (showW - ps) / 2, y + ui.s(10), ps, spin, 0.42f);
        float ty = y + ps + ui.s(18);
        ui.textC(d.name.toUpperCase(), sx + showW / 2, ty, 20, Ui.TXT_HI);
        ui.textC(d.family + "   -   " + drive(d) + "   -   " + d.gears + "-speed automatic, " + d.engineType, sx + showW / 2, ty + ui.s(28), 11, Ui.AMBER);
        ty += ui.s(56);
        float[] r = ratings(d);
        for (int i = 0; i < r.length; i++) {
            ui.text(RATING[i], sx + ui.s(22), ty, 11, Ui.TXT_DIM);
            ui.bar(sx + ui.s(140), ty + ui.s(3), showW - ui.s(162), ui.s(8), r[i], i == 4 ? 0xFF7DBA5C : 0xFFE2A034);
            ty += ui.s(20);
        }
        ty += ui.s(6);
        ty += ui.wrap(blurb(d), sx + ui.s(22), ty, showW - ui.s(44), 12, Ui.TXT) + ui.s(10);
        ui.wrap("Bring one vehicle per operation. It waits by your insertion point. Extract and it returns to the garage; die and it is lost with everything else you carried in.", sx + ui.s(22), ty, showW - ui.s(44), 11, Ui.TXT_DIM);
        // ---- buy / bring
        boolean owned = p.garage.contains(d.id), locked = p.prog.rank() < d.rank && !owned, deploy = d.id.equals(p.deployVehicle);
        float by = y + h - ui.s(58), bw = showW - ui.s(44);
        if (!owned) {
            boolean can = !locked && p.scrip >= d.price;
            if (ui.button("garbuy", sx + ui.s(22), by, bw, ui.s(44), locked ? "UNLOCKS AT RANK " + d.rank : "BUY   " + Version.coins(d.price), "coin", can)) {
                p.scrip -= d.price;
                p.garage.add(d.id);
                c.sfx("ui_buy", 0.7f);
                SaveSystem.save(p);
            }
            if (!locked && p.scrip < d.price) ui.textC("Short by " + Version.coins(d.price - p.scrip), sx + showW / 2, by - ui.s(20), 11, Ui.RED);
        } else if (ui.button("garuse", sx + ui.s(22), by, bw, ui.s(44), deploy ? "LEAVE IT IN THE GARAGE" : "BRING ON THE NEXT OPERATION", deploy ? "cross" : "flag", true)) {
            p.deployVehicle = deploy ? "" : d.id;
            c.sfx("car_door", 0.5f);
            SaveSystem.save(p);
        }
    }

    private static List<CarHandlingData> sorted(List<CarHandlingData> all) {
        List<CarHandlingData> l = new java.util.ArrayList<>(all);
        List<String> order = java.util.Arrays.asList(CarHandlingData.FAMILIES);
        l.sort((a, b) -> order.indexOf(a.family) != order.indexOf(b.family) ? Integer.compare(order.indexOf(a.family), order.indexOf(b.family)) : Integer.compare(a.price, b.price));
        return l;
    }

    private static String drive(CarHandlingData d) { return d.drive == CarHandlingData.Drive.FOUR ? "four-wheel drive" : d.drive == CarHandlingData.Drive.FRONT ? "front-wheel drive" : "rear-wheel drive"; }

    private static String blurb(CarHandlingData d) {
        return switch (d.model) {
            case "coupe" -> "Light, short and quick to turn. Front-wheel drive pulls it straight out of trouble; there is not much of it to hide behind.";
            case "sedan" -> "The ordinary car of the old city. Stable on the avenues, rear-wheel drive, and willing to step out if you ask too much in a corner.";
            case "pickup" -> "A long wheelbase with a light tail. Strong in a straight line, loose at the back when the bed is empty and the road is wet.";
            case "van" -> "Tall, heavy and slow to change its mind. It leans, it takes its time stopping - and it puts a lot of steel between you and the street.";
            case "suv" -> "Four-wheel drive and weight. Not fast off the line, hard to stop once it is moving, and at home on broken ground.";
            case "offroad" -> "Long-travel suspension, big tyres, four-wheel drive. Slow on tarmac and top-heavy if you throw it; nothing else crosses rubble like it.";
            case "ambulance" -> "A rescue unit: the van's bulk with an engine to move it and a light bar on the roof ([J] while driving). Heavy, high, and rolls if hurried.";
            case "patrol" -> "A government pursuit car. The quickest thing left in the city, with the brakes to match, and a light bar that tells everyone where you are.";
            default -> "";
        };
    }
}

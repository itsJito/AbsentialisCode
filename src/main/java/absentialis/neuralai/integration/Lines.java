package absentialis.neuralai.integration;

import absentialis.entity.Actor;
import absentialis.scene.Raid;

/**
 * What people say. NeuralAI asks for a line by the event it marks - contact, lost, flanking, a casualty - and this
 * gives the words in the voice of whoever is speaking (the cordon is clipped, contractors are flat, bandits are
 * loud, loners are frightened) together with the bark sample that goes with it. Every line says something true
 * about what the speaker knows; none of them is a message by itself (see group.ReportBus).
 */
final class Lines {
    private Lines() {}

    private static String pick(Raid r, String... s) { return s[r.rng.nextInt(s.length)]; }

    private static String buddy(Raid r, Actor a) {
        Actor best = null;
        float bd = 40;
        for (Actor m : r.nearby(a.x, a.z, 40)) {
            if (m == a || m.dead || m.isPlayer || m.squad != a.squad || !m.faction.equals(a.faction)) continue;
            float d = a.dist(m);
            if (d < bd) { bd = d; best = m; }
        }
        return best == null || best.name.isEmpty() ? "" : Relations.callsign(best);
    }

    /** {text, bark} for an event, or null when this speaker has nothing to say about it. */
    static String[] of(Raid r, Actor a, String event, String detail) {
        boolean mate = a.mate();
        Relations.Doctrine d = mate ? Relations.Doctrine.MERC : Relations.doc(a.faction).d();
        String b = buddy(r, a), dir = detail == null ? "" : detail;
        return switch (event) {
            case "contact" -> new String[]{mate ? pick(r, "Contact " + dir + "!", "Eyes on - " + dir + "!")
                    : d == Relations.Doctrine.MILITARY ? pick(r, "Contact " + dir + "!", "Eyes on, " + dir + "!")
                    : d == Relations.Doctrine.MERC ? pick(r, "Contact, " + dir + ". Moving on him.", "Target " + dir + ".")
                    : d == Relations.Doctrine.BANDIT ? pick(r, "There he is - " + dir + "!", "Got one, " + dir + "!")
                    : pick(r, "Someone's there - " + dir + "!", "I see him, " + dir + "!"), "bark_contact"};
            case "reacquired" -> new String[]{pick(r, "Got him again - " + dir + "!", "There! " + dir + " side!"), "bark_contact"};
            case "lost" -> new String[]{d == Relations.Doctrine.MILITARY ? pick(r, "Lost visual. Sweep the area.", "No contact - search pattern.")
                    : d == Relations.Doctrine.BANDIT ? pick(r, "Where'd he go?!", "He can't have gone far - spread out!")
                    : pick(r, "Lost him. Spread out.", "Lost him - last seen " + dir + "."), "bark_lost"};
            case "flanking" -> new String[]{pick(r, "Going round the " + dir + ".", (b.isEmpty() ? "Keep" : b + ", keep") + " him busy - I'm flanking " + dir + "."), "bark_flank"};
            case "flanked" -> new String[]{pick(r, "He's on my flank!", "He's got round us - " + dir + "!"), "bark_help"};
            case "moving" -> new String[]{pick(r, "Moving!", "Pushing up!", b.isEmpty() ? "Moving up!" : b + ", cover me - moving!"), "bark_move"};
            case "suppressing" -> new String[]{pick(r, "Suppressing!", "Keeping his head down!", (b.isEmpty() ? "Go" : b + ", go") + " - I've got him pinned!"), "bark_cover"};
            case "reloading" -> new String[]{pick(r, "Reloading!", "Changing mag!"), "bark_reload"};
            case "retreating" -> new String[]{d == Relations.Doctrine.BANDIT ? "Screw this - run!" : pick(r, "Falling back!", "I'm hit bad - pulling back!"), "bark_help"};
            case "grenade" -> new String[]{pick(r, "Grenade!", "Get clear!"), "bark_help"};
            case "grenade_out" -> new String[]{pick(r, "Frag out!", "Grenade out!"), "bark_cover"};
            case "ally_down" -> new String[]{pick(r, "Man down!", b.isEmpty() ? "They got one of us!" : "They got " + b + "!"), "bark_down"};
            case "body_found" -> new String[]{pick(r, "Body here! Eyes up!", "Man down - spread out!"), "shout"};
            case "blood_found" -> new String[]{pick(r, "Blood. Fresh.", "Somebody's bleeding - this way."), "shout"};
            case "blood_blame" -> new String[]{pick(r, "That's their blood. They came through here.", "Blood - they're hit, and they're close.", "He's bleeding. He won't have got far."), "shout"};
            case "clear" -> new String[]{pick(r, "Clear here.", "Nothing here.", "Clear."), mate ? "radio_chatter" : "bark_search"};
            case "search_house" -> new String[]{d == Relations.Doctrine.MILITARY ? pick(r, "He went inside. Clear it - room by room.", "Building! Stack up and sweep every floor.")
                    : d == Relations.Doctrine.BANDIT ? pick(r, "He's in the building! Turn it over!", "Rat's gone to ground in there - every room!")
                    : pick(r, "He's inside. Check every room.", "In the building - take it floor by floor."), "bark_search"};
            case "hold_door" -> new String[]{d == Relations.Doctrine.MILITARY ? pick(r, "I've got the door. Nobody gets out.", "Holding the exit - go!") : d == Relations.Doctrine.BANDIT ? pick(r, "I'll sit on the door. Flush him out.", "Door's mine. Go get him.") : pick(r, "Watching the way out.", "I'll keep the door."), "bark_clear"};
            case "search_over" -> new String[]{pick(r, "Nothing. Stay sharp.", "He's gone. Keep your eyes open."), "bark_clear"};
            case "target_down" -> new String[]{pick(r, "Target down.", "He's down.", "Got him."), mate ? "radio_chatter" : "bark_clear"};
            case "in_position" -> new String[]{"In position.", "radio_chatter"};
            case "noticed" -> new String[]{pick(r, "Something moved over there.", "Did you see that?"), "bark_search"};
            default -> null;
        };
    }
}

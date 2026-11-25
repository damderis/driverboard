package local.driveboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiles the owner can pick for DriveBoard Peta's panel (phone setting "panel_tiles").
 * A {@link #query} sends a nearby search to the navigation app; {@link #place} navigates to a
 * saved address (pref key); WEB opens the browser in the DriveBoard navigation entry.
 */
public enum PanelShortcut {
    HOME("home", R.string.home, R.drawable.ic_shortcut_home, null, "home"),
    WORK("work", R.string.work, R.drawable.ic_shortcut_work, null, "work"),
    PETROL("petrol", R.string.sc_petrol, R.drawable.ic_shortcut_petrol, "petrol station", null),
    CHARGING("charging", R.string.charging_station, R.drawable.ic_shortcut_charging,
            "EV charging station", null),
    MOSQUE("mosque", R.string.nearest_mosque, R.drawable.ic_shortcut_mosque, "masjid", null),
    SURAU("surau", R.string.sc_surau, R.drawable.ic_shortcut_mosque, "surau", null),
    FOOD("food", R.string.sc_food, R.drawable.ic_shortcut_food, "restaurant", null),
    COFFEE("coffee", R.string.sc_coffee, R.drawable.ic_shortcut_coffee, "cafe", null),
    REST("rest", R.string.sc_rest, R.drawable.ic_shortcut_toilet, "R&R rest area", null),
    PARKING("parking", R.string.sc_parking, R.drawable.ic_shortcut_parking, "parking", null),
    CLINIC("clinic", R.string.sc_clinic, R.drawable.ic_shortcut_hospital, "klinik", null),
    ATM("atm", R.string.sc_atm, R.drawable.ic_shortcut_atm, "ATM", null),
    WEB("web", R.string.web, R.drawable.ic_shortcut_web, null, null);

    public static final String DEFAULT = "home,charging,web,mosque";
    public static final int MAX = 4;

    public final String id;
    public final int label;
    public final int icon;
    public final String query;
    public final String place;

    PanelShortcut(String id, int label, int icon, String query, String place) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.query = query;
        this.place = place;
    }

    /** Parses the saved CSV, keeping order, dropping unknown ids, capped at {@link #MAX}. */
    public static List<PanelShortcut> parse(String csv) {
        List<PanelShortcut> result = new ArrayList<>();
        for (String id : (csv == null ? DEFAULT : csv).split(",")) {
            for (PanelShortcut shortcut : values()) {
                if (shortcut.id.equals(id.trim()) && !result.contains(shortcut)
                        && result.size() < MAX) {
                    result.add(shortcut);
                }
            }
        }
        return result;
    }

    public static String join(List<PanelShortcut> shortcuts) {
        StringBuilder csv = new StringBuilder();
        for (PanelShortcut shortcut : shortcuts) {
            if (csv.length() > 0) csv.append(',');
            csv.append(shortcut.id);
        }
        return csv.toString();
    }
}

package local.driveboard;

/** Shared phone and car palette. RGB values keep this table testable on the JVM. */
public enum Tone {
    TEAL("teal", 0xFF0F3D3E, 0xFF16494A),
    EMERALD("emerald", 0xFF0E3B2E, 0xFF15503F),
    NAVY("navy", 0xFF13233F, 0xFF1B3156),
    MAROON("maroon", 0xFF3A1420, 0xFF4D1D2C),
    GRAPHITE("graphite", 0xFF24272B, 0xFF2F3337);

    public final String id;
    public final int ground;
    public final int panel;

    Tone(String id, int ground, int panel) {
        this.id = id;
        this.ground = ground;
        this.panel = panel;
    }

    public static Tone from(String id) {
        for (Tone tone : values()) {
            if (tone.id.equals(id)) {
                return tone;
            }
        }
        return TEAL;
    }

    public static Tone current(android.content.Context context) {
        return from(Store.prefs(context).getString("tone", "teal"));
    }
}

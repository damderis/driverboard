package local.driveboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArraySet;

/** Process-local navigation and per-drive presentation choice; nothing is persisted. */
public final class NavigationRepository {
    public interface Listener { void onNavigationChanged(); }
    private static final NavigationRepository INSTANCE = new NavigationRepository();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile NavigationState state;
    private boolean collapsed;
    private String lastInstruction;

    private NavigationRepository() {}
    public static NavigationRepository get() { return INSTANCE; }
    public NavigationState state() { return state; }
    public boolean collapsed() { return collapsed; }
    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    public void publish(Context context, NavigationState next) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> publish(context, next));
            return;
        }
        NavigationState previous = state;
        state = next;
        if (next == null) {
            if (previous != null) CarDiagnostics.log(context, "Nav cleared");
        } else if (!next.instruction.equals(lastInstruction)) {
            lastInstruction = next.instruction;
            CarDiagnostics.log(context, "Nav manoeuvre " + next.instruction);
        } else if (previous == null || !next.distance.equals(previous.distance)
                || !next.eta.equals(previous.eta) || next.progress != previous.progress) {
            CarDiagnostics.log(context, "Nav update distance=" + next.distance
                    + " eta=" + next.eta + " progress=" + next.progress);
        }
        notifyListeners();
    }

    public void toggleCollapsed(Context context) {
        collapsed = !collapsed;
        CarDiagnostics.log(context, "Nav " + (collapsed ? "collapsed" : "expanded"));
        notifyListeners();
    }

    public void resetDrive() {
        collapsed = false;
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener listener : listeners) listener.onNavigationChanged();
    }
}

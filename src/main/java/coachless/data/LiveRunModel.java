package coachless.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which run the Live Run and Map tabs show, based on the mode the user picked.
 *   SINGLE       -> the newest solo save (current_run.save)
 *   MULTI_IDLE   -> multiplayer selected but not hosting/joined yet: nothing to show
 *   MULTI_HOST   -> the newest multiplayer save (current_run_mp.save)
 *   MULTI_CLIENT -> the run the host sent over the network (client has no local file)
 */
public final class LiveRunModel {
    public enum Mode { SINGLE, MULTI_IDLE, MULTI_HOST, MULTI_CLIENT }

    private final SaveFileManager saves;
    private final List<Runnable> listeners = new ArrayList<>();
    private Mode mode = Mode.SINGLE;
    private CurrentRun current;
    private CurrentRun remote;   // last run received from the host (client mode only)

    public LiveRunModel(SaveFileManager saves) {
        this.saves = saves;
        saves.addListener(this::recompute);   // the game saved something new
        recompute();
    }

    public Mode mode() { return mode; }

    // The run to display right now, or null if there isn't one
    public CurrentRun current() { return current; }

    public void addListener(Runnable r) { listeners.add(r); }

    public void setMode(Mode newMode) {
        if (mode == newMode) return;
        mode = newMode;
        if (mode != Mode.MULTI_CLIENT) {
            remote = null;
        }
        recompute();
    }

    // Client mode: the host sent us a new copy of the run
    public void setRemoteRun(String json) {
        try {
            remote = CurrentRun.parse(json, true);
        } catch (RuntimeException e) {
            return;   // bad data: keep showing the previous run
        }
        recompute();
    }

    private void recompute() {
        switch (mode) {
            case SINGLE -> current = CurrentRun.latest(saves, false);
            case MULTI_HOST -> current = CurrentRun.latest(saves, true);
            case MULTI_CLIENT -> current = remote;
            default -> current = null;
        }
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }
}

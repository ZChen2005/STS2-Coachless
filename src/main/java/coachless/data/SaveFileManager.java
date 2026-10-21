package coachless.data;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;


public final class SaveFileManager {

    //One file the user gave us, plus what we got out of it (or why it failed).
    public static final class Entry {
        public final Path path;
        public ProgressSave data;
        public String error;
        long modified = Long.MIN_VALUE;

        Entry(Path path) { this.path = path; }
    }

    private static final String PREF_KEY = "files";
    private final Preferences prefs = Preferences.userNodeForPackage(SaveFileManager.class);
    private final List<Entry> entries = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public SaveFileManager() {
        for (String p : prefs.get(PREF_KEY, "").split("\n")) {
            if (!p.isBlank()) {
                Entry e = new Entry(Paths.get(p));
                reload(e);
                entries.add(e);
            }
        }
        Timer timer = new Timer(2000, ev -> poll());
        timer.setCoalesce(true);
        timer.start();
    }

    public List<Entry> entries() { return Collections.unmodifiableList(entries); }

    public List<ProgressSave> validSaves() {
        List<ProgressSave> out = new ArrayList<>();
        for (Entry e : entries) if (e.data != null) out.add(e.data);
        return out;
    }

    public void addListener(Runnable r) { listeners.add(r); }

    public void add(Path path) {
        Path p = path.toAbsolutePath().normalize();
        for (Entry e : entries) if (e.path.equals(p)) return;
        Entry e = new Entry(p);
        reload(e);
        entries.add(e);
        persist();
        fire();
    }

    public void remove(Entry e) {
        if (entries.remove(e)) {
            persist();
            fire();
        }
    }

    //Internals
    private void poll() {
        boolean changed = false;
        for (Entry e : entries) {
            long m;
            try {
                m = Files.getLastModifiedTime(e.path).toMillis();
            } catch (IOException ex) {
                if (e.error == null) { e.data = null; e.error = "File not found"; changed = true; }
                e.modified = Long.MIN_VALUE;   // re-read as soon as it comes back
                continue;
            }
            if (m != e.modified) {
                reload(e);
                changed = true;
            }
        }
        if (changed) fire();
    }

    private void reload(Entry e) {
        try {
            e.modified = Files.getLastModifiedTime(e.path).toMillis();
            e.data = ProgressSave.parse(Files.readString(e.path));
            e.error = null;
        } catch (Exception ex) {
            e.data = null;
            e.error = ex instanceof IOException ? "Can't read file" : String.valueOf(ex.getMessage());
        }
    }

    private void persist() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) sb.append(e.path).append('\n');
        prefs.put(PREF_KEY, sb.toString());
    }

    private void fire() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }
}

package coachless.data;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public class RunFileWatcher {
    private final Path file;
    private final Consumer<String> onChange;
    private volatile boolean running;
    private Thread thread;

    public RunFileWatcher(Path file, Consumer<String> onChange) {
        this.file = file.toAbsolutePath();
        this.onChange = onChange;
    }

    public void start() {
        running = true;
        thread = new Thread(this::loop, "run-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            Path dir = file.getParent();
            dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            readAndNotify(); // initial read if file already exists
            while (running) {
                WatchKey key = ws.take();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    Object ctx = ev.context();
                    if (ctx instanceof Path && dir.resolve((Path) ctx).equals(file)) {
                        Thread.sleep(150); // let the game finish writing
                        readAndNotify();
                    }
                }
                if (!key.reset()) break;
            }
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            onChange.accept("Watcher error: " + e.getMessage());
        }
    }

    private void readAndNotify() {
        try {
            if (Files.exists(file)) onChange.accept(Files.readString(file));
        } catch (IOException ignored) {
            // file mid-write; next modify event will retry
        }
    }
}

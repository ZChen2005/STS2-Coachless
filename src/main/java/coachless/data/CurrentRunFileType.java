package coachless.data;

import java.nio.file.Files;
import java.nio.file.Path;

// current_run.save: the run in progress (map, position, potion odds). Parsed into CurrentRun.
public class CurrentRunFileType implements FileType {
    @Override
    public boolean matches(Path file) {
        return file.getFileName().toString().equals("current_run.save") || file.getFileName().toString().equals("current_run_mp.save");
    }

    @Override
    public Object parse(Path file) throws Exception {
        boolean multiplayer = file.getFileName().toString().equals("current_run_mp.save");
        return CurrentRun.parse(Files.readString(file), multiplayer);
    }
}
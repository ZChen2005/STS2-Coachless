package coachless.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TODO: parse your saved run files (see RunFileWatcher) and compute real values.
 */

public class PersonalStats implements StatsProvider {
    @Override
    public Map<String, Double> load() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Win rate %", 32.0);
        m.put("Avg deck size", 24.5);
        m.put("Avg floors reached", 38.0);
        m.put("Avg relics", 9.2);
        return m;
    }
}

package coachless.data;

import java.util.LinkedHashMap;
import java.util.Map;


public class GlobalStats implements StatsProvider {
    @Override
    public Map<String, Double> load() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Win rate %", 18.0);
        m.put("Avg run time", 27.1);
        m.put("Avg floors reached", 31.0);
        m.put("Best win streak", 5.0);
        return m;
    }
}

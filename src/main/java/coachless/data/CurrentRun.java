package coachless.data;
 
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
 
public final class CurrentRun {
    public static final double ELITE_BONUS = 0.125;
    public record Point(int col, int row, String type, List<int[]> children) { }
    public record ActInfo(String id, String bossId, String secondBossId, String ancientId) { }

    // multiplayer files have one per player.
    public record PlayerInfo(String characterId, int hp, int maxHp, int gold,
                             double potionOdds, List<String> potions, int potionSlots) { }
 
    public enum Phase { ON_MAP, IN_ROOM, REWARDS }
 
    public final String characterId;
    public final int ascension;
    public final int hp;
    public final int maxHp;
    public final int gold;
    public final long saveTime;
    public final int actIndex;
    public final String actId;
    public final List<ActInfo> acts;
 
    public final int mapWidth;
    public final int mapHeight;
    public final List<Point> points;
    public final List<int[]> visited;
 
    public final double potionOdds;

    public final List<PlayerInfo> players;   // everyone in the run 
    public final boolean multiplayer;        // true if this came from current_run_mp.save
    public final String rawJson;             // the original file text
 
    private final int historyEntries;
    public final boolean preFinished;
    public final String preFinishedEncounter;
 
    private CurrentRun(Map<String, Object> root, String rawJson, boolean multiplayer) {
        List<Object> actList = Json.list(root.get("acts"));
        List<Object> playerList = Json.list(root.get("players"));
        if (actList.isEmpty() || playerList.isEmpty()) {
            throw new IllegalArgumentException("Not a current_run.save file (no acts or players)");
        }
        Map<String, Object> player = Json.map(playerList.get(0));

        this.rawJson = rawJson;
        this.multiplayer = multiplayer;
        List<PlayerInfo> infos = new ArrayList<>();
        for (Object o : playerList) {
            infos.add(playerInfo(Json.map(o)));
        }
        players = infos;
 
        characterId = Json.str(player, "character_id", "");
        ascension = (int) Json.num(root, "ascension", 0);
        hp = (int) Json.num(player, "current_hp", 0);
        maxHp = (int) Json.num(player, "max_hp", 0);
        gold = (int) Json.num(player, "gold", 0);
        saveTime = (long) Json.num(root, "save_time", 0);
        potionOdds = Json.num(Json.map(player.get("odds")), "potion_reward_odds_value", Double.NaN);
 
        int idx = (int) Json.num(root, "current_act_index", 0);
        idx = Math.max(0, Math.min(idx, actList.size() - 1));
        actIndex = idx;
        actId = Json.str(Json.map(actList.get(idx)), "id", "");
 
        List<ActInfo> actInfos = new ArrayList<>();
        for (Object o : actList) {
            Map<String, Object> a = Json.map(o);
            Map<String, Object> rooms = Json.map(a.get("rooms"));
            actInfos.add(new ActInfo(Json.str(a, "id", ""),
                    Json.str(rooms, "boss_id", ""), 
                    Json.str(rooms, "second_boss_id", ""),
                    Json.str(rooms, "ancient_id", "")));
        }
        acts = actInfos;
 
        Map<String, Object> saved = Json.map(Json.map(actList.get(idx)).get("saved_map"));
        List<Point> pts = new ArrayList<>();
        if (saved.get("start") instanceof Map)  {
            pts.add(point(Json.map(saved.get("start"))));
        }

        for (Object o : Json.list(saved.get("points"))) {
            pts.add(point(Json.map(o)));
        }
        
        if (saved.get("boss") instanceof Map) {
            pts.add(point(Json.map(saved.get("boss"))));
        }

        points = pts;
        mapWidth = (int) Json.num(saved, "width", 7);
        int top = 0;

        for (Point p : pts) {
            top = Math.max(top, p.row());
        }

        mapHeight = top;
 
        List<int[]> seen = new ArrayList<>();
        for (Object o : Json.list(root.get("visited_map_coords"))) {
            Map<String, Object> c = Json.map(o);
            seen.add(new int[]{(int) Json.num(c, "col", 0), (int) Json.num(c, "row", 0)});
        }
        visited = seen;
 
        List<Object> history = Json.list(root.get("map_point_history"));
        historyEntries = idx < history.size() ? Json.list(history.get(idx)).size() : 0;
 
        Object pre = root.get("pre_finished_room");
        preFinished = pre instanceof Map;
        preFinishedEncounter = Json.str(Json.map(pre), "encounter_id", "");
    }
 
    public static CurrentRun parse(String json) {
        return parse(json, false);
    }

    public static CurrentRun parse(String json, boolean multiplayer) {
        return new CurrentRun(Json.map(Json.parse(json)), json, multiplayer);
    }
 
    //most recent
    public static CurrentRun latest(SaveFileManager saves) {
        CurrentRun best = null;
        for (CurrentRun r : saves.valid(CurrentRun.class)) {
            if (best == null || r.saveTime > best.saveTime) best = r;
        }
        return best;
    }
 
    // most recent, only solo files or only multiplayer files 
    public static CurrentRun latest(SaveFileManager saves, boolean multiplayer) {
        CurrentRun best = null;
        for (CurrentRun r : saves.valid(CurrentRun.class)) {
            if (r.multiplayer != multiplayer) continue;
            if (best == null || r.saveTime > best.saveTime) best = r;
        }
        return best;
    }

    //keeping track of current phase
    public Phase phase() {
        if (preFinished) {
            return Phase.REWARDS;
        }
        if (visited.size() > historyEntries) {
            return Phase.IN_ROOM;
        }
        return Phase.ON_MAP;
    }
 
    public Point currentPoint() {
        if (visited.isEmpty()) return null;
        int[] c = visited.get(visited.size() - 1);
        return pointAt(c[0], c[1]);
    }
 
    public Point pointAt(int col, int row) {
        for (Point p : points) {
            if (p.col() == col && p.row() == row) return p;
        }
        return null;
    }
 
    public double potionChance(boolean elite) {
        if (Double.isNaN(potionOdds)) {
            return Double.NaN;
        }
        return Math.max(0, Math.min(1, potionOdds + (elite ? ELITE_BONUS : 0)));
    }
 
    // same math as potionChance(boolean) but for any player in the run
    public double potionChance(PlayerInfo p, boolean elite) {
        if (Double.isNaN(p.potionOdds())) {
            return Double.NaN;
        }
        return Math.max(0, Math.min(1, p.potionOdds() + (elite ? ELITE_BONUS : 0)));
    }

    private static PlayerInfo playerInfo(Map<String, Object> p) {
        List<String> potions = new ArrayList<>();
        for (Object o : Json.list(p.get("potions"))) {
            String id = Json.str(Json.map(o), "id", "");
            if (!id.isEmpty()) {
                potions.add(id);
            }
        }
        return new PlayerInfo(
                Json.str(p, "character_id", ""),
                (int) Json.num(p, "current_hp", 0),
                (int) Json.num(p, "max_hp", 0),
                (int) Json.num(p, "gold", 0),
                Json.num(Json.map(p.get("odds")), "potion_reward_odds_value", Double.NaN),
                potions,
                (int) Json.num(p, "max_potion_slot_count", 0));
    }

    private static Point point(Map<String, Object> m) {
        Map<String, Object> c = Json.map(m.get("coord"));
        List<int[]> kids = new ArrayList<>();
        for (Object o : Json.list(m.get("children"))) {
            Map<String, Object> k = Json.map(o);
            kids.add(new int[]{(int) Json.num(k, "col", 0), (int) Json.num(k, "row", 0)});
        }
        return new Point((int) Json.num(c, "col", 0), (int) Json.num(c, "row", 0), Json.str(m, "type", ""), kids);
    }
}

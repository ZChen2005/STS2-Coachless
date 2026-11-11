package coachless.ui;

import coachless.data.CurrentRun;
import coachless.data.Names;
import coachless.data.LiveRunModel;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class MapPanel extends JPanel {
    private static final Color ACCENT = new Color(0x7C9CFF);
    // private static final int BACKGROUND_DIM = 140;

    private static final Map<String, Color> COLORS = Map.of(
            "monster", new Color(0x94A3B8),
            "elite", new Color(0xF87171),
            "rest_site", new Color(0x4ADE80),
            "shop", new Color(0xFACC15),
            "treasure", new Color(0xFB923C),
            "unknown", new Color(0xA78BFA),
            "boss", new Color(0xEF4444),
            "ancient", new Color(0x38BDF8));

    private final LiveRunModel runModel;
    private final MapView view = new MapView();
    private final JLabel subtitle = new JLabel(" ");
    private final JLabel bosses = new JLabel(" ");

    public MapPanel(LiveRunModel runModel) {
        super(new BorderLayout(0, 12));
        this.runModel = runModel;
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("Map");
        title.putClientProperty("FlatLaf.styleClass", "h1");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        bosses.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel header = new JPanel(new GridLayout(3, 1));
        header.setOpaque(false);
        header.add(title);
        header.add(subtitle);
        header.add(bosses);

        add(header, BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
        add(legend(), BorderLayout.SOUTH);

        runModel.addListener(this::refresh);   // updates when the run changes
        refresh();
    }

    private void refresh() {
        CurrentRun run = runModel.current();
        view.setRun(run);
        if (run == null) {
            subtitle.setText("No run to show. Single player: add the folder with current_run.save. Multiplayer: host or join a session.");
            bosses.setText(" ");
            return;
        }
        subtitle.setText("Act " + (run.actIndex + 1) + " - " + Names.PrettyPrint(run.actId));

        StringBuilder sb = new StringBuilder("Bosses:  ");
        for (int i = 0; i < run.acts.size(); i++) {
            CurrentRun.ActInfo a = run.acts.get(i);
            if (i > 0) sb.append("   -   ");
            sb.append("Act ").append(i + 1).append(": ").append(bossName(a.bossId()));
            if (!a.secondBossId().isEmpty()) sb.append(" / ").append(bossName(a.secondBossId()));
        }
        bosses.setText(sb.toString());
    }

    private static String bossName(String id) {
        return Names.PrettyPrint(id).replaceAll("\\s+Boss$", "");
    }

    private static JComponent legend() {
        JLabel note = new JLabel("Faded = not visited yet   |   blue ring = you are here   |   white ring = where you can go next");
        note.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel noteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        noteRow.setOpaque(false);
        noteRow.add(note);

        JPanel p = new JPanel(new GridLayout(2, 1, 0, 4));
        p.setOpaque(false);
        p.add(noteRow);
        return p;
    }

    private static JLabel legendItem(String text, Color c, BufferedImage pic) {
        if (pic != null) {
            BufferedImage small = MapArt.fit(pic, 18);
            JLabel l = new JLabel(text, new ImageIcon(small), SwingConstants.LEFT);
            l.setIconTextGap(6);
            return l;
        }
        Icon dot = new Icon() {
            @Override public void paintIcon(Component comp, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c);
                g.fill(new Ellipse2D.Double(x, y, 12, 12));
                g.dispose();
            }
            @Override public int getIconWidth() { return 12; }
            @Override public int getIconHeight() { return 12; }
        };
        JLabel l = new JLabel(text, dot, SwingConstants.LEFT);
        l.setIconTextGap(6);
        return l;
    }

    private static class MapView extends JPanel {
        private CurrentRun run;

        MapView() { setOpaque(false); }

        void setRun(CurrentRun run) {
            this.run = run;
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // BufferedImage bg = MapArt.background();
            // if (bg != null) {
            //     // tile the picture, then darken it a bit so the map stays readable
            //     g.setPaint(new TexturePaint(bg, new Rectangle(0, 0, bg.getWidth(), bg.getHeight())));
            //     g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            //     g.setPaint(new Color(0, 0, 0, BACKGROUND_DIM));
            //     g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            // }

            if (run == null || run.points.isEmpty()) {
                g.setColor(UIManager.getColor("Label.disabledForeground"));
                String msg = "No map yet. Add the folder that holds current_run.save.";
                g.drawString(msg, (getWidth() - g.getFontMetrics().stringWidth(msg)) / 2, getHeight() / 2);
                g.dispose();
                return;
            }

            int pad = 24;
            double colW = (getWidth() - 2.0 * pad) / run.mapWidth;
            double rowH = (getHeight() - 2.0 * pad) / Math.max(1, run.mapHeight);
            boolean art = MapArt.node("monster") != null;
            double r = Math.max(7, Math.min(art ? 22 : 16, Math.min(colW, rowH) * (art ? 0.42 : 0.36)));

            Set<Long> visited = new HashSet<>();
            Set<String> pathEdges = new HashSet<>();
            List<int[]> v = run.visited;
            for (int i = 0; i < v.size(); i++) {
                visited.add(key(v.get(i)[0], v.get(i)[1]));
                if (i > 0) pathEdges.add(key(v.get(i - 1)[0], v.get(i - 1)[1]) + ">" + key(v.get(i)[0], v.get(i)[1]));
            }
            CurrentRun.Point here = run.currentPoint();

            Color line = UIManager.getColor("Label.disabledForeground");
            Color faint = new Color(line.getRed(), line.getGreen(), line.getBlue(), 90);

            // paths first, so the circles sit on top
            for (CurrentRun.Point p : run.points) {
                for (int[] c : p.children()) {
                    boolean walked = pathEdges.contains(key(p.col(), p.row()) + ">" + key(c[0], c[1]));
                    g.setColor(walked ? ACCENT : faint);
                    g.setStroke(new BasicStroke(walked ? 4f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(x(p.col(), colW, pad), y(p.row(), rowH, pad),
                            x(c[0], colW, pad), y(c[1], rowH, pad)));
                }
            }

            // rooms
            g.setStroke(new BasicStroke(2f));
            int bossIndex = 0;
            for (CurrentRun.Point p : run.points) {
                double cx = x(p.col(), colW, pad), cy = y(p.row(), rowH, pad);
                boolean isBoss = p.type().equals("boss");
                double rad = isBoss ? r * 1.4 : r;
                boolean seen = visited.contains(key(p.col(), p.row()));

                BufferedImage pic;
                if (isBoss) pic = MapArt.boss(bossId(bossIndex++));
                else if (p.type().equals("ancient")) pic = MapArt.ancient(ancientId());
                else pic = MapArt.node(p.type());

                if (pic != null) {
                    BufferedImage img = MapArt.fit(pic, (int) Math.round(rad * 2));
                    Composite old = g.getComposite();
                    if (!seen) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                    // BufferedImage back = MapArt.nodeBackdrop();
                    // if (back != null) {
                    //     BufferedImage b = MapArt.fit(back, (int) Math.round(rad * 2 * 1.25));
                    //     g.drawImage(b, (int) Math.round(cx - b.getWidth() / 2.0), (int) Math.round(cy - b.getHeight() / 2.0), null);
                    // }
                    g.drawImage(img, (int) Math.round(cx - img.getWidth() / 2.0), (int) Math.round(cy - img.getHeight() / 2.0), null);
                    g.setComposite(old);
                    continue;
                }

                Color base = COLORS.getOrDefault(p.type(), Color.GRAY);
                g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), seen ? 255 : 120));
                g.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));

                g.setFont(getFont().deriveFont(Font.BOLD, (float) (rad * 1.1)));
                g.setColor(new Color(0x10131A));
                var fm = g.getFontMetrics();
            }

            // where you can go next (white ring) and where you are (blue ring)
            if (here != null) {
                g.setColor(new Color(255, 255, 255, 230));
                g.setStroke(new BasicStroke(2.5f));
                for (int[] c : here.children()) {
                    CurrentRun.Point n = run.pointAt(c[0], c[1]);
                    double rad = (n != null && n.type().equals("boss") ? r * 1.4 : r) + 4;
                    double cx = x(c[0], colW, pad), cy = y(c[1], rowH, pad);
                    g.draw(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
                }
                double rad = (here.type().equals("boss") ? r * 1.4 : r) + 5;
                double cx = x(here.col(), colW, pad), cy = y(here.row(), rowH, pad);
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(4f));
                g.draw(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
            }
            g.dispose();
        }

        private String ancientId() {
            if (run.actIndex < 0 || run.actIndex >= run.acts.size()) return "";
            return run.acts.get(run.actIndex).ancientId();
        }

        private String bossId(int n) {
            if (run.actIndex < 0 || run.actIndex >= run.acts.size()) return "";
            CurrentRun.ActInfo a = run.acts.get(run.actIndex);
            return n == 0 || a.secondBossId().isEmpty() ? a.bossId() : a.secondBossId();
        }

        private double x(int col, double colW, int pad) { return pad + (col + 0.5) * colW; }

        private double y(int row, double rowH, int pad) { return getHeight() - pad - row * rowH; }

        private static long key(int col, int row) { return col * 1000L + row; }
    }
}
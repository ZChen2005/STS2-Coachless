package coachless.ui;

import coachless.data.StatsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/** One rounded card per stat: your value vs global, with bars and a +/- badge. */
public class ComparePanel extends JPanel {
    private static final Color YOU = new Color(0x7C9CFF);
    private static final Color GLOBAL = new Color(0x5B6178);
    private static final Color GOOD = new Color(0x4ADE80);
    private static final Color BAD = new Color(0xF87171);

    public ComparePanel(StatsProvider personal, StatsProvider global) {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("You vs. Everyone");
        title.putClientProperty("FlatLaf.styleClass", "h1");   // FlatLaf big heading
        JLabel sub = new JLabel("How your runs stack up against global averages");
        sub.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setOpaque(false);
        header.add(title);
        header.add(sub);

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));   // 2 cards per row
        grid.setOpaque(false);
        Map<String, Double> mine = personal.load();
        Map<String, Double> theirs = global.load();
        for (String stat : mine.keySet()) {
            grid.add(new StatCard(stat, mine.get(stat), theirs.getOrDefault(stat, 0.0)));
        }
        JPanel top = new JPanel(new BorderLayout());   // keeps cards at natural height
        top.setOpaque(false);
        top.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(top);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** A rounded card. Copy this pattern for any new "tile" in your app. */
    private static class StatCard extends JPanel {
        private final double you, global;

        StatCard(String stat, double you, double global) {
            this.you = you;
            this.global = global;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
            setPreferredSize(new Dimension(300, 120));
            setLayout(new BorderLayout());

            JLabel name = new JLabel(stat);
            name.setFont(name.getFont().deriveFont(Font.BOLD, 15f));
            double diff = you - global;
            JLabel badge = new JLabel(String.format("%+.1f", diff));
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 12f));
            badge.setForeground(diff >= 0 ? GOOD : BAD);

            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(name, BorderLayout.WEST);
            head.add(badge, BorderLayout.EAST);
            add(head, BorderLayout.NORTH);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = UIManager.getColor("Panel.background");
            g.setColor(base.brighter());
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

            double max = Math.max(Math.max(you, global), 0.0001);
            int x = 18, w = getWidth() - 36;
            drawBar(g, "You", you, you / max, YOU, x, 52, w);
            drawBar(g, "Global", global, global / max, GLOBAL, x, 86, w);
            g.dispose();
            super.paintComponent(g0);
        }

        private void drawBar(Graphics2D g, String label, double value, double frac, Color c, int x, int y, int w) {
            g.setFont(getFont().deriveFont(12f));
            g.setColor(UIManager.getColor("Label.disabledForeground"));
            g.drawString(label, x, y);
            g.setColor(UIManager.getColor("Label.foreground"));
            String v = String.format("%.1f", value);
            g.drawString(v, x + w - g.getFontMetrics().stringWidth(v), y);
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRoundRect(x, y + 6, w, 8, 8, 8);
            g.setColor(c);
            g.fillRoundRect(x, y + 6, (int) (w * Math.max(0, Math.min(1, frac))), 8, 8, 8);
        }
    }
}
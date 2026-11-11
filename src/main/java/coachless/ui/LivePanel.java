package coachless.ui;

import coachless.GameSessionFramework.session.GameSession;
import coachless.data.CurrentRun;
import coachless.data.LiveRunModel;
import coachless.data.Names;
import coachless.multiplayer.MultiplayerController;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class LivePanel extends JPanel implements MultiplayerController.Listener {
    private static final Color ACCENT = new Color(0x7C9CFF);

    private final LiveRunModel runModel;
    private final JLabel runLine = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    // one PlayerView per player in the run (1 in single player, up to 4 in multiplayer)
    private final JPanel playersBox = new JPanel() {
        @Override
        public Dimension getMaximumSize() {   // grow wider with the window, but never taller than needed
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    };
    private final List<PlayerView> playerViews = new ArrayList<>();
    private long lastSharedSave = -1;   // saveTime of the last run the host sent to the other players

    // ---- multiplayer ----
    private static final String CARD_OFF = "off";
    private static final String CARD_SETUP = "setup";
    private static final String CARD_SESSION = "session";

    private final MultiplayerController multiplayer = new MultiplayerController(this);

    private final JRadioButton singleBtn = new JRadioButton("Single player", true);
    private final JRadioButton multiBtn = new JRadioButton("Multiplayer");
    private final CardLayout multiCards = new CardLayout();
    private final JPanel multiArea = new JPanel(multiCards);

    private final JTextField nameField = new JTextField("Player", 12);
    private final JTextField hostPortField = new JTextField("5555", 5);
    private final JButton hostBtn = new JButton("Host game");
    private final JTextField joinAddressField = new JTextField(12);
    private final JTextField joinPortField = new JTextField("5555", 5);
    private final JTextField joinCodeField = new JTextField(8);
    private final JButton joinBtn = new JButton("Join game");

    private final JLabel codeLabel = new JLabel("-");
    private final JLabel addressLabel = new JLabel("-");
    private final JLabel playersCountLabel = new JLabel("Players");
    private final JLabel sessionStatus = new JLabel(" ");
    private final DefaultListModel<String> playersModel = new DefaultListModel<>();
    private final JList<String> playersList = new JList<>(playersModel);
    private String inviteText = "";

    public LivePanel(LiveRunModel runModel) {
        super(new BorderLayout(0, 16));
        this.runModel = runModel;
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("Live Run");
        title.putClientProperty("FlatLaf.styleClass", "h1");
        status.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel header = new JPanel(new GridLayout(3, 1));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        header.add(title);
        header.add(runLine);
        header.add(status);

        playersBox.setOpaque(false);
        JPanel multi = buildMultiplayerPanel();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        for (JComponent c : new JComponent[] { header, playersBox, multi }) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        content.add(header);
        content.add(Box.createVerticalStrut(16));
        content.add(playersBox);
        content.add(Box.createVerticalStrut(16));
        content.add(multi);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        runModel.addListener(this::refresh);
        refresh();
    }

    // Redraws everything from whichever run the model says we should show
    private void refresh() {
        CurrentRun run = runModel.current();
        if (run == null) {
            runLine.setText(" ");
            status.setText(emptyMessage());
            syncPlayerViews(0);
            return;
        }

        String act = "Act " + (run.actIndex + 1) + " (" + Names.PrettyPrint(run.actId) + ")";
        runLine.setText("Ascension " + run.ascension + "  -  " + act
                + (run.multiplayer ? "  -  " + run.players.size() + " players" : ""));

        CurrentRun.Point here = run.currentPoint();
        String type = here == null ? "" : here.type();
        switch (run.phase()) {
            case REWARDS:
                status.setText("Fight finished - reward screen open");
                break;
            case IN_ROOM:
                status.setText("In a room: " + roomName(type));
                break;
            default:
                status.setText("On the map (last room: " + roomName(type) + ")");
        }

        syncPlayerViews(run.players.size());
        for (int i = 0; i < run.players.size(); i++) {
            playerViews.get(i).update(run, run.players.get(i), type);
        }
        shareWithParty(run);
    }

    // Host only: when the game saves a new version of the run, send it to everyone else
    private void shareWithParty(CurrentRun run) {
        if (runModel.mode() == LiveRunModel.Mode.MULTI_HOST && run.saveTime != lastSharedSave) {
            lastSharedSave = run.saveTime;
            multiplayer.shareRun(run.rawJson);
        }
    }

    private String emptyMessage() {
        switch (runModel.mode()) {
            case MULTI_IDLE:
                return "Host or join a session below to see a multiplayer run.";
            case MULTI_HOST:
                return "No multiplayer run found. Add the folder that holds current_run_mp.save.";
            case MULTI_CLIENT:
                return "Waiting for the host to share the run...";
            default:
                return "No active run found. Add the folder that holds current_run.save.";
        }
    }

    // Makes sure there is exactly one PlayerView per player, laid out in up to 2 columns
    private void syncPlayerViews(int count) {
        if (playerViews.size() == count) {
            return;
        }
        playersBox.removeAll();
        playerViews.clear();
        playersBox.setLayout(new GridLayout(0, Math.max(1, Math.min(2, count)), 16, 16));
        for (int i = 0; i < count; i++) {
            PlayerView v = new PlayerView();
            playerViews.add(v);
            playersBox.add(v);
        }
        playersBox.revalidate();
        playersBox.repaint();
    }

    private static void updatePotionCard(PotionCard card, CurrentRun run, CurrentRun.PlayerInfo p, String type) {
        double normal = run.potionChance(p, false);
        double elite = run.potionChance(p, true);
        if (Double.isNaN(normal)) {
            card.show("Potion chance", "-", "This save file doesn't have the potion chance.", "", Double.NaN);
            return;
        }
        String both = "Normal fight " + fmt(normal) + "    -    Elite fight " + fmt(elite);

        switch (run.phase()) {
            case REWARDS:
                // The game updates chance after the reward screen
                card.show("Fight finished", "Rewards pending",
                        "The chance updates after the reward screen (it was " + fmt(normal) + " before this fight).",
                        "Next fight: " + fmt(clamp(normal + 0.10)) + " if no potion dropped, "
                                + fmt(clamp(normal - 0.10)) + " if one did.",
                        Double.NaN);
                return;
            case IN_ROOM:
                switch (type) {
                    case "monster":
                        card.show("This combat (normal fight)", fmt(normal),
                                "Base chance from your recent fights.", both, normal);
                        return;
                    case "elite":
                        card.show("This combat (elite)", fmt(elite),
                                "Base " + fmt(normal) + " + " + fmt(CurrentRun.ELITE_BONUS) + " elite bonus.",
                                both, elite);
                        return;
                    case "boss":
                        card.show("This combat (boss)", fmt(normal),
                                "Boss fights: any special rules aren't included.", both, normal);
                        return;
                    case "unknown":
                        card.show("This room (?)", fmt(normal),
                                "If it turns out to be a fight, this is the normal-fight chance.", both, normal);
                        return;
                    default:
                        card.show("Next combat", fmt(normal),
                                "This room isn't a fight. Chance for your next normal fight.", both, normal);
                        return;
                }
            default:
                card.show("Next combat", fmt(normal),
                        "Chance for your next normal fight. Elites get +" + fmt(CurrentRun.ELITE_BONUS) + ".",
                        both, normal);
        }
    }


    // ================= Multiplayer UI =================

    private JPanel buildMultiplayerPanel() {
        ButtonGroup group = new ButtonGroup();
        group.add(singleBtn);
        group.add(multiBtn);
        singleBtn.addActionListener(e -> onModeChanged());
        multiBtn.addActionListener(e -> onModeChanged());

        JPanel modeRow = row(new JLabel("Mode:"), singleBtn, multiBtn);

        multiArea.add(new JPanel(), CARD_OFF);
        multiArea.add(buildSetupCard(), CARD_SETUP);
        multiArea.add(buildSessionCard(), CARD_SESSION);
        multiCards.show(multiArea, CARD_OFF);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(modeRow, BorderLayout.NORTH);
        panel.add(multiArea, BorderLayout.CENTER);
        return panel;
    }

    // host or join
    private JPanel buildSetupCard() {
        hostBtn.addActionListener(e -> onHostClicked());
        joinBtn.addActionListener(e -> onJoinClicked());

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createTitledBorder("Multiplayer"));
        card.add(row(new JLabel("Your name:"), nameField));
        card.add(row(new JLabel("Host a game - port:"), hostPortField, hostBtn));
        card.add(row(new JLabel("Join a game - address:"), joinAddressField,
                new JLabel("port:"), joinPortField,
                new JLabel("code:"), joinCodeField, joinBtn));
        return card;
    }

    // code, address, player list, leave
    private JPanel buildSessionCard() {
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 18f));

        JButton copyBtn = new JButton("Copy invite");
        copyBtn.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(inviteText), null));
        JButton leaveBtn = new JButton("Leave session");
        leaveBtn.addActionListener(e -> onLeaveClicked());

        JPanel info = new JPanel(new GridLayout(2, 2, 8, 2));
        info.add(new JLabel("Session code:"));
        info.add(codeLabel);
        info.add(new JLabel("Address:"));
        info.add(addressLabel);

        JPanel players = new JPanel(new BorderLayout(0, 2));
        players.add(playersCountLabel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(playersList);
        scroll.setPreferredSize(new Dimension(200, 80));
        players.add(scroll, BorderLayout.CENTER);

        JPanel bottom = row(copyBtn, leaveBtn, sessionStatus);

        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBorder(BorderFactory.createTitledBorder("Multiplayer session"));
        card.add(info, BorderLayout.NORTH);
        card.add(players, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel row(Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        for (Component c : parts) {
            p.add(c);
        }
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private void onModeChanged() {
        if (multiBtn.isSelected()) {
            multiCards.show(multiArea, multiplayer.isInSession() ? CARD_SESSION : CARD_SETUP);
            runModel.setMode(multiplayer.isHosting() ? LiveRunModel.Mode.MULTI_HOST
                    : multiplayer.isConnected() ? LiveRunModel.Mode.MULTI_CLIENT
                    : LiveRunModel.Mode.MULTI_IDLE);
        } else {
            if (multiplayer.isInSession()) {
                multiplayer.leaveSession();
            }
            multiCards.show(multiArea, CARD_OFF);
            runModel.setMode(LiveRunModel.Mode.SINGLE);   // back to singleplayer 
        }
    }

    private void onHostClicked() {
        String name = readName();
        Integer port = readPort(hostPortField);
        if (name == null || port == null) return;
        try {
            multiplayer.hostGame(name, port); // onHosting() switches the card
        } catch (IOException ex) {
            showError("Could not start hosting: " + ex.getMessage());
        }
    }

    private void onJoinClicked() {
        String name = readName();
        Integer port = readPort(joinPortField);
        String address = joinAddressField.getText().trim();
        String code = joinCodeField.getText().trim().toUpperCase();
        if (name == null || port == null) return;
        if (address.isEmpty() || code.isEmpty()) {
            showError("Enter the host's address and the session code.");
            return;
        }

        joinBtn.setEnabled(false);
        // not on the Swing thread because it is slow
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                multiplayer.joinGame(address, port, code, name);
                return null;
            }

            @Override
            protected void done() {
                joinBtn.setEnabled(true);
                try {
                    get();
                    runModel.setMode(LiveRunModel.Mode.MULTI_CLIENT);   // show the host's run, not local files
                    showSession(code, address + ":" + port,
                            "Join my STS Coachless game - address " + address
                                    + ", port " + port + ", code " + code);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    showError("Could not join: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void onLeaveClicked() {
        multiplayer.leaveSession();
        playersModel.clear();
        multiCards.show(multiArea, CARD_SETUP);
        runModel.setMode(LiveRunModel.Mode.MULTI_IDLE);
    }

    private void showSession(String code, String address, String invite) {
        codeLabel.setText(code);
        addressLabel.setText(address);
        inviteText = invite;
        multiBtn.setSelected(true);
        multiCards.show(multiArea, CARD_SESSION);
    }

    private String readName() {
        String name = nameField.getText().trim().replace(",", "");
        if (name.isEmpty()) {
            showError("Enter your name first.");
            return null;
        }
        return name;
    }

    private Integer readPort(JTextField field) {
        try {
            int port = Integer.parseInt(field.getText().trim());
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }
        showError("Enter a port between 1 and 65535.");
        return null;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Multiplayer", JOptionPane.ERROR_MESSAGE);
    }


    @Override
    public void onStatusChanged(String s) {
        sessionStatus.setText(s);
    }

    @Override
    public void onPlayersChanged(List<String> names) {
        playersModel.clear();
        for (int i = 0; i < names.size(); i++) {
            playersModel.addElement(i == 0 ? names.get(i) + " (host)" : names.get(i));
        }
        playersCountLabel.setText("Players (" + names.size() + "/" + GameSession.MAX_PLAYERS + ")");
    }

    @Override
    public void onHosting(String code, String address, int port) {
        showSession(code, address + ":" + port,
                "Join my STS Coachless game - address " + address
                        + ", port " + port + ", code " + code);
        lastSharedSave = -1;   // make sure the first run gets sent
        runModel.setMode(LiveRunModel.Mode.MULTI_HOST);   // read current_run_mp.save
    }

    @Override
    public void onDisconnected(String reason) {
        runModel.setMode(LiveRunModel.Mode.MULTI_IDLE);
        playersModel.clear();
        multiCards.show(multiArea, CARD_SETUP);
        JOptionPane.showMessageDialog(this, reason, "Disconnected", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void onRunReceived(String json) {
        runModel.setRemoteRun(json);   // host sent a new copy of the run
    }

    @Override
    public void onMessageReceived(String message) {
        // not used yet
    }

    private static String roomName(String type) {
        switch (type) {
            case "monster": return "normal fight";
            case "elite": return "elite fight";
            case "boss": return "boss fight";
            case "rest_site": return "rest site";
            case "shop": return "shop";
            case "treasure": return "treasure";
            case "unknown": return "? room";
            case "ancient": return "start (Neow / ancient)";
            default: return "unknown";
        }
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private static String fmt(double chance) {
        if (Double.isNaN(chance)) return "-";
        double pct = chance * 100;
        return Math.abs(pct - Math.round(pct)) < 0.05
                ? String.format("%d%%", Math.round(pct))
                : String.format("%.1f%%", pct);
    }

    // One player's section: name/HP/gold, potions held, and their potion-chance card
    private static class PlayerView extends JPanel {
        private final JLabel header = new JLabel(" ");
        private final JLabel potions = new JLabel(" ");
        private final PotionCard card = new PotionCard();

        PlayerView() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
            potions.setForeground(UIManager.getColor("Label.disabledForeground"));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
            for (JComponent c : new JComponent[] { header, potions, card }) {
                c.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            add(header);
            add(Box.createVerticalStrut(2));
            add(potions);
            add(Box.createVerticalStrut(8));
            add(card);
        }

        void update(CurrentRun run, CurrentRun.PlayerInfo p, String roomType) {
            header.setText(Names.PrettyPrint(p.characterId()) + "  -  HP " + p.hp() + "/" + p.maxHp()
                    + "  -  Gold " + p.gold());
            StringBuilder held = new StringBuilder("Potions: ");
            if (p.potions().isEmpty()) {
                held.append("none");
            } else {
                for (int i = 0; i < p.potions().size(); i++) {
                    if (i > 0) held.append(", ");
                    held.append(Names.PrettyPrint(p.potions().get(i)));
                }
            }
            held.append("  (").append(p.potions().size()).append("/").append(p.potionSlots()).append(" slots)");
            potions.setText(held.toString());
            updatePotionCard(card, run, p, roomType);
        }
    }

    private static class PotionCard extends JPanel {
        private String title = "", big = "", line1 = "", line2 = "";
        private double fraction = Double.NaN;

        PotionCard() {
            setOpaque(false);
            setPreferredSize(new Dimension(300, 200));
        }

        void show(String title, String big, String line1, String line2, double fraction) {
            this.title = title;
            this.big = big;
            this.line1 = line1;
            this.line2 = line2;
            this.fraction = fraction;
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(UIManager.getColor("Panel.background").brighter());
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

            int x = 24, w = getWidth() - 48;
            Color muted = UIManager.getColor("Label.disabledForeground");
            Color text = UIManager.getColor("Label.foreground");

            g.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g.setColor(muted);
            g.drawString("POTION CHANCE  -  " + title, x, 36);

            g.setFont(getFont().deriveFont(Font.BOLD, 46f));
            g.setColor(text);
            g.drawString(big, x, 92);

            if (!Double.isNaN(fraction)) {
                g.setColor(new Color(0, 0, 0, 60));
                g.fillRoundRect(x, 108, w, 10, 10, 10);
                g.setColor(ACCENT);
                g.fillRoundRect(x, 108, (int) (w * Math.max(0, Math.min(1, fraction))), 10, 10, 10);
            }

            g.setFont(getFont().deriveFont(13f));
            g.setColor(muted);
            g.drawString(line1, x, 146);
            g.drawString(line2, x, 170);
            g.dispose();
        }
    }
}
package coachless;

import coachless.data.GlobalStats;
import coachless.data.PersonalStats;
import coachless.ui.ComparePanel;
import coachless.ui.LivePanel;
import javax.swing.*;
import java.awt.Dimension;

/**
 * Main window. To add a feature: write a JPanel in coachless.ui, then add ONE line in the constructor: tabs.addTab("Name", new YourPanel());
 */

public class MainFrame extends JFrame {

    public MainFrame() {
        super("STS Coachless");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(900, 600));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Compare", new ComparePanel(new PersonalStats(), new GlobalStats()));
        tabs.addTab("Live Run", new LivePanel());

        add(tabs);
        pack();
        setLocationRelativeTo(null);
    }
}

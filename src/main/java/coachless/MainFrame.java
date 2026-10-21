package coachless;

import coachless.data.GlobalStats;
import coachless.data.PersonalStats;
import coachless.data.SaveFileManager;
import coachless.ui.ComparePanel;
import coachless.ui.LivePanel;
import coachless.ui.SourcesBar;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
/**
 * Main window. To add a feature: write a JPanel in coachless.ui, then add ONE line in the constructor: tabs.addTab("Name", new YourPanel());
 */

public class MainFrame extends JFrame {

    public MainFrame() {
        super("STS Coachless");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(900, 600));

        SaveFileManager saves = new SaveFileManager();
        PersonalStats personal = new PersonalStats(saves);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Compare", new ComparePanel(personal, new GlobalStats()));
        tabs.addTab("Live Run", new LivePanel());

        add(new SourcesBar(saves), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        SourcesBar.installFileDrop((JComponent) getContentPane(), saves);
        
        pack();
        setLocationRelativeTo(null);
    }
}

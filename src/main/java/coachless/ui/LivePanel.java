package coachless.ui;

import coachless.data.RunFileWatcher;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Font;
import java.nio.file.Path;

/**
 * Shows the live run file as it changes. Pick the file with the button.
 * TODO: parse the JSON and generate suggestions in onRunUpdate().
 */
public class LivePanel extends JPanel {
    private final JLabel status = new JLabel("No run file selected");
    private final JTextArea output = new JTextArea();
    private RunFileWatcher watcher;

    public LivePanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton choose = new JButton("Choose run file...");
        choose.addActionListener(e -> chooseFile());

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(choose, BorderLayout.WEST);
        top.add(status, BorderLayout.CENTER);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path p = fc.getSelectedFile().toPath();
        if (watcher != null) watcher.stop();
        status.setText("Watching: " + p);
        watcher = new RunFileWatcher(p, text -> SwingUtilities.invokeLater(() -> onRunUpdate(text)));
        watcher.start();
    }

    /** Called on the Swing thread each time the run file changes. */
    private void onRunUpdate(String json) {
        output.setText(json); // replace with parsing + suggestions
        output.setCaretPosition(0);
    }
}

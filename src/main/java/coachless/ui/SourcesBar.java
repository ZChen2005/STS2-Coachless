package coachless.ui;

import coachless.data.SaveFileManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Strip at the top of the window: "Add save file..." button plus one chip per
 * progress.save the user gave us (click x to remove). Files can also be dragged
 * onto the window - see installFileDrop().
 */
public class SourcesBar extends JPanel {
    private static final Color BAD = new Color(0xF87171);

    private final SaveFileManager saves;
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

    public SourcesBar(SaveFileManager saves) {
        super(new BorderLayout(12, 0));
        this.saves = saves;
        setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JButton addBtn = new JButton("Add save file...");
        addBtn.addActionListener(e -> chooseFiles());
        chips.setOpaque(false);

        add(addBtn, BorderLayout.WEST);
        add(chips, BorderLayout.CENTER);

        saves.addListener(this::rebuild);
        rebuild();
    }

    private void rebuild() {
        chips.removeAll();
        if (saves.entries().isEmpty()) {
            JLabel hint = new JLabel("Drag your progress.save file(s) onto this window, or click Add");
            hint.setForeground(UIManager.getColor("Label.disabledForeground"));
            chips.add(hint);
        }
        for (SaveFileManager.Entry e : saves.entries()) chips.add(chip(e));
        chips.revalidate();
        chips.repaint();
    }

    private JComponent chip(SaveFileManager.Entry e) {
        JPanel p = new JPanel(new BorderLayout(4, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(UIManager.getColor("Panel.background").brighter());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 4));
        p.setToolTipText(e.error == null ? e.path.toString() : e.path + "  -  " + e.error);

        JLabel name = new JLabel((e.error == null ? "" : "(!) ") + shortName(e.path));
        if (e.error != null) name.setForeground(BAD);

        JButton x = new JButton("×");
        x.setToolTipText("Remove");
        x.setBorderPainted(false);
        x.setContentAreaFilled(false);
        x.setFocusable(false);
        x.setMargin(new Insets(0, 4, 0, 4));
        x.addActionListener(ev -> saves.remove(e));

        p.add(name, BorderLayout.CENTER);
        p.add(x, BorderLayout.EAST);
        return p;
    }

    /** Last 3 parts of the path, e.g. "profile1/saves/progress.save". */
    private static String shortName(Path p) {
        int n = p.getNameCount();
        return p.subpath(Math.max(0, n - 3), n).toString().replace('\\', '/');
    }

    private void chooseFiles() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setDialogTitle("Choose progress.save file(s) or a folder that contains them");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addAll(saves, fc.getSelectedFiles());
        }
    }

    // ---- shared by the button and drag-and-drop --------------------------

    /** A file is added as-is. A folder is searched for progress.save files inside it. */
    static void addAll(SaveFileManager saves, File... files) {
        for (File f : files) {
            if (f.isDirectory()) {
                try (Stream<Path> walk = Files.walk(f.toPath(), 6)) {
                    List<Path> found = new ArrayList<>();
                    walk.filter(p -> p.getFileName().toString().equals("progress.save")
                            && Files.isRegularFile(p)).forEach(found::add);
                    found.forEach(saves::add);
                } catch (IOException ignored) {
                }
            } else {
                saves.add(f.toPath());
            }
        }
    }

    /** Make a component (e.g. the window's content pane) accept dropped files and folders. */
    public static void installFileDrop(JComponent target, SaveFileManager saves) {
        target.setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                if (!canImport(s)) return false;
                try {
                    List<File> files = (List<File>) s.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    addAll(saves, files.toArray(new File[0]));
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
    }
}

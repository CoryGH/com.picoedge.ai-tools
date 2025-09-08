package com.picoedge.ai_tools;

import com.picoedge.ai_tools.utils.LogPaneUtils;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class LogPaneUI {
    private final JPanel content;
    private final JEditorPane logArea;
    private final JComboBox<String> levelFilter;
    private final JComboBox<String> sourceFilter;
    private final JTextField categoryFilter;
    private final JTextField searchField;
    private final JComboBox<String> timeRangeFilter;
    private final JCheckBox[] levelCheckBoxes;
    private final JList<String> sourceList;
    private final DefaultListModel<String> sourceModel;
    private final JButton connectButton;
    private final JButton newEntryButton;
    private final JButton settingsButton;
    private final JButton toggleStackButton;
    private final JButton toggleDataButton;
    private final JButton clearButton;
    private final JButton copyButton;
    private final JButton clearDeviceIdButton;
    private final JButton clearCorrelationIdButton;
    private int selectedLevels = 255;
    private String searchQuery = "";
    private String timeRange = "All Time";
    private final LogFilter logFilter; // Added to access filter states

    public LogPaneUI(Consumer<String> onHyperlinkActivated, Runnable onTimeRangeChanged, Runnable onLevelFilterChanged,
                     Runnable onSourceFilterChanged, Runnable onCategoryFilterChanged, Runnable onSearchQueryChanged,
                     Runnable onConnect, Runnable onNewEntry, Runnable onSettings, Runnable onToggleStacks,
                     Runnable onToggleData, Runnable onClearLogs, Runnable onCopyLogs,
                     Consumer<String> onSetDeviceIdFilter, Consumer<String> onSetCorrelationIdFilter,
                     LogFilter logFilter) {
        this.logFilter = logFilter; // Initialize LogFilter
        content = new JPanel(new BorderLayout());
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));

        // Connect button with circular state indicator
        connectButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillOval(10, 5, 20, 20);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
                g2d.drawString("⚡", 35, 20);
            }
        };
        connectButton.setPreferredSize(new Dimension(60, 30));
        connectButton.setToolTipText("Connection Status");
        connectButton.addActionListener(e -> onConnect.run());

        // New Entry button
        newEntryButton = new JButton("➕");
        newEntryButton.setPreferredSize(new Dimension(30, 30));
        newEntryButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        newEntryButton.setToolTipText("New Entry");
        newEntryButton.addActionListener(e -> onNewEntry.run());

        // Timestamp range filter
        timeRangeFilter = new JComboBox<>(new String[]{"All Time", "Last 5 Minutes", "Last 1 Hour", "Last 24 Hours", "Last 7 Days"});
        timeRangeFilter.setPreferredSize(new Dimension(150, 30));
        timeRangeFilter.setSelectedItem("All Time");
        timeRangeFilter.addActionListener(e -> {
            timeRange = (String) timeRangeFilter.getSelectedItem();
            onTimeRangeChanged.run();
        });

        // Level filter with "All Levels" option
        levelFilter = new JComboBox<>();
        levelFilter.setPreferredSize(new Dimension(150, 30));
        levelCheckBoxes = new JCheckBox[]{
                new JCheckBox("All Levels"),
                new JCheckBox("Trace"), new JCheckBox("Debug"), new JCheckBox("Info"),
                new JCheckBox("Notice"), new JCheckBox("Warning"), new JCheckBox("Error"),
                new JCheckBox("Critical"), new JCheckBox("Fatal")
        };
        levelCheckBoxes[0].setSelected(true);
        Arrays.stream(levelCheckBoxes).skip(1).forEach(cb -> cb.setSelected(true));
        JPopupMenu levelPopup = new JPopupMenu();
        Arrays.stream(levelCheckBoxes).forEach(levelPopup::add);
        levelFilter.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                levelPopup.show(levelFilter, 0, levelFilter.getHeight());
            }
        });
        Arrays.stream(levelCheckBoxes).forEach(cb -> cb.addActionListener(e -> {
            if (cb == levelCheckBoxes[0]) {
                boolean allSelected = cb.isSelected();
                Arrays.stream(levelCheckBoxes).skip(1).forEach(other -> other.setSelected(allSelected));
                selectedLevels = allSelected ? 255 : 0;
            } else {
                boolean allOthersSelected = Arrays.stream(levelCheckBoxes).skip(1).allMatch(JCheckBox::isSelected);
                levelCheckBoxes[0].setSelected(allOthersSelected);
                selectedLevels = 0;
                for (int i = 1; i < levelCheckBoxes.length; i++) {
                    if (levelCheckBoxes[i].isSelected()) {
                        selectedLevels |= LogProcessor.LogLevel.values()[i].getValue();
                    }
                }
            }
            updateLevelFilter();
            onLevelFilterChanged.run();
        }));

        // Source filter with "All Sources" option
        sourceModel = new DefaultListModel<>();
        sourceModel.addElement("All Sources");
        sourceList = new JList<>(sourceModel);
        sourceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        sourceList.setSelectedIndex(0); // Default to "All Sources"
        sourceList.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (index0 == 0) {
                    boolean selectAll = !sourceList.isSelectedIndex(0);
                    if (selectAll) {
                        super.setSelectionInterval(0, sourceModel.getSize() - 1);
                    } else {
                        super.clearSelection();
                    }
                } else {
                    super.setSelectionInterval(index0, index1);
                    boolean allOthersSelected = sourceModel.getSize() > 1 &&
                            IntStream.range(1, sourceModel.getSize()).allMatch(sourceList::isSelectedIndex);
                    if (allOthersSelected) {
                        super.addSelectionInterval(0, 0);
                    } else {
                        super.removeSelectionInterval(0, 0);
                    }
                }
                updateSourceFilterDisplay();
                onSourceFilterChanged.run();
            }

            @Override
            public void addSelectionInterval(int index0, int index1) {
                if (index0 == 0) {
                    super.setSelectionInterval(0, sourceModel.getSize() - 1);
                } else {
                    super.addSelectionInterval(index0, index1);
                    boolean allOthersSelected = sourceModel.getSize() > 1 &&
                            IntStream.range(1, sourceModel.getSize()).allMatch(sourceList::isSelectedIndex);
                    if (allOthersSelected) {
                        super.addSelectionInterval(0, 0);
                    } else {
                        super.removeSelectionInterval(0, 0);
                    }
                }
                updateSourceFilterDisplay();
                onSourceFilterChanged.run();
            }

            @Override
            public void removeSelectionInterval(int index0, int index1) {
                if (index0 == 0) {
                    super.clearSelection();
                } else {
                    super.removeSelectionInterval(index0, index1);
                    super.removeSelectionInterval(0, 0);
                }
                updateSourceFilterDisplay();
                onSourceFilterChanged.run();
            }
        });
        sourceFilter = new JComboBox<>();
        sourceFilter.setPreferredSize(new Dimension(150, 30));
        JPopupMenu sourcePopup = new JPopupMenu();
        JScrollPane sourceScroll = new JScrollPane(sourceList);
        sourceScroll.setPreferredSize(new Dimension(150, 150));
        sourcePopup.add(sourceScroll);
        sourceFilter.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                sourcePopup.show(sourceFilter, 0, sourceFilter.getHeight());
            }
        });

        // Category filter with placeholder
        categoryFilter = new JTextField(15) {
            private final String placeholder = "Enter category (e.g. test.websocket)";
            private boolean showingPlaceholder = true;

            {
                setText(placeholder);
                setForeground(Color.GRAY);
                addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        if (showingPlaceholder) {
                            setText("");
                            setForeground(Color.BLACK);
                            showingPlaceholder = false;
                        }
                    }
                    @Override
                    public void focusLost(FocusEvent e) {
                        if (getText().isEmpty()) {
                            setText(placeholder);
                            setForeground(Color.GRAY);
                            showingPlaceholder = true;
                        }
                    }
                });
            }

            @Override
            public String getText() {
                return showingPlaceholder ? "" : super.getText();
            }
        };
        categoryFilter.setPreferredSize(new Dimension(150, 30));
        categoryFilter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                onCategoryFilterChanged.run();
            }
        });

        // Search field with placeholder
        searchField = new JTextField(15) {
            private final String placeholder = "Search query";
            private boolean showingPlaceholder = true;

            {
                setText(placeholder);
                setForeground(Color.GRAY);
                addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        if (showingPlaceholder) {
                            setText("");
                            setForeground(Color.BLACK);
                            showingPlaceholder = false;
                        }
                    }
                    @Override
                    public void focusLost(FocusEvent e) {
                        if (getText().isEmpty()) {
                            setText(placeholder);
                            setForeground(Color.GRAY);
                            showingPlaceholder = true;
                        }
                    }
                });
            }

            @Override
            public String getText() {
                return showingPlaceholder ? "" : super.getText();
            }
        };
        searchField.setPreferredSize(new Dimension(150, 30));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                searchQuery = searchField.getText().trim();
                onSearchQueryChanged.run();
            }
        });

        // Other buttons
        settingsButton = new JButton("⚙️");
        settingsButton.setPreferredSize(new Dimension(30, 30));
        settingsButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        settingsButton.setToolTipText("Settings");
        settingsButton.addActionListener(e -> onSettings.run());

        toggleStackButton = new JButton("📚");
        toggleStackButton.setPreferredSize(new Dimension(30, 30));
        toggleStackButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        toggleStackButton.setToolTipText("Expand/Collapse Stack Traces");
        toggleStackButton.addActionListener(e -> onToggleStacks.run());

        toggleDataButton = new JButton("📊");
        toggleDataButton.setPreferredSize(new Dimension(30, 30));
        toggleDataButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        toggleDataButton.setToolTipText("Expand/Collapse All Data");
        toggleDataButton.addActionListener(e -> onToggleData.run());

        clearButton = new JButton("🗑️");
        clearButton.setPreferredSize(new Dimension(30, 30));
        clearButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        clearButton.setToolTipText("Clear Logs");
        clearButton.addActionListener(e -> onClearLogs.run());

        copyButton = new JButton("📋");
        copyButton.setPreferredSize(new Dimension(30, 30));
        copyButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        copyButton.setToolTipText("Copy Logs");
        copyButton.addActionListener(e -> onCopyLogs.run());

        clearDeviceIdButton = new JButton("🖥️");
        clearDeviceIdButton.setPreferredSize(new Dimension(30, 30));
        clearDeviceIdButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        clearDeviceIdButton.setToolTipText("Set Device ID Filter");
        clearDeviceIdButton.addActionListener(e -> {
            if (!logFilter.getDeviceIdFilter().isEmpty()) {
                onSetDeviceIdFilter.accept(""); // Clear filter if active
                System.out.println("[LogPaneUI] Cleared deviceId filter");
            } else {
                handleClearDeviceId(onSetDeviceIdFilter); // Open prompt if no filter
            }
        });

        clearCorrelationIdButton = new JButton("🔗");
        clearCorrelationIdButton.setPreferredSize(new Dimension(30, 30));
        clearCorrelationIdButton.setFont(new Font("SansSerif", Font.PLAIN, 16));
        clearCorrelationIdButton.setToolTipText("Set Correlation ID Filter");
        clearCorrelationIdButton.addActionListener(e -> {
            if (!logFilter.getCorrelationIdFilter().isEmpty()) {
                onSetCorrelationIdFilter.accept(""); // Clear filter if active
                System.out.println("[LogPaneUI] Cleared correlationId filter");
            } else {
                handleClearCorrelationId(onSetCorrelationIdFilter); // Open prompt if no filter
            }
        });

        // Add components to filter panel
        filterPanel.add(connectButton);
        filterPanel.add(newEntryButton);
        filterPanel.add(timeRangeFilter);
        filterPanel.add(categoryFilter);
        filterPanel.add(sourceFilter);
        filterPanel.add(clearDeviceIdButton);
        filterPanel.add(clearCorrelationIdButton);
        filterPanel.add(levelFilter);
        filterPanel.add(searchField);
        filterPanel.add(toggleDataButton);
        filterPanel.add(toggleStackButton);
        filterPanel.add(clearButton);
        filterPanel.add(copyButton);
        filterPanel.add(settingsButton);

        logArea = new JEditorPane();
        logArea.setContentType("text/html");
        logArea.setEditable(false);
        logArea.setBackground(new Color(30, 30, 30));
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // Ensure scroll to bottom on new logs
        logArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                onHyperlinkActivated.accept(e.getDescription());
            }
        });

        JScrollPane scrollPane = new JScrollPane(logArea);
        content.add(filterPanel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        updateLevelFilter();
        updateSourceFilterDisplay();
        updateClearDeviceIdButton(logFilter.getDeviceIdFilter()); // Initialize with current filter
        updateClearCorrelationIdButton(logFilter.getCorrelationIdFilter()); // Initialize with current filter
    }

    public JComponent getContent() {
        return content;
    }

    public void setLogContent(String html) {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) logArea.getParent().getParent();
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            boolean isAtBottom = verticalScrollBar.getValue() + verticalScrollBar.getVisibleAmount() >= verticalScrollBar.getMaximum();
            System.out.println("[LogPaneUI] Setting log content: isAtBottom=" + isAtBottom + ", scrollValue=" + verticalScrollBar.getValue() + ", visibleAmount=" + verticalScrollBar.getVisibleAmount() + ", max=" + verticalScrollBar.getMaximum());
            logArea.setText(html);
            logArea.revalidate();
            logArea.repaint();
            if (isAtBottom) {
                logArea.setCaretPosition(logArea.getDocument().getLength());
                System.out.println("[LogPaneUI] Scrolled to bottom: caretPosition=" + logArea.getCaretPosition());
            } else {
                System.out.println("[LogPaneUI] Scroll position maintained: caretPosition=" + logArea.getCaretPosition());
            }
        });
    }

    public void updateConnectionStatus(boolean isConnected) {
        SwingUtilities.invokeLater(() -> {
            connectButton.setBackground(isConnected ? new Color(0, 128, 0) : new Color(128, 128, 128));
            connectButton.setToolTipText(isConnected ? "Connected" : "Disconnected");
            connectButton.repaint();
        });
    }

    public void updateStackButton(boolean expanded) {
        SwingUtilities.invokeLater(() -> toggleStackButton.setBackground(expanded ? new Color(76, 175, 80) : new Color(42, 42, 42)));
    }

    public void updateDataButton(boolean expanded) {
        SwingUtilities.invokeLater(() -> toggleDataButton.setBackground(expanded ? new Color(76, 175, 80) : new Color(42, 42, 42)));
    }

    public void clearSources() {
        SwingUtilities.invokeLater(() -> {
            sourceModel.clear();
            sourceModel.addElement("All Sources");
            sourceList.setSelectedIndex(0);
            updateSourceFilterDisplay();
        });
    }

    public void addSource(String source) {
        SwingUtilities.invokeLater(() -> {
            if (!sourceModel.contains(source)) {
                sourceModel.addElement(source);
                sourceList.setSelectedIndex(sourceModel.size() - 1);
                updateSourceFilterDisplay();
            }
        });
    }

    public void setCategoryFilter(String category) {
        SwingUtilities.invokeLater(() -> {
            categoryFilter.setText(category);
            categoryFilter.setForeground(Color.BLACK);
        });
    }

    public void setSourceFilter(String source) {
        SwingUtilities.invokeLater(() -> {
            sourceList.setSelectedValue(source, true);
            updateSourceFilterDisplay();
        });
    }

    public void setLevelFilter(int level) {
        SwingUtilities.invokeLater(() -> {
            selectedLevels = level;
            for (JCheckBox cb : levelCheckBoxes) {
                cb.setSelected(cb.getText().equals(LogProcessor.LogLevel.toString(level)) || cb.getText().equals("All Levels") && level == 255);
            }
            updateLevelFilter();
        });
    }

    public String getCategoryFilter() {
        return categoryFilter.getText().trim();
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public int getSelectedLevels() {
        return selectedLevels;
    }

    public List<String> getSelectedSources() {
        return sourceList.getSelectedValuesList();
    }

    public boolean isAllSourcesSelected() {
        return sourceList.isSelectedIndex(0);
    }

    private void updateLevelFilter() {
        SwingUtilities.invokeLater(() -> {
            levelFilter.removeAllItems();
            levelFilter.addItem(selectedLevels == 255 ? "All Levels" : getLevelDisplay());
        });
    }

    private void updateSourceFilterDisplay() {
        SwingUtilities.invokeLater(() -> {
            sourceFilter.removeAllItems();
            int selectedCount = sourceList.getSelectedIndices().length;
            if (selectedCount == sourceModel.getSize() && !sourceModel.isEmpty()) {
                sourceFilter.addItem("All Sources");
            } else if (selectedCount == 0) {
                sourceFilter.addItem("None");
            } else {
                List<String> selectedSources = sourceList.getSelectedValuesList();
                sourceFilter.addItem(String.join(",", selectedSources));
            }
        });
    }

    public void updateClearDeviceIdButton(String deviceIdFilter) {
        SwingUtilities.invokeLater(() -> {
            clearDeviceIdButton.setToolTipText(deviceIdFilter.isEmpty() ? "Set Device ID Filter" : "Clear Device ID Filter");
            clearDeviceIdButton.setBackground(deviceIdFilter.isEmpty() ? new Color(42, 42, 42) : new Color(76, 175, 80));
            clearDeviceIdButton.repaint();
            System.out.println("[LogPaneUI] Updated deviceId button: filter=" + (deviceIdFilter.isEmpty() ? "none" : deviceIdFilter));
        });
    }

    public void updateClearCorrelationIdButton(String correlationIdFilter) {
        SwingUtilities.invokeLater(() -> {
            clearCorrelationIdButton.setToolTipText(correlationIdFilter.isEmpty() ? "Set Correlation ID Filter" : "Clear Correlation ID Filter");
            clearCorrelationIdButton.setBackground(correlationIdFilter.isEmpty() ? new Color(42, 42, 42) : new Color(76, 175, 80));
            clearCorrelationIdButton.repaint();
            System.out.println("[LogPaneUI] Updated correlationId button: filter=" + (correlationIdFilter.isEmpty() ? "none" : correlationIdFilter));
        });
    }

    private void handleClearDeviceId(Consumer<String> onSetDeviceIdFilter) {
        JDialog dialog = new JDialog((Frame) null, "Set Device ID Filter", true);
        dialog.setLayout(new GridLayout(3, 1));
        JTextField inputField = new JTextField(20);
        dialog.add(new JLabel("Enter Device ID:"));
        dialog.add(inputField);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            onSetDeviceIdFilter.accept(inputField.getText().trim());
            dialog.dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(content);
        dialog.setVisible(true);
    }

    private void handleClearCorrelationId(Consumer<String> onSetCorrelationIdFilter) {
        JDialog dialog = new JDialog((Frame) null, "Set Correlation ID Filter", true);
        dialog.setLayout(new GridLayout(3, 1));
        JTextField inputField = new JTextField(20);
        dialog.add(new JLabel("Enter Correlation ID:"));
        dialog.add(inputField);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            onSetCorrelationIdFilter.accept(inputField.getText().trim());
            dialog.dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(content);
        dialog.setVisible(true);
    }

    private String getLevelDisplay() {
        if (selectedLevels == 255) return "All Levels";
        if (selectedLevels == 0) return "None";
        List<String> selected = new ArrayList<>();
        for (int i = 1; i < levelCheckBoxes.length; i++) {
            if (levelCheckBoxes[i].isSelected()) {
                selected.add(levelCheckBoxes[i].getText());
            }
        }
        return selected.isEmpty() ? "None" : String.join(",", selected);
    }
}
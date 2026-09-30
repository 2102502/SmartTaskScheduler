package smarttaskscheduler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.DefaultTableModel;

/** Application entry point and Swing user interface. */
public final class SmartTaskScheduler {
    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, uuuu  HH:mm");
    private static final Color NAVY = new Color(29, 43, 76);
    private static final Color ACCENT = new Color(49, 99, 190);
    private static final Color BACKGROUND = new Color(245, 247, 251);
    private static final Color MUTED = new Color(104, 115, 134);

    private final TaskManager taskManager;
    private final JFrame frame = new JFrame("Smart Task Scheduler");
    private final JTextField titleField = new JTextField(25);
    private final JComboBox<Task.Priority> priorityBox = new JComboBox<>(Task.Priority.values());
    private final JTextField deadlineField = new JTextField(17);
    private final JComboBox<String> filterBox = new JComboBox<>(new String[]{
            "Show All", "Today's Tasks", "High Priority", "Completed", "Pending"
    });
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Task", "Priority", "Deadline", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable taskTable = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Ready");
    private final List<Task> visibleTasks = new java.util.ArrayList<>();
    private final Set<UUID> remindedTaskIds = ConcurrentHashMap.newKeySet();
    private final java.util.Timer reminderTimer = new java.util.Timer("task-reminder-checker", true);

    private SmartTaskScheduler(TaskManager taskManager) {
        this.taskManager = taskManager;
        configureWindow();
        buildInterface();
        refreshTasks();
        startReminderTimer();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            configureLookAndFeel();
            Path storagePath = Path.of("data", "tasks.csv");
            TaskManager manager;
            try {
                manager = new TaskManager(storagePath);
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(null,
                        "Saved tasks could not be loaded. The application will start with an empty list.\n\n"
                                + exception.getMessage(),
                        "Storage warning", JOptionPane.WARNING_MESSAGE);
                try {
                    manager = new TaskManager(storagePath, false);
                } catch (IOException impossible) {
                    throw new IllegalStateException("Could not initialize task storage.", impossible);
                }
            }
            SmartTaskScheduler application = new SmartTaskScheduler(manager);
            application.frame.requestFocus();
        });
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
             | UnsupportedLookAndFeelException ignored) {
            // Swing's default look and feel remains available if the system one cannot be loaded.
        }
    }

    private void configureWindow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(850, 590));
        frame.setSize(1050, 700);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                reminderTimer.cancel();
            }
        });
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        root.add(createHeader(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);
        content.add(createTaskEntryPanel(), BorderLayout.NORTH);
        content.add(createTaskListPanel(), BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);

        statusLabel.setForeground(MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
        root.add(statusLabel, BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(19, 22, 19, 22));

        JLabel title = new JLabel("Smart Task Scheduler");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        JLabel subtitle = new JLabel("Plan clearly. Prioritize what matters.");
        subtitle.setForeground(new Color(205, 215, 232));
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel text = new JPanel(new BorderLayout(0, 5));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(subtitle, BorderLayout.SOUTH);
        header.add(text, BorderLayout.WEST);
        return header;
    }

    private JPanel createTaskEntryPanel() {
        JPanel card = createCard();
        card.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 7, 5, 7);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JLabel sectionTitle = new JLabel("Task details");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 4;
        card.add(sectionTitle, constraints);

        addFormField(card, constraints, "Title", titleField, 0, 1, 2);
        addFormField(card, constraints, "Priority", priorityBox, 2, 1, 1);
        addFormField(card, constraints, "Deadline", deadlineField, 3, 1, 1);

        deadlineField.setText(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                .format(INPUT_DATE_FORMAT));
        deadlineField.setToolTipText("Use yyyy-MM-dd HH:mm, for example 2026-10-05 14:30");
        titleField.setToolTipText("Enter a short description for the task");

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton addButton = createButton("Add Task", ACCENT, Color.WHITE);
        JButton editButton = createButton("Edit Selected", new Color(231, 237, 248), NAVY);
        JButton completeButton = createButton("Mark Complete", new Color(225, 242, 234),
                new Color(37, 115, 77));
        JButton deleteButton = createButton("Delete Selected", new Color(252, 232, 232),
                new Color(166, 59, 59));
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(completeButton);
        buttons.add(deleteButton);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 4;
        constraints.insets = new Insets(11, 7, 2, 7);
        card.add(buttons, constraints);

        addButton.addActionListener(event -> addTask());
        editButton.addActionListener(event -> editSelectedTask());
        completeButton.addActionListener(event -> completeSelectedTask());
        deleteButton.addActionListener(event -> deleteSelectedTask());
        return card;
    }

    private void addFormField(JPanel panel, GridBagConstraints constraints, String labelText,
                              java.awt.Component field, int column, int row, int width) {
        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED);
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.gridwidth = width;
        constraints.weightx = column == 0 || column == 2 ? 0.01 : 0.5;
        panel.add(label, constraints);

        constraints.gridy = row + 1;
        constraints.weightx = column == 0 || column == 2 ? 0.01 : 0.5;
        constraints.gridwidth = width;
        panel.add(field, constraints);
    }

    private JPanel createTaskListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        JLabel listTitle = new JLabel("Your tasks");
        listTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
        toolbar.add(listTitle, BorderLayout.WEST);

        JPanel filters = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        JLabel filterLabel = new JLabel("Filter");
        filterLabel.setForeground(MUTED);
        filters.add(filterLabel);
        filters.add(filterBox);
        toolbar.add(filters, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.setRowHeight(32);
        taskTable.setFillsViewportHeight(true);
        taskTable.setShowVerticalLines(false);
        taskTable.setGridColor(new Color(232, 236, 243));
        taskTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        taskTable.getTableHeader().setBackground(new Color(238, 242, 248));
        taskTable.getTableHeader().setForeground(NAVY);
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(350);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(190);
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        taskTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedTaskIntoForm();
            }
        });
        filterBox.addActionListener(event -> refreshTasks());

        JScrollPane scrollPane = new JScrollPane(taskTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(225, 230, 238)));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCard() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(228, 233, 241)),
                BorderFactory.createEmptyBorder(13, 15, 13, 15)));
        return panel;
    }

    private JButton createButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 234)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return button;
    }

    private void addTask() {
        TaskInput input = readTaskInput();
        if (input == null) {
            return;
        }
        try {
            taskManager.addTask(input.title(), input.priority(), input.deadline());
            refreshTasks();
            clearForm();
            statusLabel.setText("Task added and saved.");
        } catch (IOException exception) {
            showStorageError(exception);
        }
    }

    private void editSelectedTask() {
        Task selected = getSelectedTask();
        if (selected == null) {
            showMessage("Select a task to edit.", "No task selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TaskInput input = readTaskInput();
        if (input == null) {
            return;
        }
        try {
            taskManager.editTask(selected.getId(), input.title(), input.priority(), input.deadline());
            remindedTaskIds.remove(selected.getId());
            refreshTasks();
            statusLabel.setText("Task updated and saved.");
        } catch (IOException | IllegalArgumentException exception) {
            showStorageError(exception);
        }
    }

    private void deleteSelectedTask() {
        Task selected = getSelectedTask();
        if (selected == null) {
            showMessage("Select a task to delete.", "No task selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(frame,
                "Delete \"" + selected.getTitle() + "\"?", "Confirm deletion",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            taskManager.deleteTask(selected.getId());
            remindedTaskIds.remove(selected.getId());
            refreshTasks();
            clearForm();
            statusLabel.setText("Task deleted and saved.");
        } catch (IOException | IllegalArgumentException exception) {
            showStorageError(exception);
        }
    }

    private void completeSelectedTask() {
        Task selected = getSelectedTask();
        if (selected == null) {
            showMessage("Select a task to mark as complete.", "No task selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (selected.isCompleted()) {
            showMessage("This task is already completed.", "Task status",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            taskManager.completeTask(selected.getId());
            refreshTasks();
            statusLabel.setText("Task marked complete and saved.");
        } catch (IOException | IllegalArgumentException exception) {
            showStorageError(exception);
        }
    }

    private TaskInput readTaskInput() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showMessage("Enter a title for the task.", "Title required", JOptionPane.WARNING_MESSAGE);
            titleField.requestFocusInWindow();
            return null;
        }

        try {
            LocalDateTime deadline = LocalDateTime.parse(deadlineField.getText().trim(), INPUT_DATE_FORMAT);
            Task.Priority priority = (Task.Priority) priorityBox.getSelectedItem();
            if (priority == null) {
                showMessage("Choose a priority for the task.", "Priority required", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return new TaskInput(title, priority, deadline);
        } catch (DateTimeParseException exception) {
                    showMessage("""
                        Enter a valid deadline using yyyy-MM-dd HH:mm.
                        Example: 2026-10-05 14:30
                        """,
                    "Invalid deadline", JOptionPane.WARNING_MESSAGE);
            deadlineField.requestFocusInWindow();
            return null;
        }
    }

    private void refreshTasks() {
        String filter = (String) filterBox.getSelectedItem();
        LocalDate today = LocalDate.now();
        List<Task> filteredTasks = taskManager.getTasks().stream()
                .filter(task -> switch (filter == null ? "Show All" : filter) {
                    case "Today's Tasks" -> task.getDeadline().toLocalDate().equals(today);
                    case "High Priority" -> task.getPriority() == Task.Priority.HIGH;
                    case "Completed" -> task.isCompleted();
                    case "Pending" -> !task.isCompleted();
                    default -> true;
                })
                .toList();

        taskTable.clearSelection();
        visibleTasks.clear();
        visibleTasks.addAll(filteredTasks);
        tableModel.setRowCount(0);
        for (Task task : filteredTasks) {
            tableModel.addRow(new Object[]{
                    task.getTitle(),
                    task.getPriority().getDisplayName(),
                    task.getDeadline().format(DISPLAY_DATE_FORMAT),
                    task.isCompleted() ? "Completed" : "Pending"
            });
        }
        statusLabel.setText(filteredTasks.size() + (filteredTasks.size() == 1 ? " task" : " tasks")
                + " shown · ordered by priority and deadline");
    }

    private void loadSelectedTaskIntoForm() {
        Task selected = getSelectedTask();
        if (selected == null) {
            return;
        }
        titleField.setText(selected.getTitle());
        priorityBox.setSelectedItem(selected.getPriority());
        deadlineField.setText(selected.getDeadline().format(INPUT_DATE_FORMAT));
    }

    private Task getSelectedTask() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= visibleTasks.size()) {
            return null;
        }
        return visibleTasks.get(selectedRow);
    }

    private void clearForm() {
        titleField.setText("");
        priorityBox.setSelectedItem(Task.Priority.MEDIUM);
        deadlineField.setText(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                .format(INPUT_DATE_FORMAT));
        taskTable.clearSelection();
        titleField.requestFocusInWindow();
    }

    private void startReminderTimer() {
        reminderTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkForDueTasks();
            }
        }, 1_000L, 30_000L);
    }

    private void checkForDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        for (Task task : taskManager.getTasks()) {
            if (!task.isCompleted() && !task.getDeadline().isAfter(now)
                    && remindedTaskIds.add(task.getId())) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                        "The deadline for this task has been reached:\n\n"
                                + task.getTitle() + "\nDue: " + task.getDeadline().format(DISPLAY_DATE_FORMAT),
                        "Task deadline reminder", JOptionPane.INFORMATION_MESSAGE));
            }
        }
    }

    private void showStorageError(Exception exception) {
        statusLabel.setText("Could not save the change.");
        showMessage("The task change could not be saved.\n\n" + exception.getMessage(),
                "Storage error", JOptionPane.ERROR_MESSAGE);
    }

    private void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(frame, message, title, messageType);
    }

    private record TaskInput(String title, Task.Priority priority, LocalDateTime deadline) {
    }
}

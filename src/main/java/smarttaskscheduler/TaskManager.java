package smarttaskscheduler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

/** Owns the task queue and handles task persistence. */
public final class TaskManager {
    private static final Comparator<Task> TASK_ORDER =
            Comparator.comparingInt((Task task) -> task.getPriority().getLevel()).reversed()
                    .thenComparing(Task::getDeadline)
                    .thenComparing(Task::getId);

    private final PriorityQueue<Task> tasks = new PriorityQueue<>(TASK_ORDER);
    private final Path storageFile;

    public TaskManager(Path storageFile) throws IOException {
        this(storageFile, true);
    }

    /** The loadExisting option lets the UI recover if the saved file cannot be read. */
    public TaskManager(Path storageFile, boolean loadExisting) throws IOException {
        this.storageFile = storageFile;
        if (loadExisting) {
            loadTasks();
        }
    }

    public synchronized Task addTask(String title, Task.Priority priority, LocalDateTime deadline)
            throws IOException {
        Task task = new Task(UUID.randomUUID(), title, priority, deadline, false);
        tasks.add(task);
        saveTasks();
        return task;
    }

    public synchronized void editTask(UUID taskId, String title, Task.Priority priority,
                                      LocalDateTime deadline) throws IOException {
        Task existing = findTask(taskId);
        Task updated = existing.withDetails(title, priority, deadline);
        tasks.remove(existing);
        tasks.add(updated);
        saveTasks();
    }

    public synchronized void deleteTask(UUID taskId) throws IOException {
        tasks.remove(findTask(taskId));
        saveTasks();
    }

    public synchronized void completeTask(UUID taskId) throws IOException {
        Task existing = findTask(taskId);
        if (!existing.isCompleted()) {
            tasks.remove(existing);
            tasks.add(existing.markCompleted());
            saveTasks();
        }
    }

    /** Returns a priority-ordered snapshot without exposing the internal queue. */
    public synchronized List<Task> getTasks() {
        PriorityQueue<Task> snapshot = new PriorityQueue<>(TASK_ORDER);
        snapshot.addAll(tasks);
        List<Task> orderedTasks = new ArrayList<>(snapshot.size());
        while (!snapshot.isEmpty()) {
            orderedTasks.add(snapshot.poll());
        }
        return List.copyOf(orderedTasks);
    }

    private Task findTask(UUID taskId) {
        return tasks.stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The selected task no longer exists."));
    }

    private void loadTasks() throws IOException {
        if (!Files.exists(storageFile)) {
            return;
        }

        int lineNumber = 0;
        for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            try {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 5) {
                    throw new IllegalArgumentException("Expected five fields");
                }
                Task task = new Task(
                        UUID.fromString(fields[0]),
                        new String(Base64.getDecoder().decode(fields[4]), StandardCharsets.UTF_8),
                        Task.Priority.fromLevel(Integer.parseInt(fields[1])),
                        LocalDateTime.parse(fields[2]),
                        Boolean.parseBoolean(fields[3]));
                tasks.add(task);
            } catch (RuntimeException exception) {
                throw new IOException("Could not read saved task on line " + lineNumber + ".", exception);
            }
        }
    }

    private void saveTasks() throws IOException {
        Path parent = storageFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>(tasks.size());
        for (Task task : getTasks()) {
            String encodedTitle = Base64.getEncoder()
                    .encodeToString(task.getTitle().getBytes(StandardCharsets.UTF_8));
            lines.add(task.getId() + "\t"
                    + task.getPriority().getLevel() + "\t"
                    + task.getDeadline() + "\t"
                    + task.isCompleted() + "\t"
                    + encodedTitle);
        }
        Files.write(storageFile, lines, StandardCharsets.UTF_8);
    }
}

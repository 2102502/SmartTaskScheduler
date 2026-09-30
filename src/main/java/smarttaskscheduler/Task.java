package smarttaskscheduler;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Represents one item scheduled by the user. */
public final class Task {
    public enum Priority {
        HIGH("High", 3),
        MEDIUM("Medium", 2),
        LOW("Low", 1);

        private final String displayName;
        private final int level;

        Priority(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getLevel() {
            return level;
        }

        public static Priority fromLevel(int level) {
            for (Priority priority : values()) {
                if (priority.level == level) {
                    return priority;
                }
            }
            throw new IllegalArgumentException("Unknown priority level: " + level);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final UUID id;
    private final String title;
    private final Priority priority;
    private final LocalDateTime deadline;
    private final boolean completed;

    public Task(UUID id, String title, Priority priority, LocalDateTime deadline, boolean completed) {
        this.id = Objects.requireNonNull(id, "id");
        String cleanedTitle = Objects.requireNonNull(title, "title").trim();
        if (cleanedTitle.isEmpty()) {
            throw new IllegalArgumentException("Task title cannot be empty.");
        }
        this.title = cleanedTitle;
        this.priority = Objects.requireNonNull(priority, "priority");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.completed = completed;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Task withDetails(String newTitle, Priority newPriority, LocalDateTime newDeadline) {
        return new Task(id, newTitle, newPriority, newDeadline, completed);
    }

    public Task markCompleted() {
        return new Task(id, title, priority, deadline, true);
    }
}

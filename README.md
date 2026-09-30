# Smart Task Scheduler

## Project objective

Smart Task Scheduler is a small Java desktop application for organizing tasks by priority and deadline. It demonstrates object-oriented design, Swing event handling, a priority queue, date/time validation, reminders, and local file storage.

## Features

- Add, edit, delete, and complete tasks
- Store a title, priority, and deadline for each task
- Order tasks by priority (High, Medium, Low), then by earliest deadline
- Filter all tasks, today's tasks, high-priority tasks, completed tasks, or pending tasks
- Periodically check pending tasks and display a reminder when a deadline is reached
- Load and save tasks automatically in `data/tasks.csv`
- Validate task titles and deadline input

## Technologies used

- Java 17
- Java Swing (`JFrame`, `JTable`, and standard Swing controls)
- Java `PriorityQueue`, `Timer`, and NIO file I/O

No external libraries or JavaFX are used.

## PriorityQueue explanation

`TaskManager` uses a `PriorityQueue<Task>` as its main task collection. Its comparator sorts higher numeric priority levels first (High = 3, Medium = 2, Low = 1). Tasks at the same priority are ordered by their deadline, earliest first. The queue is copied and polled when the application needs an ordered display, so the internal queue remains available for future operations.

## Project structure

```text
smartTaskScheduler/
├── data/                         # Created automatically for saved tasks
├── src/main/java/smarttaskscheduler/
│   ├── SmartTaskScheduler.java   # Swing interface and application entry point
│   ├── Task.java                 # Task model and priority levels
│   └── TaskManager.java          # PriorityQueue operations and file storage
├── .gitignore
├── README.md
└── run.bat
```

## How to run

1. Install a Java 17 JDK and ensure `java` and `javac` are available on your PATH.
2. Open a terminal in the project directory.
3. Run `run.bat` on Windows. It compiles the source files into `out/` and launches the app.

The task file is created in the project-local `data/` directory the first time a task is saved. It is excluded from Git so personal tasks are not committed.

## Screenshots

Add application screenshots here after launching the application, for example:

- Main task list
- Task filtering and reminders

## Future improvements

- Add recurring tasks and configurable reminder intervals
- Support exporting and importing tasks
- Add search, keyboard shortcuts, and configurable themes
- Add automated unit tests for task ordering and persistence

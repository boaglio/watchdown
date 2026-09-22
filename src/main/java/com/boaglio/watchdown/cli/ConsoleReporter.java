package com.boaglio.watchdown.cli;

import com.boaglio.watchdown.Progress;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The one line per step that watchdown prints while it works.
 *
 * <p>Progress goes to stderr, so it never pollutes a pipe. stdout is reserved for the output folder
 * paths, one per line, which is what makes the tool scripting-friendly.
 *
 * <p>On a terminal the current step's line is redrawn in place with elapsed time, a spinner, and a
 * bar once the step knows how far along it is. The line appears the moment the step starts, before
 * anything is known about it: the first thing a run does is ask yt-dlp about the video, that can
 * take a while, and a blank screen is indistinguishable from a hang. What the step is waiting for
 * shows next to the spinner, and the title replaces it once yt-dlp has answered.
 *
 * <p>When stderr is not a terminal, or in verbose mode where the debug log needs the screen,
 * nothing is redrawn and the output is one plain line per step.
 *
 * <p>Java cannot see whether stderr is a terminal — {@link java.io.Console} answers for stdin and
 * stdout, and watchdown's stdout is usually a pipe, which is the whole point of it. So the
 * launcher, which can see it, passes the answer in {@code WATCHDOWN_PROGRESS}.
 */
public class ConsoleReporter {

    private static final int DOTS_COLUMN = 56;
    private static final int BAR_WIDTH = 20;
    private static final Duration FRAME = Duration.ofMillis(120);
    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final PrintStream out;
    private final PrintStream err;
    private final boolean verbose;
    private final boolean live;

    private long startedAt;
    private String header = "";
    private volatile String detail = "";
    private boolean printed;
    private int paintedWidth;
    private int frame;

    private volatile double fraction = -1;
    private volatile String note = "";
    private Thread ticker;

    public ConsoleReporter(PrintStream out, PrintStream err, boolean verbose) {
        this(out, err, verbose, isTerminal());
    }

    ConsoleReporter(PrintStream out, PrintStream err, boolean verbose, boolean live) {
        this.out = out;
        this.err = err;
        this.verbose = verbose;
        // A live bar would fight with the debug log for the same screen.
        this.live = live && !verbose;
    }

    private static boolean isTerminal() {
        return live(System.getenv("WATCHDOWN_PROGRESS"), consoleIsTerminal());
    }

    private static boolean consoleIsTerminal() {
        java.io.Console console = System.console();
        return console != null && console.isTerminal();
    }

    /**
     * {@code always} and {@code never} settle it; {@code auto}, or nothing at all, falls back to
     * what Java can see, which is stdout. The launcher sets {@code always} when stderr is a
     * terminal, so redirecting stdout to a file no longer costs you the progress line.
     */
    static boolean live(String setting, boolean consoleIsTerminal) {
        if (setting == null || setting.isBlank() || "auto".equalsIgnoreCase(setting.strip())) {
            return consoleIsTerminal;
        }
        return "always".equalsIgnoreCase(setting.strip());
    }

    /**
     * Starts the timer for a step whose detail is only known once the step has begun. On a
     * terminal the line goes up right away, so the wait for yt-dlp is visibly a wait.
     */
    public synchronized void stepStart(int index, int total, String label) {
        startedAt = System.nanoTime();
        header = "[%d/%d] %-12s ".formatted(index, total, label);
        detail = "";
        printed = false;
        fraction = -1;
        note = "";
        frame = 0;
        paintedWidth = 0;
        if (live) {
            printed = true;
            paint();
            startTicker();
        }
    }

    public void stepStart(int index, int total, String label, String detail) {
        stepStart(index, total, label);
        detail(detail);
    }

    /**
     * What this step is about — the video's title, the model — as soon as it is known. On a
     * terminal it joins the line already on screen; elsewhere it prints the line, and in verbose
     * mode it ends there so the debug log can follow beneath.
     */
    public synchronized void detail(String text) {
        if (live) {
            detail = text == null ? "" : text;
            // Whatever the step was waiting for, it has it now.
            note = "";
            repaint();
            return;
        }
        if (printed) {
            return;
        }
        printed = true;
        detail = text == null ? "" : text;
        if (verbose) {
            err.println(line());
        } else {
            err.print(line());
            err.flush();
        }
    }

    /**
     * What the step is waiting on right now, shown next to the spinner: the metadata, the
     * captions, the audio. It is the difference between a slow step and a stuck one.
     */
    public synchronized void doing(String what) {
        note = what == null ? "" : what;
        repaint();
    }

    private String line() {
        return header + detail;
    }

    /** The handle this step reports its progress into. */
    public Progress progress() {
        return new Progress() {
            @Override
            public void fraction(double value) {
                fraction = value;
                repaint();
            }

            @Override
            public void note(String value) {
                note = value == null ? "" : value;
                repaint();
            }
        };
    }

    public void stepDone() {
        finishLine("done  %.1fs".formatted(elapsedSeconds()));
    }

    public void stepFailed(String reason) {
        finishLine("FAILED  " + firstLineOf(reason));
    }

    /** The last step prints the folder it wrote, with no timing, as the spec shows it. */
    public void finalStep(int index, int total, String label, Path folder) {
        stopTicker();
        err.println("[%d/%d] %-12s %s".formatted(index, total, label, folder));
        printed = false;
    }

    /** The output folder path, on stdout, for the caller's script. */
    public void output(Path folder) {
        out.println(folder);
        out.flush();
    }

    public void error(String message) {
        stopTicker();
        err.println("watchdown: " + message);
    }

    public void info(String message) {
        err.println(message);
    }

    private void startTicker() {
        stopTicker();
        ticker = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(FRAME.toMillis());
                    frame++;
                    repaint();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "watchdown-progress");
        ticker.setDaemon(true);
        ticker.start();
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.interrupt();
            ticker = null;
        }
    }

    private synchronized void repaint() {
        if (live && printed) {
            paint();
        }
    }

    private void paint() {
        StringBuilder line = new StringBuilder(line());
        double done = fraction;
        if (done >= 0 && done <= 1) {
            int filled = (int) Math.round(done * BAR_WIDTH);
            line.append("  ").append("█".repeat(filled)).append("░".repeat(BAR_WIDTH - filled))
                    .append(" %3d%%".formatted(Math.round(done * 100)));
        } else {
            line.append("  ").append(SPINNER[Math.floorMod(frame, SPINNER.length)]);
        }
        if (!note.isEmpty()) {
            line.append("  ").append(note);
        }
        line.append("  ").append(clock(elapsedSeconds()));

        String text = line.toString();
        int padding = Math.max(0, paintedWidth - text.length());
        err.print("\r" + text + " ".repeat(padding));
        err.flush();
        paintedWidth = text.length();
    }

    private synchronized void finishLine(String tail) {
        stopTicker();
        if (!printed) {
            detail("");
            stopTicker();
        }
        if (verbose) {
            err.println("        " + tail);
        } else {
            if (live) {
                // Wipe the bar before the final line lands in the scrollback.
                err.print("\r" + " ".repeat(paintedWidth) + "\r");
                err.print(line());
            }
            int padding = Math.max(1, DOTS_COLUMN - line().length());
            err.println(" " + ".".repeat(padding) + " " + tail);
        }
        printed = false;
        paintedWidth = 0;
    }

    private double elapsedSeconds() {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }

    private static String clock(double seconds) {
        long total = Math.round(seconds);
        return "%d:%02d".formatted(total / 60, total % 60);
    }

    private static String firstLineOf(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}

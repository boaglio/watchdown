package com.boaglio.watchdown.cli;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * The one line per step that watchdown prints while it works.
 *
 * <p>Progress goes to stderr, so it never pollutes a pipe. stdout is reserved for the output folder
 * paths, one per line, which is what makes the tool scripting-friendly.
 */
public class ConsoleReporter {

    private static final int DOTS_COLUMN = 56;

    private final PrintStream out;
    private final PrintStream err;
    private final boolean verbose;

    private long startedAt;
    private String header = "";
    private boolean headerPrinted;

    public ConsoleReporter(PrintStream out, PrintStream err, boolean verbose) {
        this.out = out;
        this.err = err;
        this.verbose = verbose;
    }

    /** Starts the timer for a step whose detail is only known once the step has begun. */
    public void stepStart(int index, int total, String label) {
        startedAt = System.nanoTime();
        header = "[%d/%d] %-12s ".formatted(index, total, label);
        headerPrinted = false;
    }

    public void stepStart(int index, int total, String label, String detail) {
        stepStart(index, total, label);
        detail(detail);
    }

    /** Prints the step line. In verbose mode it ends there, so the debug log can follow beneath. */
    public void detail(String detail) {
        if (headerPrinted) {
            return;
        }
        headerPrinted = true;
        header = header + detail;
        if (verbose) {
            err.println(header);
        } else {
            err.print(header);
            err.flush();
        }
    }

    public void stepDone() {
        finishLine("done  %.1fs".formatted(elapsedSeconds()));
    }

    public void stepFailed(String reason) {
        finishLine("FAILED  " + firstLineOf(reason));
    }

    /** The last step prints the folder it wrote, with no timing, as the spec shows it. */
    public void finalStep(int index, int total, String label, Path folder) {
        err.println("[%d/%d] %-12s %s".formatted(index, total, label, folder));
        headerPrinted = false;
    }

    /** The output folder path, on stdout, for the caller's script. */
    public void output(Path folder) {
        out.println(folder);
        out.flush();
    }

    public void error(String message) {
        err.println("watchdown: " + message);
    }

    public void info(String message) {
        err.println(message);
    }

    private void finishLine(String tail) {
        if (!headerPrinted) {
            detail("");
        }
        if (verbose) {
            err.println("        " + tail);
        } else {
            int padding = Math.max(1, DOTS_COLUMN - header.length());
            err.println(" " + ".".repeat(padding) + " " + tail);
        }
        headerPrinted = false;
    }

    private double elapsedSeconds() {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }

    private static String firstLineOf(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}

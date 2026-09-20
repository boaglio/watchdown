package com.boaglio.watchdown.process;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** What an external command produced. */
public record ProcessResult(List<String> command, int exitCode, String stdout, String stderr, Duration duration) {

    public ProcessResult {
        command = List.copyOf(command);
    }

    public boolean successful() {
        return exitCode == 0;
    }

    public String commandLine() {
        return String.join(" ", command);
    }

    /** The last {@code lines} lines of stderr, which is what error messages quote. */
    public String tailOfStderr(int lines) {
        String[] all = stderr.strip().split("\n");
        int from = Math.max(0, all.length - lines);
        return String.join("\n", Arrays.asList(all).subList(from, all.length));
    }
}

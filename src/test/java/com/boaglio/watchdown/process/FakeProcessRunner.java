package com.boaglio.watchdown.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A {@link ProcessRunner} that replays canned results and records what it was asked to run. */
public class FakeProcessRunner implements ProcessRunner {

    private final Deque<ProcessResult> answers = new ArrayDeque<>();
    private final List<List<String>> invocations = new ArrayList<>();
    private Runnable sideEffect = () -> { };

    public FakeProcessRunner answering(String stdout) {
        return answering(0, stdout, "");
    }

    public FakeProcessRunner answering(int exitCode, String stdout, String stderr) {
        answers.add(new ProcessResult(List.of("recorded"), exitCode, stdout, stderr, Duration.ZERO));
        return this;
    }

    /** Runs when a command is executed, so a test can create the files the real tool would write. */
    public FakeProcessRunner doingOnRun(Runnable sideEffect) {
        this.sideEffect = sideEffect;
        return this;
    }

    @Override
    public ProcessResult run(List<String> command, Duration timeout, Path workingDirectory,
            java.util.function.Consumer<String> onLine) {
        invocations.add(List.copyOf(command));
        sideEffect.run();
        ProcessResult answer = answers.isEmpty()
                ? new ProcessResult(command, 0, "", "", Duration.ZERO)
                : answers.poll();
        answer.stdout().lines().forEach(onLine);
        answer.stderr().lines().forEach(onLine);
        return new ProcessResult(command, answer.exitCode(), answer.stdout(), answer.stderr(), Duration.ZERO);
    }

    public List<List<String>> invocations() {
        return List.copyOf(invocations);
    }

    public List<String> lastCommand() {
        return invocations.getLast();
    }
}

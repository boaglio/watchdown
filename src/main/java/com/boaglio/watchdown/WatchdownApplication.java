package com.boaglio.watchdown;

import com.boaglio.watchdown.cli.WatchdownCommand;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/** Non-web Spring Boot application whose only job is to run the picocli command. */
@SpringBootApplication
public class WatchdownApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(
                new SpringApplication(WatchdownApplication.class).run(args)));
    }

    /** Carries the command's exit code out to {@link SpringApplication#exit}. */
    static class CommandExitCode implements ExitCodeGenerator {

        private int exitCode;

        @Override
        public int getExitCode() {
            return exitCode;
        }
    }

    @Bean
    CommandExitCode commandExitCode() {
        return new CommandExitCode();
    }

    @Bean
    ApplicationRunner commandRunner(WatchdownCommand command, IFactory factory, CommandExitCode exitCode) {
        return arguments -> exitCode.exitCode = new CommandLine(command, factory)
                .execute(arguments.getSourceArgs());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

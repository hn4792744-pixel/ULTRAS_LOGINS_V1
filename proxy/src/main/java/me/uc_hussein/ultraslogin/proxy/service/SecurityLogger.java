package me.uc_hussein.ultraslogin.proxy.service;

import me.uc_hussein.ultraslogin.common.SecurityLog;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Security events go to logs/security-YYYY-MM-DD.log (and the console). Callers only pass already-sanitized
 * text: passwords, hashes, tokens and keys are never part of any message.
 */
public final class SecurityLogger implements SecurityLog {
    private final Path dir;
    private final Logger logger;
    private final BooleanSupplier console;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UltrasLogin-Log");
        t.setDaemon(true);
        return t;
    });

    public SecurityLogger(Path dataDir, Logger logger, BooleanSupplier console) {
        this.dir = dataDir.resolve("logs");
        this.logger = logger;
        this.console = console;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            logger.warn("Could not create the logs directory: {}", e.getMessage());
        }
    }

    @Override
    public void event(String category, String message) {
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] [" + category + "] " + message;
        if (console.getAsBoolean()) {
            logger.info("[{}] {}", category, message);
        }
        LocalDate day = LocalDate.now();
        try {
            io.execute(() -> {
                try {
                    Files.writeString(dir.resolve("security-" + day + ".log"), line + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    logger.warn("Could not write the security log: {}", e.getMessage());
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    public void close() {
        io.shutdown();
        try {
            io.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.mybot.velocity.util;

import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Lightweight file watcher used for hot-reloading configs.
 */
public final class FileWatcher implements Closeable {

    private final WatchService watchService;
    private final ExecutorService executor;
    private final Map<WatchKey, Path> directories = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Consumer<Path>> listeners = new CopyOnWriteArrayList<>();
    private final Logger logger;
    private volatile boolean running;

    public FileWatcher(Logger logger) throws IOException {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mybot-file-watcher");
            t.setDaemon(true);
            return t;
        });
    }

    public void register(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        WatchKey key = directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
        directories.put(key, directory);
    }

    public void addListener(Consumer<Path> consumer) {
        listeners.add(consumer);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor.submit(this::pollLoop);
    }

    private void pollLoop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                Path parent = directories.get(key);
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path context = (Path) event.context();
                    Path absolute = parent == null ? context : parent.resolve(context);
                    for (Consumer<Path> listener : listeners) {
                        try {
                            listener.accept(absolute);
                        } catch (Exception ex) {
                            logger.error("Watcher listener failed for {}", absolute, ex);
                        }
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                logger.warn("File watcher error", ex);
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        executor.shutdownNow();
        watchService.close();
    }
}

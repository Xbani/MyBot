package com.mybot.velocity.schematic;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides access to schematic metadata. Real parsing of .schem (NBT) can be
 * plugged later; current implementation simply caches file sizes and existence
 * checks so builders can validate configuration quickly.
 */
public final class SchematicService {

    private final Path schematicsDir;
    private final Logger logger;
    private final Map<String, SchematicMetadata> cache = new ConcurrentHashMap<>();

    public SchematicService(Path schematicsDir, Logger logger) {
        this.schematicsDir = Objects.requireNonNull(schematicsDir, "schematicsDir");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Optional<SchematicMetadata> metadata(String fileName) {
        return Optional.ofNullable(cache.computeIfAbsent(fileName, this::probe));
    }

    public void invalidateAll() {
        cache.clear();
    }

    private SchematicMetadata probe(String name) {
        Path file = schematicsDir.resolve(name).normalize();
        if (!Files.exists(file)) {
            logger.warn("Schematic {} missing from {}", name, schematicsDir);
            return null;
        }
        try {
            long size = Files.size(file);
            return new SchematicMetadata(name, file, size);
        } catch (IOException e) {
            logger.error("Failed to examine schematic {}", file, e);
            return null;
        }
    }

    public record SchematicMetadata(String id, Path path, long bytes) { }
}

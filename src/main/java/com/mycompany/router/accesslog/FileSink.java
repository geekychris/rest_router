package com.mycompany.router.accesslog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Appends JSON lines to a local file. Pair with logrotate or fluent-bit
 * tailers for shipment.
 */
public class FileSink implements AccessLogSink {

    private static final Logger log = LoggerFactory.getLogger(FileSink.class);

    private final ObjectMapper mapper;
    private final Path path;
    private BufferedWriter writer;

    public FileSink(ObjectMapper mapper, String path) throws IOException {
        this.mapper = mapper;
        this.path = Path.of(path);
        Files.createDirectories(this.path.getParent());
        this.writer = Files.newBufferedWriter(
                this.path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public synchronized void publish(AccessLogEvent event) {
        try {
            writer.write(mapper.writeValueAsString(event));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.warn("Failed to write access log to {}", path, e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException e) {
            log.warn("Failed to close access log file", e);
        }
    }
}

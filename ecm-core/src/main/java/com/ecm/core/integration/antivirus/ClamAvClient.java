package com.ecm.core.integration.antivirus;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Low-level ClamAV daemon (clamd) client using INSTREAM protocol.
 *
 * Protocol:
 * - Send: zINSTREAM\0
 * - For each chunk: 4-byte big-endian size followed by data
 * - End: 4-byte zero
 * - Receive: scan result string
 */
@Slf4j
public class ClamAvClient implements AutoCloseable {

    private static final byte[] INSTREAM_CMD = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PING_CMD = "zPING\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VERSION_CMD = "zVERSION\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 2048;
    private static final int DEFAULT_TIMEOUT = 30000;

    private final String host;
    private final int port;
    private final int timeout;

    public ClamAvClient(String host, int port) {
        this(host, port, DEFAULT_TIMEOUT);
    }

    public ClamAvClient(String host, int port, int timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
    }

    /**
     * Ping the ClamAV daemon to check if it's available.
     */
    public boolean ping() {
        try (Socket socket = createSocket()) {
            socket.getOutputStream().write(PING_CMD);
            socket.getOutputStream().flush();

            String response = readResponse(socket.getInputStream());
            return "PONG".equals(response.trim());
        } catch (Exception e) {
            log.warn("ClamAV ping failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the ClamAV version.
     */
    public String getVersion() {
        try (Socket socket = createSocket()) {
            socket.getOutputStream().write(VERSION_CMD);
            socket.getOutputStream().flush();

            return readResponse(socket.getInputStream()).trim();
        } catch (Exception e) {
            log.warn("Failed to get ClamAV version: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Scan an input stream for viruses using INSTREAM protocol.
     *
     * @param inputStream the content to scan
     * @return ScanResult containing clean status and any detected threat name
     */
    public ScanResult scan(InputStream inputStream) throws IOException {
        try (Socket socket = createSocket()) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Send INSTREAM command
            out.write(INSTREAM_CMD);
            out.flush();

            // Stream content in chunks
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Send chunk size (4 bytes, big-endian)
                byte[] sizeBytes = ByteBuffer.allocate(4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(bytesRead)
                        .array();
                out.write(sizeBytes);

                // Send chunk data
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            // Send terminating zero-length chunk
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            // Read and parse response
            String response = readResponse(in).trim();
            log.debug("ClamAV response for {} bytes: {}", totalBytes, response);

            return parseResponse(response);
        }
    }

    /**
     * Scan a file for viruses.
     */
    public ScanResult scanFile(File file) throws IOException {
        try (InputStream fis = new BufferedInputStream(new FileInputStream(file))) {
            return scan(fis);
        }
    }

    /**
     * Scan byte array for viruses.
     */
    public ScanResult scan(byte[] data) throws IOException {
        try (InputStream bis = new ByteArrayInputStream(data)) {
            return scan(bis);
        }
    }

    private Socket createSocket() throws IOException {
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(timeout);
        return socket;
    }

    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int bytesRead;

        while ((bytesRead = in.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            // ClamAV response ends with null byte or newline
            if (bytesRead > 0 && (buffer[bytesRead - 1] == 0 || buffer[bytesRead - 1] == '\n')) {
                break;
            }
        }

        return baos.toString(StandardCharsets.US_ASCII).replace("\0", "");
    }

    private ScanResult parseResponse(String response) {
        // Expected formats:
        // "stream: OK" - clean
        // "stream: <threat_name> FOUND" - infected
        // "stream: <error_message> ERROR" - error

        if (response.contains(" OK")) {
            return ScanResult.clean();
        } else if (response.contains(" FOUND")) {
            // Extract threat name: "stream: Eicar-Test-Signature FOUND"
            String threatName = response
                    .replace("stream:", "")
                    .replace("FOUND", "")
                    .trim();
            return ScanResult.infected(threatName);
        } else if (response.contains(" ERROR")) {
            String errorMsg = response
                    .replace("stream:", "")
                    .replace("ERROR", "")
                    .trim();
            return ScanResult.error(errorMsg);
        } else {
            return ScanResult.error("Unknown ClamAV response: " + response);
        }
    }

    @Override
    public void close() {
        // Nothing to close - sockets are created per-operation
    }

    /**
     * Result of a virus scan operation.
     */
    public record ScanResult(Status status, String threatName, String errorMessage) {

        public enum Status {
            CLEAN,
            INFECTED,
            ERROR
        }

        public static ScanResult clean() {
            return new ScanResult(Status.CLEAN, null, null);
        }

        public static ScanResult infected(String threatName) {
            return new ScanResult(Status.INFECTED, threatName, null);
        }

        public static ScanResult error(String errorMessage) {
            return new ScanResult(Status.ERROR, null, errorMessage);
        }

        public boolean isClean() {
            return status == Status.CLEAN;
        }

        public boolean isInfected() {
            return status == Status.INFECTED;
        }

        public boolean isError() {
            return status == Status.ERROR;
        }
    }
}

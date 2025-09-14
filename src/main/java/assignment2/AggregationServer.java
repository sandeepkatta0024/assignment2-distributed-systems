package assignment2;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * AggregationServer listens for Content Servers PUTting weather data and Read Clients GETting aggregated weather data.
 * Manages Lamport clocks for ordering and expires old data after 30 seconds.
 */
public class AggregationServer {
    // Expiry threshold for weather data in milliseconds (30 seconds)
    private static final int EXPIRY_MS = 30000;
    // Persistent data store file name
    private static final String DATA_STORE = "server_data.json";

    // Thread-safe map storing weather data keyed by content server ID
    static final Map<String, WeatherRecord> data = new ConcurrentHashMap<>();

    // Lamport clock instance used for synchronization of events
    private static final LamportClock clock = new LamportClock();

    /**
     * Main method to start the Aggregation Server on specified port (default 4567).
     * Loads persisted data, starts expiry scheduler, and accepts client connections.
     *
     * @param args Optional first argument is port number.
     * @throws Exception on server error.
     */
    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 4567;

        loadFromDisk();

        // Scheduled executor allows periodic expiry clean-up
        ScheduledExecutorService expiryService = Executors.newSingleThreadScheduledExecutor();
        expiryService.scheduleAtFixedRate(AggregationServer::removeExpired, 2, 2, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("AggregationServer started on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleConnection(socket)).start();
            }
        }
    }

    /**
     * Removes expired weather data entries older than EXPIRY_MS.
     * Saves the updated data to disk if removals occur.
     */
    static void removeExpired() {
        long now = System.currentTimeMillis();
        boolean removed = data.entrySet().removeIf(entry -> now - entry.getValue().timestamp > EXPIRY_MS);
        if (removed) saveToDisk();
    }

    /**
     * Handles a client connection: reads the request and delegates to PUT or GET handlers.
     * Returns 400 Bad Request for unsupported methods.
     *
     * @param socket Client socket connection.
     */
    static void handleConnection(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            String requestLine = in.readLine();
            if (requestLine == null) return;

            if (requestLine.startsWith("PUT")) handlePut(in, out);
            else if (requestLine.startsWith("GET")) handleGet(out);
            else {
                out.write("HTTP/1.1 400 Bad Request\r\n\r\n");
                out.flush();
            }
        } catch (Exception e) {
            // Optional logging here
        }
    }

    /**
     * Handles HTTP PUT requests: reads headers and JSON body, parses using custom JSON parser,
     * updates Lamport clock, updates stored data, and responds with appropriate code.
     *
     * @param in BufferedReader for client input.
     * @param out BufferedWriter for client output.
     * @throws IOException on IO errors.
     */
    private static void handlePut(BufferedReader in, BufferedWriter out) throws IOException {
        int lamportReceived = 0;
        int contentLength = 0;
        String line;

        // Read headers
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Lamport-Clock:")) {
                lamportReceived = Integer.parseInt(line.split(":")[1].trim());
            } else if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
        }

        if (contentLength <= 0) {
            out.write("HTTP/1.1 400 Bad Request\r\n\r\nMissing Content-Length.\r\n");
            out.flush();
            return;
        }

        // Read JSON body characters exactly
        char[] body = new char[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = in.read(body, read, contentLength - read);
            if (n == -1) break;
            read += n;
        }
        String json = new String(body);

        // Parse JSON to map with custom parser
        Map<String, String> obj;
        try {
            obj = SimpleJsonParser.parse(json);
            if (!obj.containsKey("id") || obj.get("id").isEmpty()) throw new Exception("Missing id");
        } catch (Exception e) {
            out.write("HTTP/1.1 500 Internal Server Error\r\n\r\nInvalid JSON or missing 'id'.\r\n");
            out.flush();
            return;
        }

        // Update Lamport clock
        clock.update(lamportReceived);

        // Store data as WeatherRecord
        String id = obj.get("id");
        boolean isFirst = !data.containsKey(id);
        data.put(id, new WeatherRecord(obj, clock.getTime()));

        // Persist data immediately
        saveToDisk();

        System.out.println("PUT received for id: " + id + ", Lamport: " + clock.getTime());

        // Respond with 201 if new, otherwise 200 OK
        out.write(isFirst ? "HTTP/1.1 201 Created\r\n" : "HTTP/1.1 200 OK\r\n");
        out.write("Lamport-Clock: " + clock.getTime() + "\r\n\r\n");
        out.flush();
    }

    /**
     * Handles HTTP GET requests: sends aggregated data as JSON array or 404 if no data.
     *
     * @param out BufferedWriter for client output.
     * @throws IOException on IO errors.
     */
    private static void handleGet(BufferedWriter out) throws IOException {
        clock.tick();      // Lamport clock tick on event
        removeExpired();   // Remove expired entries

        if (data.isEmpty()) {
            out.write("HTTP/1.1 404 Not Found\r\n\r\nNo weather data available.");
            out.flush();
            return;
        }

        // Build JSON array string manually
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (WeatherRecord record : data.values()) {
            if (!first) sb.append(",");
            sb.append(SimpleJsonParser.toJson(record.getData()));
            first = false;
        }
        sb.append("]");

        out.write("HTTP/1.1 200 OK\r\n");
        out.write("Lamport-Clock: " + clock.getTime() + "\r\n");
        out.write("Content-Type: application/json\r\n\r\n");
        out.write(sb.toString());
        out.flush();
    }

    /**
     * Saves current data persistently to disk.
     * Synchronized to protect against concurrent writes.
     */
    synchronized static void saveToDisk() {
        try (Writer writer = new FileWriter(DATA_STORE)) {
            // Write as JSON array of objects
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (WeatherRecord record : data.values()) {
                if (!first) sb.append(",");
                sb.append(SimpleJsonParser.toJson(record.getData()));
                first = false;
            }
            sb.append("]");
            writer.write(sb.toString());
        } catch (IOException e) {
            // Optional logging
            System.err.println("Failed to save data: " + e.getMessage());
        }
    }

    /**
     * Loads persisted data from disk into memory.
     * Synchronized to prevent conflicts with save.
     */
    synchronized static void loadFromDisk() {
        File file = new File(DATA_STORE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            String content = sb.toString();
            if (content.isEmpty()) return;

            // Expecting a JSON array of objects
            if (!content.startsWith("[") || !content.endsWith("]")) return;

            // Remove the surrounding brackets
            content = content.substring(1, content.length() - 1);

            // Split top-level objects by ',', simple split (may not handle nested commas)
            String[] items = content.split("},\\s*\\{");
            for (String s : items) {
                String item = s;
                if (!item.startsWith("{")) item = "{" + item;
                if (!item.endsWith("}")) item = item + "}";

                Map<String, String> obj = SimpleJsonParser.parse(item);
                if (obj.containsKey("id")) {
                    data.put(obj.get("id"), new WeatherRecord(obj, 0));
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Failed to load data: " + e.getMessage());
        }
    }
}

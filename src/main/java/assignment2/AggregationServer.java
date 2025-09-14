package assignment2;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;

public class AggregationServer {
    private static final int EXPIRY_MS = 30000; // 30 seconds expiry
    private static final String DATA_STORE = "server_data.json";
    static final Map<String, WeatherRecord> data = new ConcurrentHashMap<>();
    private static final LamportClock clock = new LamportClock();
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        int port = (args.length>0) ? Integer.parseInt(args[0]) : 4567;

        loadFromDisk();

        ScheduledExecutorService expiryService = Executors.newSingleThreadScheduledExecutor();
        expiryService.scheduleAtFixedRate(AggregationServer::removeExpired, 2, 2, TimeUnit.SECONDS);

        try(ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("AggregationServer started on port " + port);

            while(true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleConnection(socket)).start();
            }
        }
    }

    static void removeExpired() {
        long now = System.currentTimeMillis();
        boolean removed = data.entrySet().removeIf(entry -> now - entry.getValue().timestamp > EXPIRY_MS);
        if(removed) saveToDisk();
    }

    static void handleConnection(Socket socket) {
        try (socket; BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            try {

                String requestLine = in.readLine();
                if (requestLine == null) return;

                if (requestLine.startsWith("PUT")) handlePut(in, out);
                else if (requestLine.startsWith("GET")) handleGet(out);
                else {
                    out.write("HTTP/1.1 400 Bad Request\r\n\r\n");
                    out.flush();
                }
            } catch (Exception e) {
                // log or ignore
            }
        } catch (Exception ignored) {
        }
    }

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

        // Read body
        char[] body = new char[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = in.read(body, read, contentLength - read);
            if (n == -1) break;
            read += n;
        }
        String json = new String(body);

        // Parse JSON
        JsonObject obj;
        try {
            obj = gson.fromJson(json, JsonObject.class);
            if (!obj.has("id")) throw new Exception("Missing id");
        } catch (Exception e) {
            out.write("HTTP/1.1 500 Internal Server Error\r\n\r\nInvalid JSON or missing 'id'.\r\n");
            out.flush();
            return;
        }

        // Update Lamport clock
        clock.update(lamportReceived);

        // Store data
        String id = obj.get("id").getAsString();
        boolean isFirst = !data.containsKey(id);
        data.put(id, new WeatherRecord(obj, clock.getTime()));

        // Save immediately to disk
        saveToDisk();

        System.out.println("PUT received for id: " + id + ", Lamport: " + clock.getTime());

        // Send response
        out.write(isFirst ? "HTTP/1.1 201 Created\r\n" : "HTTP/1.1 200 OK\r\n");
        out.write("Lamport-Clock: " + clock.getTime() + "\r\n\r\n");
        out.flush();
    }

    private static void handleGet(BufferedWriter out) throws IOException {
        clock.tick();
        removeExpired();

        if(data.isEmpty()) {
            out.write("HTTP/1.1 404 Not Found\r\n\r\nNo weather data available.");
            out.flush();
            return;
        }

        out.write("HTTP/1.1 200 OK\r\n");
        out.write("Lamport-Clock: " + clock.getTime() + "\r\n");
        out.write("Content-Type: application/json\r\n\r\n");

        // Aggregate all weather JSON objects into an array
        JsonArray arr = new JsonArray();
        data.values().forEach(record -> arr.add(record.obj));
        out.write(gson.toJson(arr));
        out.flush();
    }

    synchronized static void saveToDisk() {
        try(Writer writer = new FileWriter(DATA_STORE)) {
            JsonArray arr = new JsonArray();
            data.values().forEach(record -> arr.add(record.obj));
            gson.toJson(arr, writer);
        } catch(IOException e) {
            // ignore or log error
        }
    }

    synchronized static void loadFromDisk() {
        try(FileReader reader = new FileReader(DATA_STORE)) {
            JsonElement el = JsonParser.parseReader(reader);
            if(el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                for(JsonElement element : arr) {
                    JsonObject obj = element.getAsJsonObject();
                    String id = obj.get("id").getAsString();
                    data.put(id, new WeatherRecord(obj, 0));
                }
            }
        } catch(IOException ignored){}
    }
}

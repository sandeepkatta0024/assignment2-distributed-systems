package assignment2;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * ContentServer reads weather data from a text file and sends it as JSON to the AggregationServer via HTTP-like PUT request.
 * It maintains a LamportClock for event ordering and retries on connection failure.
 */
public class ContentServer {
    // Lamport clock instance to maintain causal consistency
    private static LamportClock clock = new LamportClock();

    /**
     * Main accepts two args: target host:port and path to data file.
     * Reads the data file, converts key-value pairs to JSON string,
     * and sends repeatedly PUT requests with Lamport clock,
     * retrying connections on failures every 2 seconds.
     *
     * @param args [0] host:port, [1] data file path
     * @throws Exception IO or sleep interruption errors
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java ContentServer <host:port> <datafile>");
            return;
        }
        String[] parts = args[0].split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        String filePath = args[1];

        // Parse the text data file into key-value pairs map
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                int idx = line.indexOf(':');
                if (idx < 0) continue;
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                map.put(key, value);
            }
        }

        // Validate presence of mandatory 'id' field
        if (!map.containsKey("id")) {
            System.out.println("Data file must contain 'id' field.");
            return;
        }

        // Serialize map to JSON string using custom JSON parser
        String json = SimpleJsonParser.toJson(map);

        // Retry loop to send PUT request until successful
        while (true) {
            try (Socket socket = new Socket(host, port);
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                clock.tick();  // Increment Lamport clock before sending

                // Compose HTTP PUT request with headers and JSON body
                out.write("PUT /weather.json HTTP/1.1\r\n");
                out.write("Host: " + host + "\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("Lamport-Clock: " + clock.getTime() + "\r\n");
                out.write("Content-Length: " + json.length() + "\r\n");
                out.write("\r\n");
                out.write(json);
                out.flush();

                // Read and display server response status line
                String response = in.readLine();
                System.out.println("Sending PUT to " + host + ":" + port + " with Lamport " + clock.getTime());
                System.out.println("Server response: " + response);

                // Update Lamport clock based on server response headers
                while ((response = in.readLine()) != null && !response.isEmpty()) {
                    if (response.startsWith("Lamport-Clock:")) {
                        int servClock = Integer.parseInt(response.split(":")[1].trim());
                        clock.update(servClock);
                    }
                }
                break; // Exit retry loop on success
            } catch (IOException e) {
                System.out.println("Retrying connection in 2s...");
                Thread.sleep(2000);
            }
        }
    }
}

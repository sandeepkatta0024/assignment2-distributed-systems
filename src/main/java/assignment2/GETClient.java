package assignment2;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * GETClient connects to the AggregationServer and sends a GET request for weather data.
 * It maintains a LamportClock and parses the JSON response using a custom JSON parser.
 */
public class GETClient {
    private static LamportClock clock = new LamportClock();

    /**
     * Main entry point: expects one argument - host:port or full URL.
     * Sends GET request and prints parsed weather data or error response.
     *
     * @param args [0] the server address in host:port or URL format
     * @throws Exception on IO or URL errors
     */
    public static void main(String[] args) throws Exception {
        if(args.length != 1){
            System.out.println("Usage: java GETClient <host:port> or <http://host:port/path>");
            return;
        }

        String urlString = args[0];
        if(!urlString.startsWith("http://") && !urlString.startsWith("https://")){
            urlString = "http://" + urlString;
        }

        URL url = new URL(urlString);
        String host = url.getHost();
        int port = (url.getPort() == -1) ? url.getDefaultPort() : url.getPort();
        String path = (url.getPath().isEmpty()) ? "/weather.json" : url.getPath();

        clock.tick();

        try (
                Socket socket = new Socket(host, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            // Send GET request with Lamport clock header
            out.write("GET " + path + " HTTP/1.1\r\n");
            out.write("Host: " + host + "\r\n");
            out.write("Lamport-Clock: " + clock.getTime() + "\r\n\r\n");
            out.flush();

            // Read status line
            String status = in.readLine();
            if (status == null) return;
            System.out.println(status);

            // Read headers and update Lamport clock if present
            String line;
            while ((line = in.readLine()) != null && !line.trim().isEmpty()) {
                if (line.startsWith("Lamport-Clock:")) {
                    int servClock = Integer.parseInt(line.split(":")[1].trim());
                    clock.update(servClock);
                }
            }

            // Read response body
            StringBuilder sb = new StringBuilder();
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }

            String body = sb.toString().trim();
            if (body.isEmpty()) return;

            // Check if body is JSON array
            if (body.startsWith("[")) {
                // Parse JSON array using custom parser
                List<String> jsonObjects = splitTopLevelJsonObjects(body);
                for (String jsonObject : jsonObjects) {
                    Map<String, String> obj = SimpleJsonParser.parse(jsonObject);
                    obj.forEach((key, value) -> System.out.println(key + ": " + value));
                    System.out.println();
                }
            } else {
                // Print error or plain text response
                System.out.println(body);
            }
        }
    }

    /**
     * Helper method to split a JSON array string into individual JSON object strings.
     * Assumes flat JSON objects without nested arrays.
     *
     * @param jsonArrayStr String starting with '[' and ending with ']'
     * @return List of JSON object strings including braces '{}'
     */
    private static List<String> splitTopLevelJsonObjects(String jsonArrayStr) {
        List<String> objects = new ArrayList<>();
        int level = 0;
        int start = 0;
        for (int i = 0; i < jsonArrayStr.length(); i++) {
            char c = jsonArrayStr.charAt(i);
            if (c == '{') {
                if (level == 0) start = i;
                level++;
            } else if (c == '}') {
                level--;
                if (level == 0) {
                    objects.add(jsonArrayStr.substring(start, i + 1));
                }
            }
        }
        return objects;
    }
}

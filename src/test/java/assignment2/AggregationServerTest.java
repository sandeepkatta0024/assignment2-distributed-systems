package assignment2;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * JUnit test class for AggregationServer.
 * Tests PUT and GET HTTP-like requests, data expiry, and persistence functionality.
 * Starts a real server socket on localhost and port 4567 for integration tests.
 */
public class AggregationServerTest {

    private static final String TEST_FILE = "test_server_data.json";  // Test persistent store file
    private static final String LOCALHOST = "localhost";
    private static final int TEST_PORT = 4567;

    private static ServerSocket serverSocket;
    private static ExecutorService serverExecutor;

    /**
     * Set up method executed once before all tests.
     * Deletes any test data file, clears in-memory data, and starts the AggregationServer asynchronously.
     */
    @BeforeClass
    public static void startServer() throws Exception {
        // Remove old test data file for clean testing
        Files.deleteIfExists(Paths.get(TEST_FILE));
        AggregationServer.data.clear();
        startServerAsync();
        Thread.sleep(500); // Wait for server to start
    }

    /**
     * Tear down method executed once after all tests.
     * Closes server socket, shuts down executor, and deletes test data file.
     */
    @AfterClass
    public static void stopServer() throws Exception {
        if (serverSocket != null) serverSocket.close();
        if (serverExecutor != null) serverExecutor.shutdownNow();
        Files.deleteIfExists(Paths.get(TEST_FILE));
    }

    /**
     * Tests that sending a PUT request with new data returns HTTP 201 Created,
     * and subsequent PUT for same id returns HTTP 200 OK.
     */
    @Test
    public void testPutAddsDataAndReturns201Or200() throws IOException {
        String json = "{\"id\":\"ID1\",\"temp\":25}";
        HttpResponse response = sendPut(json, 0);
        assertEquals("201 Created", response.status);
        assertTrue(response.headers.containsKey("Lamport-Clock"));

        // Repeat PUT with updated Lamport clock; expect 200 OK
        response = sendPut(json, Integer.parseInt(response.headers.get("Lamport-Clock")));
        assertEquals("200 OK", response.status);
    }

    /**
     * Tests that a GET request returns 404 Not Found when no data is present,
     * and returns 200 OK with data after a PUT.
     */
    @Test
    public void testGetReturnsDataOr404() throws Exception {
        Files.deleteIfExists(Paths.get(TEST_FILE));
        AggregationServer.data.clear();
        AggregationServer.saveToDisk();

        HttpResponse response = sendGet(0);
        assertEquals("404 Not Found", response.status);

        String json = "{\"id\":\"ID1\",\"temp\":25}";
        sendPut(json, 0);

        response = sendGet(1);
        assertEquals("200 OK", response.status);
        assertTrue(response.body.contains("ID1"));
    }

    /**
     * Tests that expired data (older than 30 seconds) is removed by expiry mechanism.
     */
    @Test
    public void testExpiryRemovesOldEntry() throws Exception {
        String json = "{\"id\":\"ID1\",\"temp\":25}";
        sendPut(json, 0);

        // Access internal data map using reflection for test manipulation
        var dataField = AggregationServer.class.getDeclaredField("data");
        dataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, WeatherRecord> dataMap = (Map<String, WeatherRecord>) dataField.get(null);

        // Simulate old timestamp to trigger expiry
        WeatherRecord record = dataMap.get("ID1");
        record.timestamp = System.currentTimeMillis() - 31000; // 31 seconds ago

        AggregationServer.removeExpired();

        HttpResponse response = sendGet(1);
        assertEquals("404 Not Found", response.status);
    }

    /**
     * Tests persistence by saving data, clearing memory, reloading from disk,
     * and verifying the previously saved record is restored.
     */
    @Test
    public void testPersistence() throws Exception {
        String json = "{\"id\":\"ID1\",\"temp\":25}";
        sendPut(json, 0);

        var dataField = AggregationServer.class.getDeclaredField("data");
        dataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, WeatherRecord> dataMap = (Map<String, WeatherRecord>) dataField.get(null);
        dataMap.clear();

        AggregationServer.loadFromDisk();

        assertTrue(dataMap.containsKey("ID1"));
    }

    /**
     * Starts AggregationServer asynchronously in a separate thread using a ServerSocket
     * and handles connections via AggregationServer.handleConnection method.
     *
     * @throws IOException if server socket fails to bind
     */
    private static void startServerAsync() throws IOException {
        serverSocket = new ServerSocket(TEST_PORT);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    AggregationServer.handleConnection(socket);
                }
            } catch (IOException ignored) {
            }
        });
    }

    /**
     * Sends a PUT HTTP-like request with given JSON body and Lamport clock value.
     *
     * @param json         JSON string to send as body
     * @param lamportClock Lamport clock to include in request header
     * @return HttpResponse parsed from server reply
     * @throws IOException on socket or IO error
     */
    private HttpResponse sendPut(String json, int lamportClock) throws IOException {
        try (Socket socket = new Socket(LOCALHOST, TEST_PORT)) {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.write("PUT /weather.json HTTP/1.1\r\n");
            out.write("Content-Type: application/json\r\n");
            out.write("Content-Length: " + json.length() + "\r\n");
            out.write("Lamport-Clock: " + lamportClock + "\r\n\r\n");
            out.write(json);
            out.flush();

            return readResponse(in);
        }
    }

    /**
     * Sends a GET HTTP-like request with given Lamport clock value.
     *
     * @param lamportClock Lamport clock to include in request header
     * @return HttpResponse parsed from server reply
     * @throws IOException on socket or IO error
     */
    private HttpResponse sendGet(int lamportClock) throws IOException {
        try (Socket socket = new Socket(LOCALHOST, TEST_PORT)) {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.write("GET /weather.json HTTP/1.1\r\n");
            out.write("Lamport-Clock: " + lamportClock + "\r\n\r\n");
            out.flush();

            return readResponse(in);
        }
    }

    /**
     * Reads HTTP-like response from BufferedReader, parsing status line, headers, and body.
     *
     * @param in BufferedReader to read from socket input stream
     * @return HttpResponse object encapsulating status, headers, and body
     * @throws IOException on IO errors
     */
    private HttpResponse readResponse(BufferedReader in) throws IOException {
        String statusLine = in.readLine();
        if (statusLine == null) return null;

        // Extract status code and message from status line
        String[] parts = statusLine.split(" ", 3);
        String status = parts.length >= 3 ? parts[1] + " " + parts[2] : parts[1];

        Map<String, String> headers = new HashMap<>();
        String line;

        // Read headers until blank line
        while (!(line = in.readLine()).isEmpty()) {
            int idx = line.indexOf(":");
            if (idx > 0) {
                String key = line.substring(0, idx);
                String value = line.substring(idx + 1).trim();
                headers.put(key, value);
            }
        }

        // Read response body
        StringBuilder body = new StringBuilder();
        while ((line = in.readLine()) != null) {
            body.append(line);
        }

        return new HttpResponse(status, headers, body.toString());
    }

    /**
     * Helper class representing an HTTP-like response.
     */
    private static class HttpResponse {
        String status;
        Map<String, String> headers;
        String body;

        HttpResponse(String status, Map<String, String> headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }
    }
}

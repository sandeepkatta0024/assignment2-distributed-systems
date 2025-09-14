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

public class AggregationServerTest {

    private static final String TEST_FILE = "test_server_data.json";
    private static final String LOCALHOST = "localhost";
    private static final int TEST_PORT = 4567;

    private static ServerSocket serverSocket;
    private static ExecutorService serverExecutor;

    @BeforeClass
    public static void startServer() throws Exception {
        Files.deleteIfExists(Paths.get(TEST_FILE));
        AggregationServer.data.clear();
        startServerAsync();
        Thread.sleep(500);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        if (serverSocket != null) serverSocket.close();
        if (serverExecutor != null) serverExecutor.shutdownNow();
        Files.deleteIfExists(Paths.get(TEST_FILE));
    }

    @Test
    public void testPutAddsDataAndReturns201Or200() throws IOException {
        String json = "{\"id\":\"ID1\",\"temp\":25}";
        HttpResponse response = sendPut(json, 0);
        assertEquals("201 Created", response.status);
        assertTrue(response.headers.containsKey("Lamport-Clock"));

        response = sendPut(json, Integer.parseInt(response.headers.get("Lamport-Clock")));
        assertEquals("200 OK", response.status);
    }

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

    @Test
    public void testExpiryRemovesOldEntry() throws Exception {
        String json = "{\"id\":\"ID1\",\"temp\":25}";
        sendPut(json, 0);

        var dataField = AggregationServer.class.getDeclaredField("data");
        dataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, WeatherRecord> dataMap = (Map<String, WeatherRecord>) dataField.get(null);
        WeatherRecord record = dataMap.get("ID1");
        record.timestamp = System.currentTimeMillis() - 31000;

        AggregationServer.removeExpired();

        HttpResponse response = sendGet(1);
        assertEquals("404 Not Found", response.status);
    }

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

    private static void startServerAsync() throws IOException {
        serverSocket = new ServerSocket(TEST_PORT);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    AggregationServer.handleConnection(socket);
                }
            } catch (IOException ignored) {}
        });
    }

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

    private HttpResponse readResponse(BufferedReader in) throws IOException {
        String statusLine = in.readLine();
        if (statusLine == null) return null;
        String status = statusLine.split(" ", 3)[1] + " " + statusLine.split(" ", 3)[2];
        Map<String, String> headers = new HashMap<>();
        String line;
        while (!(line = in.readLine()).isEmpty()) {
            int idx = line.indexOf(":");
            if (idx > 0) headers.put(line.substring(0, idx), line.substring(idx + 1).trim());
        }
        StringBuilder body = new StringBuilder();
        while ((line = in.readLine()) != null) body.append(line);
        return new HttpResponse(status, headers, body.toString());
    }

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

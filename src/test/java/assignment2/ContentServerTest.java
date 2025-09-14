package assignment2;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * JUnit test class for ContentServer.
 * Uses a mock server to verify that ContentServer sends correct PUT requests with valid data,
 * and rejects input files missing required 'id' field.
 */
public class ContentServerTest {

    private static final int TEST_PORT = 9191;
    private static ServerSocket serverSocket;
    private static ExecutorService serverExecutor;

    // Sample valid weather data includes mandatory 'id' field and other data
    private static final String SAMPLE_VALID_DATA =
            "id:TestStation\n" +
                    "temp:22\n" +
                    "wind_dir:NW\n";

    // Sample invalid weather data missing mandatory 'id' field
    private static final String SAMPLE_INVALID_DATA =
            "name:NoIDStation\n" +
                    "temp:30\n";

    /**
     * Starts a simple mock server before all tests.
     * The server accepts client connections and checks for PUT requests,
     * responds with HTTP/1.1 201 Created and Lamport-Clock header.
     */
    @BeforeClass
    public static void startMockServer() throws IOException {
        serverSocket = new ServerSocket(TEST_PORT);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    try (Socket socket = serverSocket.accept()) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                        String line;
                        boolean hasPut = false;

                        // Read headers until empty line
                        while (!(line = in.readLine()).isEmpty()) {
                            if (line.startsWith("PUT ")) hasPut = true;
                        }
                        // Assert the request includes PUT method
                        assertTrue("Expected PUT request", hasPut);

                        // Write mock response with Lamport-Clock header
                        out.write("HTTP/1.1 201 Created\r\nLamport-Clock: 1\r\n\r\n");
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // Server socket closed or interrupted: ignore for test shutdown
            }
        });
    }

    /**
     * Stops the mock server and executor after all tests complete.
     */
    @AfterClass
    public static void stopMockServer() throws IOException {
        if (serverSocket != null) serverSocket.close();
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    /**
     * Test that ContentServer sends a PUT request successfully with valid weather data.
     * Checks console output to verify sending and server response logged correctly.
     */
    @Test
    public void testContentServerValidDataSendsPut() throws Exception {
        // Create temporary file with valid data for the test
        File tempFile = File.createTempFile("valid_weather_data", ".txt");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(SAMPLE_VALID_DATA);
        }

        // Capture System.out output during execution
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ContentServer.main(new String[] { "localhost:" + TEST_PORT, tempFile.getAbsolutePath() });
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        // Verify output contains sending confirmation and server acknowledgement
        assertTrue(output.contains("Sending PUT"));
        assertTrue(output.contains("HTTP/1.1 201 Created"));
    }

    /**
     * Test that ContentServer rejects files missing the mandatory 'id' key.
     * Verifies proper error message is printed to console.
     */
    @Test
    public void testContentServerRejectsMissingId() throws Exception {
        // Create temporary file with invalid data (no id)
        File tempFile = File.createTempFile("invalid_weather_data", ".txt");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(SAMPLE_INVALID_DATA);
        }

        // Capture System.out output during execution
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ContentServer.main(new String[] { "localhost:" + TEST_PORT, tempFile.getAbsolutePath() });
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        // Verify output contains expected error message about missing id
        assertTrue(output.contains("Data file must contain 'id' field."));
    }
}

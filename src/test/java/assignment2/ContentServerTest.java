package assignment2;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ContentServerTest {

    private static final int TEST_PORT = 9191;
    private static ServerSocket serverSocket;
    private static ExecutorService serverExecutor;

    private static final String SAMPLE_VALID_DATA =
            "id:TestStation\n" +
                    "temp:22\n" +
                    "wind_dir:NW\n";

    private static final String SAMPLE_INVALID_DATA =
            "name:NoIDStation\n" +
                    "temp:30\n";

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
                        while (!(line = in.readLine()).isEmpty()) {
                            if (line.startsWith("PUT ")) hasPut = true;
                        }
                        assertTrue("Expected PUT request", hasPut);

                        out.write("HTTP/1.1 201 Created\r\nLamport-Clock: 1\r\n\r\n");
                        out.flush();
                    }
                }
            } catch (IOException ignored) {}
        });
    }

    @AfterClass
    public static void stopMockServer() throws IOException {
        if (serverSocket != null) serverSocket.close();
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Test
    public void testContentServerValidDataSendsPut() throws Exception {
        File tempFile = File.createTempFile("valid_weather_data", ".txt");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(SAMPLE_VALID_DATA);
        }

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ContentServer.main(new String[] { "localhost:" + TEST_PORT, tempFile.getAbsolutePath() });
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertTrue(output.contains("Sending PUT"));
        assertTrue(output.contains("HTTP/1.1 201 Created"));
    }

    @Test
    public void testContentServerRejectsMissingId() throws Exception {
        File tempFile = File.createTempFile("invalid_weather_data", ".txt");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(SAMPLE_INVALID_DATA);
        }

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ContentServer.main(new String[] { "localhost:" + TEST_PORT, tempFile.getAbsolutePath() });
        } finally {
            System.setOut(originalOut);
        }

        String output = outContent.toString();
        assertTrue(output.contains("Data file must contain 'id' field."));
    }
}

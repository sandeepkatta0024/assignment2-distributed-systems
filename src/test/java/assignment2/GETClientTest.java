package assignment2;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class GETClientTest {

    private static final int TEST_PORT = 9393;
    private static ServerSocket serverSocket;
    private static ExecutorService serverExecutor;

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

                        String requestLine = in.readLine();
                        if (requestLine == null) return;

                        if (requestLine.startsWith("GET ")) {
                            // Consume headers
                            String line;
                            while (!(line = in.readLine()).isEmpty()) {}

                            // Return different responses based on URL path
                            if (requestLine.contains("/empty.json")) {
                                out.write("HTTP/1.1 404 Not Found\r\n\r\nNo weather data available.\r\n");
                            } else if (requestLine.contains("/error.json")) {
                                out.write("HTTP/1.1 500 Internal Server Error\r\n\r\nServer failed.\r\n");
                            } else {
                                out.write("HTTP/1.1 200 OK\r\n");
                                out.write("Lamport-Clock: 1\r\n");
                                out.write("Content-Type: application/json\r\n\r\n");
                                out.write("[{\"id\":\"TestStation\",\"temp\":\"20\"}]\r\n");
                            }
                            out.flush();
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        });
    }

    @AfterClass
    public static void stopMockServer() throws IOException {
        if (serverSocket != null) serverSocket.close();
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Test
    public void testGetClientEmptyResponse() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        try {
            GETClient.main(new String[] { "http://localhost:" + TEST_PORT + "/empty.json" });
        } finally {
            System.setOut(originalOut);
        }
        String output = outputStream.toString();
        assertTrue(output.contains("HTTP/1.1 404 Not Found"));
        assertTrue(output.contains("No weather data available"));
    }

    @Test
    public void testGetClientServerErrorResponse() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        try {
            GETClient.main(new String[] { "http://localhost:" + TEST_PORT + "/error.json" });
        } finally {
            System.setOut(originalOut);
        }
        String output = outputStream.toString();
        assertTrue(output.contains("HTTP/1.1 500 Internal Server Error"));
        assertTrue(output.contains("Server failed"));
    }

    @Test
    public void testGetClientSuccess() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        try {
            GETClient.main(new String[] { "localhost:" + TEST_PORT });
        } finally {
            System.setOut(originalOut);
        }
        String output = outputStream.toString();
        assertTrue(output.contains("HTTP/1.1 200 OK"));
        assertTrue(output.contains("id: TestStation"));
        assertTrue(output.contains("temp: 20"));
    }
}

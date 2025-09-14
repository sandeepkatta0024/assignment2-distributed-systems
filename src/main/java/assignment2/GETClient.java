package assignment2;

import java.io.*;
import java.net.*;
import com.google.gson.*;

public class GETClient {
    private static LamportClock clock = new LamportClock();

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

        Gson gson = new Gson();
        clock.tick();

        try(Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            out.write("GET " + path + " HTTP/1.1\r\n");
            out.write("Host: " + host + "\r\n");
            out.write("Lamport-Clock: " + clock.getTime() + "\r\n\r\n");
            out.flush();

            String status = in.readLine();
            if(status == null) return;
            System.out.println(status);

            String line;
            while((line = in.readLine()) != null && !line.trim().isEmpty()){
                if(line.startsWith("Lamport-Clock:")){
                    int servClock = Integer.parseInt(line.split(":")[1].trim());
                    clock.update(servClock);
                }
            }

            // Read body
            StringBuilder sb = new StringBuilder();
            while((line = in.readLine()) != null){
                sb.append(line);
            }

            if(sb.length() > 0){
                String body = sb.toString().trim();

                // Check if the body starts with '[' meaning a JSON array
                if(body.startsWith("[")){
                    JsonArray arr = gson.fromJson(body, JsonArray.class);
                    for(JsonElement el : arr){
                        JsonObject obj = el.getAsJsonObject();
                        for(String key : obj.keySet()){
                            System.out.println(key + ": " + obj.get(key).getAsString());
                        }
                        System.out.println();
                    }
                } else {
                    // Just print the plain text (error or 404)
                    System.out.println(body);
                }
            }
        }
    }
}

package assignment2;

import java.io.*;
import java.net.*;
import com.google.gson.*;

public class GETClient {
    private static LamportClock clock = new LamportClock();

    public static void main(String[] args) throws Exception {
        if(args.length != 1){
            System.out.println("Usage: java GETClient <host:port>");
            return;
        }
        String[] split = args[0].split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);

        Gson gson = new Gson();

        clock.tick();

        try(Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            out.write("GET /weather.json HTTP/1.1\r\n");
            out.write("Host: " + host + "\r\n");
            out.write("Lamport-Clock: " + clock.getTime() + "\r\n\r\n");
            out.flush();

            String status = in.readLine();
            if(status == null) return;
            System.out.println(status);

            String line;
            // Read headers and update Lamport clock
            while((line = in.readLine()) != null && !line.trim().isEmpty()){
                if(line.startsWith("Lamport-Clock:")){
                    int servClock = Integer.parseInt(line.split(":")[1].trim());
                    clock.update(servClock);
                }
            }

            // Read JSON body
            StringBuilder sb = new StringBuilder();
            while((line = in.readLine()) != null){
                sb.append(line);
            }

            if(sb.length() > 0){
                JsonArray arr = gson.fromJson(sb.toString(), JsonArray.class);
                for(JsonElement el : arr){
                    JsonObject obj = el.getAsJsonObject();
                    for(String key : obj.keySet()){
                        System.out.println(key + ": " + obj.get(key).getAsString());
                    }
                    System.out.println();
                }
            }
        }
    }
}

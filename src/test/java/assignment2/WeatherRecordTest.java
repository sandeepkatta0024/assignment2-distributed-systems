package assignment2;

import org.junit.Test;
import com.google.gson.*;
import static org.junit.Assert.*;

public class WeatherRecordTest {

    @Test
    public void testCreateWeatherRecord() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "ID123");
        obj.addProperty("temp", 25);

        WeatherRecord wr = new WeatherRecord(obj, 3);

        assertEquals(obj, wr.obj);
        assertEquals(3, wr.lamport);
        assertTrue(wr.timestamp > 0);
    }
}

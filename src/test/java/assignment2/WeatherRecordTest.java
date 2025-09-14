package assignment2;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class WeatherRecordTest {

    @Test
    public void testCreateWeatherRecord() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", "ID123");
        map.put("temp", "25");

        WeatherRecord wr = new WeatherRecord(map, 3);

        assertEquals(map, wr.getData());
        assertEquals(3, wr.lamport);
        assertTrue(wr.timestamp > 0);
    }
}

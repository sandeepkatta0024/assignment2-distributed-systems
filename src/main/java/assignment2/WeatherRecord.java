package assignment2;

import java.util.Map;

/**
 * Represents a weather data record with associated Lamport timestamp and last update time.
 */
public class WeatherRecord {
    // Weather data as key-value pairs (strings)
    private final Map<String, String> data;

    // Last update timestamp in milliseconds since epoch
    public long timestamp;

    // Lamport timestamp for this record
    public int lamport;

    /**
     * Constructs a WeatherRecord from data map and Lamport time.
     * Sets update timestamp to current time.
     */
    public WeatherRecord(Map<String, String> data, int lamport) {
        this.data = data;
        this.lamport = lamport;
        this.timestamp = System.currentTimeMillis();
    }


    /**
     * Returns the weather data map.
     */
    public Map<String, String> getData() {
        return data;
    }

}

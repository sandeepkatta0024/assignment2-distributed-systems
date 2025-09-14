# Assignment 2 Distributed Systems

## Overview

This project implements a distributed weather data aggregation system with the following components:

- **Aggregation Server**: Centrally collects, stores, and serves weather data from multiple content servers. Maintains Lamport clocks for ordering PUT and GET operations, persists data, and expires stale data after 30 seconds.

- **Content Server**: Reads local weather data from a file and sends updates (PUT requests) to the Aggregation Server. Uses Lamport clocks for event ordering and retries sending on connection failures.

- **GET Client**: Requests and displays aggregated weather data from the Aggregation Server via GET requests, updating Lamport clocks accordingly.

The system uses a **custom JSON parser** implemented from scratch for parsing and serializing weather data, fulfilling the bonus requirements.

## Project Structure

```
assignment2-distributed-systems/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── assignment2/
│   │   │       ├── AggregationServer.java
│   │   │       ├── ContentServer.java
│   │   │       ├── GETClient.java
│   │   │       ├── LamportClock.java
│   │   │       ├── WeatherRecord.java
│   │   │       └── SimpleJsonParser.java
│   │   └── resources/
│   │       └── weather_data.txt
│   └── test/
│       └── java/
│           └── assignment2/
│               ├── AggregationServerTest.java
│               ├── ContentServerTest.java
│               ├── GETClientTest.java
│               └── WeatherRecordTest.java
├── build.gradle
├── gradlew
└── README.md
```

## Features

- TCP socket communication with HTTP-like protocol for PUT and GET requests.
- Lamport clocks to maintain causal ordering of events.
- Thread-safe concurrent server supporting multiple clients.
- **Custom JSON parser and serializer for flat JSON objects instead of gson.**
- Automatic data expiry after 30 seconds of inactivity.
- Persistent storage of weather data to disk and crash recovery on start.
- ContentServer retry logic upon connection failure.
- Comprehensive automated JUnit tests for all components.

## Requirements

- Java 17 or later
- Gradle build system

## Building the Project

From the root directory, run:

```bash
./gradlew clean build
```

## Running the Components

### Aggregation Server

Start the Aggregation Server on port 4567 (or specify another port):

```bash
./gradlew run -PmainClass=assignment2.AggregationServer --args="4567"
```

### Content Server

Send weather data from `weather_data.txt` to the Aggregation Server:

```bash
./gradlew run -PmainClass=assignment2.ContentServer --args="localhost:4567 src/main/resources/weather_data.txt"
```

### GET Client

Retrieve and display weather data from the Aggregation Server:

```bash
./gradlew run -PmainClass=assignment2.GETClient --args="localhost:4567"
```

## Automated Testing

Run all automated tests:

```bash
./gradlew test
```
if any issues were found with address already in use error, please stop the aggregationserver and then try again
Test reports will be generated at:

```
build/reports/tests/test/index.html
```

## Design Overview

- **Aggregation Server** manages storage and expiry of data, responds to HTTP-like PUT/GET requests over sockets.
- **Content Server** converts flat text weather data to JSON, PUTs updates to the server, handling Lamport clocks and retrying on failures.
- **GET Client** fetches and parses JSON aggregated data from the server, prints key-value pairs for each weather station.
- **SimpleJsonParser** is a minimal parser and serializer for flat JSON objects implemented without external libraries.

## Notes

- `weather_data.txt` is located under `src/main/resources/` directory and follows `key: value` format, must include an `id` field.
- Use the full package name `assignment2.ContentServer` etc., when running with Gradle.

## Author

Sandeep Katta
Student id -  a1990024
***
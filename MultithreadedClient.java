//second
package client;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MultithreadedClient {
    private static final int NUM_THREADS = 32;
    private static final int NUM_REQUESTS_PER_THREAD = 3;
    private static final int TOTAL_REQUESTS = 20;
    private static final String SERVER_URL = "http://168.138.73.201:8080";
    private static final BlockingQueue<SkierLiftRideEventGenerator.SkierLiftRideEvent> eventQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
    private static final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();

    private static int successfulRequests = 0;
    private static int unsuccessfulRequests = 0;
    private static final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
    private static final List<Integer> responseCodes = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis(); // Record the start time

        // Start event generation thread
        eventExecutor.execute(new EventGenerationTask());

        // Start initial threads for 1000 POST requests each
        for (int i = 0; i < NUM_THREADS; ++i) {
            executor.execute(new APIClientTask(NUM_REQUESTS_PER_THREAD));
        }

        executor.shutdown();

        while (!executor.isTerminated()) {
            // Wait for all threads to terminate
        }

        long endTime = System.currentTimeMillis(); // Record the end time

        // Print statistics
        System.out.println("Number of successful requests sent: " + successfulRequests);
        System.out.println("Number of unsuccessful requests: " + unsuccessfulRequests);
        System.out.println("Total run time: " + (endTime - startTime) + " milliseconds");

        double throughput = (double) (successfulRequests + unsuccessfulRequests) / ((endTime - startTime) / 1000.0); // requests per second
        System.out.println("Total throughput: " + throughput + " requests per second");

        calculateStatistics();
    }


    static class APIClientTask implements Runnable {
        private final int numRequests;
        private final int MAX_RETRIES = 5;

        public APIClientTask(int numRequests) {
            this.numRequests = numRequests;
        }

        public void run() {
            for (int i = 0; i < numRequests; ++i) {
                try {
                    long startTime = System.currentTimeMillis(); // Record start time
                    SkierLiftRideEventGenerator.SkierLiftRideEvent event = eventQueue.take();
                    ObjectMapper objectMapper = new ObjectMapper();
                    String json = objectMapper.writeValueAsString(event);
                    HttpClient client = HttpClient.newHttpClient();

                    int retryCount = 0;
                    HttpResponse<String> response = null;

                    while (retryCount < MAX_RETRIES) {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(new URI(SERVER_URL + "/skiers"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();

                        response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        // Check if response code is in the range of 4XX or 5XX
                        if (response.statusCode() >= 400 && response.statusCode() < 600) {
                            retryCount++;
                        } else {
                            break; // Exit loop if response code is not in 4XX or 5XX range
                        }
                    }

                    long endTime = System.currentTimeMillis(); // Record end time
                    long latency = endTime - startTime; // Calculate latency

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        successfulRequests++;
                    } else {
                        unsuccessfulRequests++;
                    }

                    latencies.add(latency); // Store latency
                    responseCodes.add(response.statusCode()); // Store response code

                    System.out.println("Thread: " + Thread.currentThread().getId() +
                            " - Response code: " + response.statusCode() +
                            " - Response body: " + response.body() +
                            " - Latency: " + latency + " milliseconds");
                } catch (IOException | URISyntaxException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class EventGenerationTask implements Runnable {
        private final SkierLiftRideEventGenerator generator = new SkierLiftRideEventGenerator();

        EventGenerationTask() {
        }

        public void run() {
            try {
                for (int i = 0; i < TOTAL_REQUESTS; i++) {
                    SkierLiftRideEventGenerator.SkierLiftRideEvent event = this.generator.generateEvent();
                    eventQueue.put(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void calculateStatistics() {
        // Calculate mean response time
        double meanResponseTime = latencies.stream().mapToLong(Long::valueOf).average().orElse(0.0);
        System.out.println("Mean response time: " + meanResponseTime + " milliseconds");

        // Calculate median response time
        Collections.sort(latencies);
        double medianResponseTime;
        if (latencies.size() % 2 == 0) {
            medianResponseTime = (latencies.get(latencies.size() / 2) + latencies.get(latencies.size() / 2 - 1)) / 2.0;
        } else {
            medianResponseTime = latencies.get(latencies.size() / 2);
        }
        System.out.println("Median response time: " + medianResponseTime + " milliseconds");

        // Calculate p99 response time
        int p99Index = (int) Math.ceil(latencies.size() * 0.99);
        double p99ResponseTime = latencies.get(p99Index);
        System.out.println("p99 response time: " + p99ResponseTime + " milliseconds");

        // Calculate min and max response times
        long minResponseTime = latencies.isEmpty() ? 0 : Collections.min(latencies);
        long maxResponseTime = latencies.isEmpty() ? 0 : Collections.max(latencies);
        System.out.println("Minimum response time: " + minResponseTime + " milliseconds");
        System.out.println("Maximum response time: " + maxResponseTime + " milliseconds");

        // Write CSV file
        try (FileWriter writer = new FileWriter("response_times.csv")) {
            writer.write("Start Time, Request Type, Latency, Response Code\n");
            for (int i = 0; i < latencies.size(); i++) {
                long startTime = System.currentTimeMillis() - latencies.get(i); // Calculate start time
                long latency = latencies.get(i); // Get latency
                int responseCode = responseCodes.get(i); // Get response code
                writer.write(startTime + ", POST, " + latency + ", " + responseCode + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

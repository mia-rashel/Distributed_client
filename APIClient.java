package client;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class APIClient {

    private static final String SERVER_URL = "http://localhost:8080"; // Replace with your server URL

    public static void main(String[] args) {
        try {
            testAPIConnection();
        } catch (IOException | InterruptedException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private static void testAPIConnection() throws IOException, InterruptedException, URISyntaxException {
        // Construct JSON data to send in the request body
        String json = "{\"skierId\":1,\"resortId\":1,\"liftId\":1,\"seasonId\":2022,\"dayId\":1,\"time\":360}";

        // Build the request with the JSON payload
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(SERVER_URL + "/skiers"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        // Send the request and handle the response
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response code: " + response.statusCode());
        System.out.println("Response body: " + response.body());
    }
}

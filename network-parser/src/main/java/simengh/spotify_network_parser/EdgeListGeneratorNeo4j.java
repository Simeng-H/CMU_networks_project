package simengh.spotify_network_parser;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.tongfei.progressbar.ProgressBar;
import org.neo4j.driver.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class EdgeListGeneratorNeo4j {
    private static final int BATCH_SIZE = 1000; // Adjust based on performance testing

    public static void main(String[] args) throws IOException {
        String dataDir = "/Users/simeng/local_dev/CMU_networks_project/dataset/data";

        // Get all JSON files in the data directory
        List<String> inputFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dataDir))) {
            for (Path path : stream) {
                if (path.toString().endsWith(".json")) {
                    inputFiles.add(path.toString());
                }
            }
        }

        // Initialize the Neo4j driver
        Driver driver = GraphDatabase.driver(
                "bolt://localhost:7687",
                AuthTokens.basic("neo4j", "password") // Replace with your Neo4j username and password
        );

        try (Session session = driver.session()) {
            // Drop previous data
            clearDatabase(session);

            // Create indexes and constraints for optimization
            createIndexesAndConstraints(session);
        }

        // Initialize the progress bar
        try (ProgressBar pb = new ProgressBar("Processing Files", inputFiles.size())) {
            for (String inputFile : inputFiles) {
                try (Session session = driver.session()) {
                    processFile(inputFile, session);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                pb.step(); // Update the progress bar
            }
        } finally {
            driver.close(); // Close the driver connection
        }

        System.out.println("Processing complete.");
    }

    private static void clearDatabase(Session session) {
        session.run("MATCH (n) DETACH DELETE n");
    }

    private static void createIndexesAndConstraints(Session session) {
        // Create an index on :Track(uri)
        session.run("CREATE INDEX IF NOT EXISTS FOR (t:Track) ON (t.uri)");

        // Optional: Create a uniqueness constraint to prevent duplicate relationships
        // This ensures that only one relationship exists between any two tracks
        // session.run("CREATE CONSTRAINT IF NOT EXISTS ON ()-[r:CO_OCCURS_WITH]-() ASSERT exists(r)");
    }

    private static void processFile(String inputFile, Session session) throws IOException {
        Gson gson = new Gson();
        try (JsonReader reader = new JsonReader(new FileReader(inputFile))) {
            reader.beginObject(); // Start reading the JSON object

            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("playlists".equals(name)) {
                    reader.beginArray(); // Start reading the playlists array
                    while (reader.hasNext()) {
                        JsonObject playlistObject = gson.fromJson(reader, JsonObject.class);
                        processPlaylist(playlistObject, session);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue(); // Skip other fields (e.g., "info")
                }
            }
            reader.endObject();
        }
    }

    private static void processPlaylist(JsonObject playlistObject, Session session) {
        JsonArray tracksArray = playlistObject.getAsJsonArray("tracks");
        List<String> trackUris = new ArrayList<>();

        for (JsonElement trackElement : tracksArray) {
            JsonObject trackObject = trackElement.getAsJsonObject();
            String trackUri = trackObject.get("track_uri").getAsString();
            trackUris.add(trackUri);
        }

        createNodesAndRelationships(trackUris, session);
    }

    private static void createNodesAndRelationships(List<String> trackUris, Session session) {
        // Remove duplicate track URIs
        Set<String> uniqueTrackUris = new HashSet<>(trackUris);

        // Batch create nodes
        List<Map<String, Object>> nodeParamsList = new ArrayList<>();
        for (String trackUri : uniqueTrackUris) {
            Map<String, Object> params = new HashMap<>();
            params.put("uri", trackUri);
            nodeParamsList.add(params);

            if (nodeParamsList.size() >= BATCH_SIZE) {
                createNodes(session, nodeParamsList);
                nodeParamsList.clear();
            }
        }
        // Create any remaining nodes
        if (!nodeParamsList.isEmpty()) {
            createNodes(session, nodeParamsList);
        }

        // Batch create relationships
        List<Map<String, Object>> relParamsList = new ArrayList<>();
        for (int i = 0; i < trackUris.size(); i++) {
            for (int j = i + 1; j < trackUris.size(); j++) {
                Map<String, Object> params = new HashMap<>();
                params.put("uri1", trackUris.get(i));
                params.put("uri2", trackUris.get(j));
                relParamsList.add(params);

                if (relParamsList.size() >= BATCH_SIZE) {
                    createRelationships(session, relParamsList);
                    relParamsList.clear();
                }
            }
        }
        // Create any remaining relationships
        if (!relParamsList.isEmpty()) {
            createRelationships(session, relParamsList);
        }
    }

    private static void createNodes(Session session, List<Map<String, Object>> nodeParamsList) {
        session.executeWrite(tx -> {
            String query = "UNWIND $nodes AS node " +
                    "MERGE (:Track {uri: node.uri})";
            tx.run(query, Collections.singletonMap("nodes", nodeParamsList));
            return null;
        });
    }

    private static void createRelationships(Session session, List<Map<String, Object>> relParamsList) {
        session.executeWrite(tx -> {
            String query = "UNWIND $rels AS rel " +
                    "MATCH (t1:Track {uri: rel.uri1}), (t2:Track {uri: rel.uri2}) " +
                    "MERGE (t1)-[:CO_OCCURS_WITH]->(t2)";
            tx.run(query, Collections.singletonMap("rels", relParamsList));
            return null;
        });
    }
}
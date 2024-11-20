package simengh.spotify_network_parser;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class EdgeListGenerator {
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
        String edgeListFile = "edge_list.txt";

        // only use first 2 files
        inputFiles = inputFiles.subList(0, 2);

        // Initialize progress tracking variables
        int totalFiles = inputFiles.size();
        int filesProcessed = 0;

        // Process each input file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(edgeListFile))) {
            for (String inputFile : inputFiles) {
                processFile(inputFile, writer);

                // Update progress
                filesProcessed++;
                printProgressBar(filesProcessed, totalFiles);
            }
        }
        System.out.println("\nProcessing complete.");
    }

    private static void processFile(String inputFile, BufferedWriter writer) throws IOException {
        Gson gson = new Gson();
        try (JsonReader reader = new JsonReader(new FileReader(inputFile))) {
            reader.beginObject(); // Start reading the JSON object

            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("playlists".equals(name)) {
                    reader.beginArray(); // Start reading the playlists array
                    while (reader.hasNext()) {
                        JsonObject playlistObject = gson.fromJson(reader, JsonObject.class);
                        processPlaylist(playlistObject, writer);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue(); // Skip other fields (e.g., "info")
                }
            }
            reader.endObject();
        }
    }

    private static void processPlaylist(JsonObject playlistObject, BufferedWriter writer) throws IOException {
        JsonArray tracksArray = playlistObject.getAsJsonArray("tracks");
        List<String> trackUris = new ArrayList<>();

        for (JsonElement trackElement : tracksArray) {
            JsonObject trackObject = trackElement.getAsJsonObject();
            String trackUri = trackObject.get("track_uri").getAsString();
            trackUris.add(trackUri);
        }

        // Generate unique track pairs and write to edge list
        for (int i = 0; i < trackUris.size(); i++) {
            for (int j = i + 1; j < trackUris.size(); j++) {
                writer.write(trackUris.get(i) + "\t" + trackUris.get(j));
                writer.newLine();
            }
        }
    }

    // Simple console progress bar
    private static void printProgressBar(int current, int total) {
        int barLength = 50;
        double percent = (double) current / total;
        int filledLength = (int) (barLength * percent);

        StringBuilder bar = new StringBuilder();
        bar.append("\r[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("#");
            } else {
                bar.append("-");
            }
        }
        bar.append("] ");
        bar.append(String.format("%.2f", percent * 100));
        bar.append("%");

        System.out.print(bar.toString());
    }
}
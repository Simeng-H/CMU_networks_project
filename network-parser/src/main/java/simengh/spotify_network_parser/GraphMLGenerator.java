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
import java.util.*;

public class GraphMLGenerator {
    enum NodeType {
        TRACK, ARTIST
    }

    public static void main(String[] args) throws IOException {
        String dataDir = "/Users/simeng/local_dev/CMU_networks_project/dataset/data";

        // Set the node type here: TRACK or ARTIST
        NodeType nodeType = NodeType.ARTIST; // or NodeType.ARTIST
        // NodeType nodeType = NodeType.TRACK; // or NodeType.ARTIST

        // Get all JSON files in the data directory
        List<String> inputFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dataDir))) {
            for (Path path : stream) {
                if (path.toString().endsWith(".json")) {
                    inputFiles.add(path.toString());
                }
            }
        }

        // Only use first 2 files for testing purposes
        inputFiles = inputFiles.subList(0, 2);

        // Initialize progress tracking variables
        int totalFiles = inputFiles.size();
        int filesProcessed = 0;

        // Initialize node and edge collections
        Set<String> nodes = new HashSet<>();
        Set<Edge> edges = new HashSet<>();

        // Process each input file
        for (String inputFile : inputFiles) {
            processFile(inputFile, nodes, edges, nodeType);

            // Update progress
            filesProcessed++;
            printProgressBar(filesProcessed, totalFiles);
        }

        // Output graph to GraphML
        String graphmlFile;
        if (nodeType == NodeType.TRACK) {
            graphmlFile = "track_graph.graphml";
        } else {
            graphmlFile = "artist_graph.graphml";
        }
        writeGraphML(graphmlFile, nodes, edges);

        // Print preliminary results
        System.out.println("\nNumber of nodes: " + nodes.size());
        System.out.println("Number of edges: " + edges.size());
        System.out.println("\nProcessing complete.");
    }

    private static void processFile(String inputFile, Set<String> nodes, Set<Edge> edges, NodeType nodeType) throws IOException {
        Gson gson = new Gson();
        try (JsonReader reader = new JsonReader(new FileReader(inputFile))) {
            reader.beginObject(); // Start reading the JSON object

            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("playlists".equals(name)) {
                    reader.beginArray(); // Start reading the playlists array
                    while (reader.hasNext()) {
                        JsonObject playlistObject = gson.fromJson(reader, JsonObject.class);
                        processPlaylist(playlistObject, nodes, edges, nodeType);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue(); // Skip other fields (e.g., "info")
                }
            }
            reader.endObject();
        }
    }

    private static void processPlaylist(JsonObject playlistObject, Set<String> nodes, Set<Edge> edges, NodeType nodeType) throws IOException {
        JsonArray tracksArray = playlistObject.getAsJsonArray("tracks");
        List<String> nodeIds = new ArrayList<>();

        for (JsonElement trackElement : tracksArray) {
            JsonObject trackObject = trackElement.getAsJsonObject();
            String nodeId;
            if (nodeType == NodeType.TRACK) {
                nodeId = trackObject.get("track_uri").getAsString();
            } else { // NodeType.ARTIST
                nodeId = trackObject.get("artist_uri").getAsString();
            }
            nodeIds.add(nodeId);
            nodes.add(nodeId);
        }

        // Generate unique node pairs and add to edges
        for (int i = 0; i < nodeIds.size(); i++) {
            for (int j = i + 1; j < nodeIds.size(); j++) {
                Edge edge = new Edge(nodeIds.get(i), nodeIds.get(j));
                edges.add(edge);
            }
        }
    }

    // Edge class with equals and hashCode to avoid duplicates
    static class Edge {
        String node1;
        String node2;

        Edge(String node1, String node2) {
            // Ensure consistent ordering for undirected edges
            if (node1.compareTo(node2) <= 0) {
                this.node1 = node1;
                this.node2 = node2;
            } else {
                this.node1 = node2;
                this.node2 = node1;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Edge)) return false;
            Edge edge = (Edge) o;
            return node1.equals(edge.node1) && node2.equals(edge.node2);
        }

        @Override
        public int hashCode() {
            return node1.hashCode() + node2.hashCode();
        }
    }

    // Method to write GraphML
    private static void writeGraphML(String graphmlFile, Set<String> nodes, Set<Edge> edges) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(graphmlFile))) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n");
            writer.write("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            writer.write("    xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n");
            writer.write("    http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");
            writer.write("  <graph id=\"G\" edgedefault=\"undirected\">\n");

            // Write nodes
            for (String nodeId : nodes) {
                writer.write("    <node id=\"" + escapeXML(nodeId) + "\"/>\n");
            }

            // Write edges
            int edgeId = 0;
            for (Edge edge : edges) {
                writer.write("    <edge id=\"e" + edgeId++ + "\" source=\"" + escapeXML(edge.node1) + "\" target=\"" + escapeXML(edge.node2) + "\"/>\n");
            }

            writer.write("  </graph>\n");
            writer.write("</graphml>\n");
        }
    }

    // Helper method to escape XML special characters
    private static String escapeXML(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&apos;")
                .replace("<", "&lt;").replace(">", "&gt;");
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
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.cli.*;
import me.tongfei.progressbar.*;

public class GraphMLGenerator {
    enum NodeType {
        TRACK, ARTIST
    }

    // Default values
    private static final String DEFAULT_DATA_DIR = "/Users/simeng/local_dev/CMU_networks_project/dataset/data";
    private static final int DEFAULT_START_INDEX = 0;
    private static final int DEFAULT_END_INDEX = 1;  // Will process 2 files by default
    private static final String DEFAULT_OUTPUT_PATH = "artist_graph.graphml";
    private static final NodeType DEFAULT_NODE_TYPE = NodeType.ARTIST;

    public static void main(String[] args) throws IOException {
        // Create command line options
        Options options = new Options();
        options.addOption(Option.builder("d")
                .longOpt("directory")
                .desc("Input directory containing JSON files")
                .hasArg()
                .build());
        options.addOption(Option.builder("s")
                .longOpt("start")
                .desc("Start index of files to process")
                .hasArg()
                .type(Number.class)
                .build());
        options.addOption(Option.builder("e")
                .longOpt("end")
                .desc("End index of files to process")
                .hasArg()
                .type(Number.class)
                .build());
        options.addOption(Option.builder("o")
                .longOpt("output")
                .desc("Output path for GraphML file")
                .hasArg()
                .build());
        options.addOption(Option.builder("t")
                .longOpt("type")
                .desc("Node type (TRACK or ARTIST)")
                .hasArg()
                .build());
        options.addOption("h", "help", false, "Print this help message");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                formatter.printHelp("GraphMLGenerator", options);
                System.exit(0);
            }

            // Parse command line arguments with defaults
            String dataDir = cmd.getOptionValue("directory", DEFAULT_DATA_DIR);
            int startIndex = Integer.parseInt(cmd.getOptionValue("start", String.valueOf(DEFAULT_START_INDEX)));
            int endIndex = Integer.parseInt(cmd.getOptionValue("end", String.valueOf(DEFAULT_END_INDEX)));
            String outputPath = cmd.getOptionValue("output", DEFAULT_OUTPUT_PATH);
            NodeType nodeType = cmd.hasOption("type") ? 
                NodeType.valueOf(cmd.getOptionValue("type").toUpperCase()) : 
                DEFAULT_NODE_TYPE;

            // Print configuration
            System.out.println("Configuration:");
            System.out.println("Input directory: " + dataDir);
            System.out.println("File index range: " + startIndex + " to " + endIndex);
            System.out.println("Output path: " + outputPath);
            System.out.println("Node type: " + nodeType);
            System.out.println();

            // Validate input directory
            Path dataDirPath = Paths.get(dataDir);
            if (!Files.exists(dataDirPath) || !Files.isDirectory(dataDirPath)) {
                System.err.println("Error: Input directory does not exist or is not a directory: " + dataDir);
                System.exit(1);
            }

            // Get all JSON files in the data directory
            List<String> inputFiles;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirPath)) {
                inputFiles = StreamSupport.stream(stream.spliterator(), false)
                        .filter(path -> path.toString().endsWith(".json"))
                        .map(Path::toString)
                        .sorted()
                        .collect(Collectors.toList());
            }

            // Validate indices
            if (startIndex < 0 || endIndex >= inputFiles.size() || startIndex > endIndex) {
                System.err.println("Error: Invalid file indices. Valid range is 0 to " + (inputFiles.size() - 1));
                System.exit(1);
            }

            // Select files based on indices
            inputFiles = inputFiles.subList(startIndex, endIndex + 1);

            // Initialize progress tracking variables
            int totalFiles = inputFiles.size();

            // Initialize node and edge collections
            Set<String> nodes = new HashSet<>();
            Map<Edge, Edge> edgeMap = new HashMap<>();  // Using map for O(1) lookup

            // Create file progress bar
            try (ProgressBar fileProgress = createFileProgressBar(totalFiles)) {
                // Process each input file
                for (String inputFile : inputFiles) {
                    processFile(inputFile, nodes, edgeMap, nodeType);
                    fileProgress.step();
                }
            }

            // Output graph to GraphML
            writeGraphML(outputPath, nodes, edgeMap.values());

            // Print preliminary results
            System.out.println("\nNumber of nodes: " + nodes.size());
            System.out.println("Number of edges: " + edgeMap.size());
            System.out.println("Processing complete. Output written to: " + outputPath);

            // Add this method to calculate statistics
            printEdgeStatistics(edgeMap.values());
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            formatter.printHelp("GraphMLGenerator", options);
            System.exit(1);
        }
    }

    private static void processFile(String inputFile, Set<String> nodes, Map<Edge, Edge> edgeMap, NodeType nodeType) throws IOException {
        Gson gson = new Gson();
        int totalPlaylists = 0;
        
        // First pass to get playlist count from slice info
        try (JsonReader reader = new JsonReader(new FileReader(inputFile))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("info".equals(name)) {
                    JsonObject info = gson.fromJson(reader, JsonObject.class);
                    String slice = info.get("slice").getAsString();
                    String[] range = slice.split("-");
                    totalPlaylists = Integer.parseInt(range[1]) - Integer.parseInt(range[0]) + 1;
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }

        // Second pass to process playlists with progress bar
        try (JsonReader reader = new JsonReader(new FileReader(inputFile));
             ProgressBar pb = new ProgressBar("Processing " + Paths.get(inputFile).getFileName(), totalPlaylists)) {
            
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("playlists".equals(name)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        JsonObject playlistObject = gson.fromJson(reader, JsonObject.class);
                        processPlaylist(playlistObject, nodes, edgeMap, nodeType);
                        pb.step();
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
    }

    private static void processPlaylist(JsonObject playlistObject, Set<String> nodes, Map<Edge, Edge> edgeMap, NodeType nodeType) throws IOException {
        JsonArray tracksArray = playlistObject.getAsJsonArray("tracks");
        List<String> nodeIds = new ArrayList<>();

        // Pre-allocate ArrayList capacity
        int trackCount = tracksArray.size();
        nodeIds = new ArrayList<>(trackCount);

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

        // Generate unique node pairs and add to edges with weights
        for (int i = 0; i < nodeIds.size(); i++) {
            for (int j = i + 1; j < nodeIds.size(); j++) {
                String node1 = nodeIds.get(i);
                String node2 = nodeIds.get(j);
                
                // Skip self-edges
                if (node1.equals(node2)) {
                    continue;
                }
                
                Edge newEdge = new Edge(node1, node2);
                Edge existingEdge = edgeMap.get(newEdge);
                
                if (existingEdge != null) {
                    existingEdge.incrementWeight();
                } else {
                    edgeMap.put(newEdge, newEdge);
                }
            }
        }
    }

    // Modify Edge class to be used as HashMap key
    static class Edge {
        String node1;
        String node2;
        int weight;

        Edge(String node1, String node2) {
            // Ensure consistent ordering for undirected edges
            if (node1.compareTo(node2) <= 0) {
                this.node1 = node1;
                this.node2 = node2;
            } else {
                this.node1 = node2;
                this.node2 = node1;
            }
            this.weight = 1;
        }

        public void incrementWeight() {
            this.weight++;
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
            return Objects.hash(node1, node2);
        }
    }

    // Modify writeGraphML method to include weight attribute
    private static void writeGraphML(String graphmlFile, Set<String> nodes, Collection<Edge> edges) throws IOException {
        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(graphmlFile);
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(graphmlFile))) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n");
            writer.write("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            writer.write("    xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n");
            writer.write("    http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");
            
            // Add weight attribute definition
            writer.write("  <key id=\"weight\" for=\"edge\" attr.name=\"weight\" attr.type=\"int\"/>\n");
            
            writer.write("  <graph id=\"G\" edgedefault=\"undirected\">\n");

            // Write nodes
            for (String nodeId : nodes) {
                writer.write("    <node id=\"" + escapeXML(nodeId) + "\"/>\n");
            }

            // Write edges with weights
            int edgeId = 0;
            for (Edge edge : edges) {
                writer.write("    <edge id=\"e" + edgeId++ + "\" source=\"" + escapeXML(edge.node1) + 
                           "\" target=\"" + escapeXML(edge.node2) + "\">\n");
                writer.write("      <data key=\"weight\">" + edge.weight + "</data>\n");
                writer.write("    </edge>\n");
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

    // Replace the old printProgressBar method with a new method for file progress
    private static ProgressBar createFileProgressBar(int totalFiles) {
        return new ProgressBarBuilder()
                .setTaskName("Processing files")
                .setInitialMax(totalFiles)
                .setUpdateIntervalMillis(1000)
                .setStyle(ProgressBarStyle.ASCII)
                .build();
    }

    // Add this method to calculate statistics
    private static void printEdgeStatistics(Collection<Edge> edges) {
        if (edges.isEmpty()) {
            System.out.println("No edges to analyze");
            return;
        }

        // Get all weights
        List<Integer> weights = edges.stream()
                .map(e -> e.weight)
                .sorted()
                .collect(Collectors.toList());

        // Find top 10 edges
        List<Edge> topEdges = edges.stream()
                .sorted(Comparator.comparingInt((Edge e) -> e.weight).reversed())
                .limit(10)
                .collect(Collectors.toList());

        int min = weights.get(0);
        int max = weights.get(weights.size() - 1);
        double avg = weights.stream().mapToInt(Integer::intValue).average().getAsDouble();
        int median = weights.get(weights.size() / 2);

        System.out.println("\nEdge Weight Statistics:");
        System.out.println("Minimum weight: " + min);
        System.out.println("Maximum weight: " + max);
        System.out.println("Average weight: " + String.format("%.2f", avg));
        System.out.println("Median weight: " + median);
        
        System.out.println("\nTop 10 Strongest Connections:");
        for (int i = 0; i < topEdges.size(); i++) {
            Edge edge = topEdges.get(i);
            System.out.println(String.format("%d. Weight: %d", i + 1, edge.weight));
            System.out.println("   Node 1: " + edge.node1);
            System.out.println("   Node 2: " + edge.node2);
        }
    }
}
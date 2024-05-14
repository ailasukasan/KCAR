import javafx.util.Pair;

import java.io.*;
import java.util.*;

class APINode {
    String name;
    String primaryCategory;
    List<String> secondaryCategories;
    Map<APINode, Double> relatedAPIs;

    public APINode(String name, String primaryCategory, List<String> secondaryCategories) {
        this.name = name;
        this.primaryCategory = primaryCategory;
        this.secondaryCategories = new ArrayList<>(secondaryCategories);
        this.relatedAPIs = new HashMap<>();
    }

    public void addOrUpdateRelatedAPI(APINode apiNode, boolean bidirectional) {
        this.relatedAPIs.put(apiNode, this.relatedAPIs.getOrDefault(apiNode, 0.0) + 1);
        if (bidirectional) {
            apiNode.relatedAPIs.put(this, apiNode.relatedAPIs.getOrDefault(this, 0.0) + 1);
        }
    }

    public void applyExponentialWeightTransformation() {
        for (Map.Entry<APINode, Double> entry : relatedAPIs.entrySet()) {
            Double transformedWeight = Math.exp(-entry.getValue());
            entry.setValue(transformedWeight);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        APINode apiNode = (APINode) obj;
        return Objects.equals(name, apiNode.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

class APIGraph {
    List<APINode> apiNodes2;
    Map<String, APINode> apiNodes;

    public APIGraph() {
        this.apiNodes2 = new ArrayList<>();
        this.apiNodes = new HashMap<>();
    }

    public void addAPINodes(String apiCsvFilePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(apiCsvFilePath))) {
            String line = br.readLine();
            if (line != null && line.contains("Name")) {
                while ((line = br.readLine()) != null) {
                    String[] values = line.split("\",\"");
                    String apiName = values[0].replace("\"", "");
                    String primaryCategory = values[1].replace("\"", "");
                    String[] secondaryCategoryArray = values[2].replace("\"", "").split(",");
                    List<String> secondaryCategories = new ArrayList<>(Arrays.asList(secondaryCategoryArray));
                    secondaryCategories.removeAll(Collections.singleton(""));

                    APINode apiNode = new APINode(apiName, primaryCategory, secondaryCategories);
                    apiNodes2.add(apiNode);
                    apiNodes.put(apiName, apiNode);
                }
            }
        }
    }

    public void buildGraphFromMashups(String mashupCsvFilePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(mashupCsvFilePath))) {
            String line = br.readLine();
            Map<String, Set<String>> mashupToApis = new HashMap<>();

            while ((line = br.readLine()) != null) {
                String[] values = line.split("\",\"");
                if (values[0].startsWith("\"")) {
                    values[0] = values[0].substring(1);
                }
                if (values[values.length - 1].endsWith("\"")) {
                    values[values.length - 1] = values[values.length - 1].substring(0, values[values.length - 1].length() - 1);
                }

                String mashupName = values[0];
                String apiName = values[1];

                mashupToApis.computeIfAbsent(mashupName, k -> new HashSet<>()).add(apiName);
            }

            for (String mashup : mashupToApis.keySet()) {
                Set<String> apisInMashup = mashupToApis.get(mashup);
                List<String> apisInMashupList = new ArrayList<>(apisInMashup);
                for (int i = 0; i < apisInMashupList.size(); i++) {
                    for (int j = i + 1; j < apisInMashupList.size(); j++) {
                        String apiName1 = apisInMashupList.get(i);
                        String apiName2 = apisInMashupList.get(j);
                        if (apiNodes.containsKey(apiName1) && apiNodes.containsKey(apiName2)) {
                            APINode apiNode1 = apiNodes.get(apiName1);
                            APINode apiNode2 = apiNodes.get(apiName2);
                            apiNode1.addOrUpdateRelatedAPI(apiNode2, true);
                        }
                    }
                }
            }
        }
    }

    public void applyExponentialTransformationToAllNodes() {
        for (APINode node : apiNodes.values()) {
            node.applyExponentialWeightTransformation();
        }
    }
}

class SteinerTree {
    APINode root;
    Set<String> coveredKeywords;
    double weight;
    Set<APINode> nodes;

    public SteinerTree(APINode root, Set<String> coveredKeywords, double weight) {
        this.root = root;
        this.coveredKeywords = coveredKeywords;
        this.weight = weight;
        this.nodes = new HashSet<>();
        this.nodes.add(root);
    }
}

class DynamicProgrammingSteinerTree {
    private final APIGraph graph;
    private Set<String> allKeywords;
    private Map<Pair<Integer, Integer>, SteinerTree> dp = new HashMap<>();
    private Comparator<Pair<Double, Integer>> pairComparator = Comparator.comparingDouble(Pair::getKey);
    private PriorityQueue<Pair<Double, Integer>> noi = new PriorityQueue<>(pairComparator);
    private double INF = 1e9;

    public DynamicProgrammingSteinerTree(APIGraph graph, Set<String> allKeywords) {
        this.graph = graph;
        this.allKeywords = allKeywords;
    }

    public List<Integer> searchNode(String keyword) {
        List<Integer> s = new ArrayList<>();
        for (int i = 0; i < graph.apiNodes2.size(); i++) {
            if (graph.apiNodes2.get(i).secondaryCategories.contains(keyword) || graph.apiNodes2.get(i).primaryCategory.equals(keyword)) {
                s.add(i);
            }
        }
        return s;
    }

    public void relax(int S) {
        for (int i = 0; i < graph.apiNodes.size(); ++i) {
            noi.add(new Pair<>(dp.get(new Pair<>(i, S)).weight, i));
        }

        while (!noi.isEmpty()) {
            int u = Objects.requireNonNull(noi.poll()).getValue();
            if (u < graph.apiNodes2.size()) {
                for (Map.Entry<APINode, Double> entry : graph.apiNodes2.get(u).relatedAPIs.entrySet()) {
                    APINode apiNode = entry.getKey();
                    Double weight = entry.getValue();
                    int v = graph.apiNodes2.indexOf(apiNode);

                    if (dp.get(new Pair<>(v, S)).weight > dp.get(new Pair<>(u, S)).weight + weight) {
                        dp.get(new Pair<>(v, S)).nodes.addAll(dp.get(new Pair<>(u, S)).nodes);
                        dp.get(new Pair<>(v, S)).coveredKeywords.addAll(dp.get(new Pair<>(u, S)).coveredKeywords);
                        dp.get(new Pair<>(v, S)).weight = dp.get(new Pair<>(u, S)).weight + weight;
                        noi.add(new Pair<>(dp.get(new Pair<>(v, S)).weight, v));
                    }
                }
                while (!noi.isEmpty() && Objects.requireNonNull(noi.peek()).getKey() > dp.get(new Pair<>(Objects.requireNonNull(noi.peek()).getValue(), S)).weight) {
                    noi.poll();
                }
            }
        }
    }

    public SteinerTree findMinimumSteinerTree(Set<String> keywords) {
        int U = (1 << keywords.size()) - 1;
        List<Integer> s;

        for (int i = 0; i < graph.apiNodes2.size(); i++) {
            for (int j = 1; j <= U; j++) {
                Set<String> intersection = new HashSet<>(graph.apiNodes2.get(i).secondaryCategories);
                intersection.add(graph.apiNodes2.get(i).primaryCategory);
                dp.put(new Pair<>(i, j), new SteinerTree(graph.apiNodes2.get(i), intersection, INF));
            }
        }

        for (int i = 0; i < keywords.size(); i++) {
            s = searchNode((String) keywords.toArray()[i]);
            for (int j = 0; j < s.size(); j++) {
                dp.get(new Pair<>(s.get(j), 1 << i)).weight = 0;
            }
        }

        for (int S = 1; S <= U; S++) {
            for (int A = (S - 1) & S; A > 0; A = (A - 1) & S) {
                for (int i = 0; i < graph.apiNodes.size(); i++) {
                    if (dp.get(new Pair<>(i, S)).weight > dp.get(new Pair<>(i, A)).weight + dp.get(new Pair<>(i, S ^ A)).weight) {
                        dp.get(new Pair<>(i, S)).nodes.addAll(dp.get(new Pair<>(i, A)).nodes);
                        dp.get(new Pair<>(i, S)).coveredKeywords.addAll(dp.get(new Pair<>(i, A)).coveredKeywords);
                        dp.get(new Pair<>(i, S)).coveredKeywords.addAll(dp.get(new Pair<>(i, S ^ A)).coveredKeywords);
                        dp.get(new Pair<>(i, S)).weight = dp.get(new Pair<>(i, A)).weight + dp.get(new Pair<>(i, S ^ A)).weight;
                    }
                }
            }
            relax(S);
        }

        double min = INF;
        int minIndex = 0;
        for (int i = 0; i < graph.apiNodes2.size(); i++) {
            if (dp.get(new Pair<>(i, U)).weight < min) {
                min = dp.get(new Pair<>(i, U)).weight;
                minIndex = i;
            }
        }

        return dp.get(new Pair<>(minIndex, U));
    }
}

public class KCAR {
    public static void main(String[] args) {
        APIGraph apiGraph = new APIGraph();
        try {
            apiGraph.addAPINodes("C:\\Users\\86186\\Desktop\\Java大作业\\api_out.csv");
            apiGraph.buildGraphFromMashups("C:\\Users\\86186\\Desktop\\Java大作业\\mashup_apis.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }

        apiGraph.applyExponentialTransformationToAllNodes();
        Set<String> keywords = new HashSet<>(Arrays.asList("Customer Relationship Management", "Predictions", "Movies"));

        DynamicProgrammingSteinerTree algorithm = new DynamicProgrammingSteinerTree(apiGraph, keywords);
        SteinerTree minimumTree = algorithm.findMinimumSteinerTree(keywords);

        System.out.println("Minimum Steiner Tree Weight: " + minimumTree.weight);
        for (APINode node : minimumTree.nodes) {
            System.out.println(node.name);
        }
        System.out.println("Minimum Steiner Tree Covered Keywords: " + minimumTree.coveredKeywords);
    }
}

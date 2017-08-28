package org.onosproject.gem.topology;

import org.graphstream.algorithm.Toolkit;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.ProxyPipe;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.LayoutRunner;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.view.Viewer;
import org.onosproject.net.Annotations;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyVertex;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by aca on 5/15/15.
 */
public class GeoTopologyManager implements GeoTopologyService {
    private final Logger log = getLogger(getClass());
    private final String path = "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/";
    private final String pointsFilename = "points.txt";
    private final String steinerOutputFilename = "graph_points.txt";

    protected static GeoTopologyManager geoTopologyManager = null;

    private TopologyGraph topologyGraph;
    private Graph graph;
    private Map<DeviceId, GeoNode> geoNodes;
    private List<GeoNode> sensorNodes;
    private List<GeoNode> steinerNodes;
    private GeoGraph steinerGraph;
    private List<GeoNode> multicastGroup;

    private Map<DeviceId, Integer> visitedNodes;

//    public static GeoTopologyService getInstance(TopologyGraph topologyGraph,
//                                                 List<DeviceId> multicastGroup) {
//        if (geoTopologyManager == null) {
//            geoTopologyManager = new GeoTopologyManager(topologyGraph, multicastGroup);
//        }
//        return geoTopologyManager;
//    }

    public GeoTopologyManager(TopologyGraph topologyGraph,
                              List<DeviceId> multicastGroup) {
        this.topologyGraph = topologyGraph;
        this.geoNodes = new HashMap<>();
        this.graph = new SingleGraph("topology");
        this.steinerNodes = new ArrayList<>();
        this.sensorNodes = new ArrayList<>();
        this.steinerGraph = new GeoGraph();
        this.multicastGroup = new ArrayList<>();

        log.info("Building graph");
        buildGraph();
        log.info("Building Geo Nodes");
        buildGeoNodes();
        for (DeviceId multicastMember : multicastGroup) {
            this.multicastGroup.add(geoNodes.get(multicastMember));
//            log.info("Setting as multicast node {}", multicastMember.toString());
            geoNodes.get(multicastMember).setMulticastGroupMember();
        }
        log.info("Building Steiner graph");
        getEuclideanSteinerGraph();
        log.info("Building Steiner output");
        parseGeoSteinerPlotOutput();
        log.info("Finished initializing");

        this.visitedNodes = new HashMap<>();
    }

    private void buildGraph() {
//        Map<String, Node> nodeMap = new HashMap<>();

        graph.setAttribute("layout.quality", 2);
        Set<TopologyVertex> topologyVertexes = topologyGraph.getVertexes();
        for (TopologyVertex topologyVertex : topologyVertexes) {
            String deviceId = topologyVertex.deviceId().toString();
            if (deviceId.contains("sdnwise")) {
//                log.info("Adding node {} to graph", topologyVertex.deviceId().toString());
                graph.addNode(deviceId).addAttribute("layout.weight", Integer.MAX_VALUE);
//                nodeMap.put(deviceId, node);
            }
        }

        Set<TopologyEdge> topologyEdges = topologyGraph.getEdges();
        for (TopologyEdge topologyEdge : topologyEdges) {
            String srcDeviceId = topologyEdge.src().deviceId().toString();
            String dstDeviceId = topologyEdge.dst().deviceId().toString();
            if ((srcDeviceId.contains("sdnwise")) && (dstDeviceId.contains("sdnwise"))) {
                String edgeId = srcDeviceId + "-" + dstDeviceId;
                String reverseEdgeId = dstDeviceId + "-" + srcDeviceId;

                if ((graph.getEdge(edgeId) == null) &&
                        (graph.getEdge(reverseEdgeId) == null)) {
                    graph.addEdge(edgeId, srcDeviceId, dstDeviceId, false)
                            .addAttribute("layout.weight", linkWeight(topologyEdge.link()));
                }
            }
        }
    }

    private double linkWeight(Link link) {
        double weight = 200;
        Annotations annotations = link.annotations();
        Set<String> keys = annotations.keys();
        if (keys != null) {
            for (String key : keys) {
                if (key.startsWith("sdnwise")) {
                    byte val = Byte.parseByte(annotations.value(key));
                    int value = val & 0xFF;
//                    log.info("RSSI = {}", value);
//                    weight = (double) (1000 / (double) value);
                    double exponent = (double) ((double) (250 - value) / (double) 58);
                    weight = Math.pow(10, exponent);
                }
            }
        }
//        log.info("{} - {} weight = {}", link.src().deviceId(), link.dst().deviceId(), weight);
        return weight;
    }

    private void buildGeoNodes() {
        Layout layout = new SpringBox(false);
        LayoutRunner layoutRunner = new LayoutRunner(graph, layout);
        layout.compute();
//        layoutRunner.start();



//        layoutRunner.release();
        Viewer viewer = graph.display();
        viewer.enableAutoLayout(layout);
        ProxyPipe pipe = viewer.newViewerPipe();
        pipe.addAttributeSink(graph);

        while (layout.getNodeMovedCount() != 0) {
            log.info("Moved {} nodes", layout.getNodeMovedCount());
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
//        while (layout.getStabilization() < 0.9) {
//            try {
//                log.info("Waiting for stabilization with current value {} and limit {}",
//                        layout.getStabilization(), layout.getStabilizationLimit());
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
        log.info("Layout stabilized at {}", layout.getStabilization());
        int cnt = 0;
        while (cnt < 100) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            cnt++;
            pipe.pump();
        }
        pipe.pump();
        Collection<Node> nodes = graph.getNodeSet();
        log.info("Getting node coordinates");
        for (Node node : nodes) {
            double[] xyz = Toolkit.nodePosition(graph, node.getId());
            GeoNode geoNode = new GeoNode(DeviceId.deviceId(node.getId()), xyz[0], xyz[1]);
//            log.info("Adding node {} at position {},{}" , node.getId(), xyz[0], xyz[1]);
            geoNodes.put(DeviceId.deviceId(node.getId()), geoNode);
            if (node.getId().startsWith("sdnwise")) {
                sensorNodes.add(geoNode);
            }
        }

//        viewer.close();

        log.info("DONE Getting details");
    }

//    private void buildGeoNodes() {
//        Viewer viewer = graph.display();
//        ProxyPipe pipe = viewer.newViewerPipe();
//        pipe.addAttributeSink(graph);
//
//        Collection<Node> nodes = graph.getNodeSet();
//
//        int cnt = 0;
//        while (cnt < 20) {
//            pipe.pump();
//
//            for (Node node : nodes) {
//                double[] xyz = Toolkit.nodePosition(graph, node.getId());
//                cnt++;
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//            }
//        }
//
//        log.info("Getting details");
//        for (Node node : nodes) {
//            double[] xyz = Toolkit.nodePosition(graph, node.getId());
//            GeoNode geoNode = new GeoNode(DeviceId.deviceId(node.getId()), xyz[0], xyz[1]);
//            geoNodes.put(DeviceId.deviceId(node.getId()), geoNode);
//            if (node.getId().startsWith("sdnwise")) {
//                sensorNodes.add(geoNode);
//            }
//        }
//
//        log.info("DONE Getting details");
////        viewer.close();
//        log.info("Closed viewer");
//    }

    private void parseGeoSteinerPlotOutput() {
        try {
            FileInputStream fin = new FileInputStream(steinerOutputFilename);
            Scanner scanner = new Scanner(fin);
            int cnt = 0;
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if ((cnt > 0) && (!line.contains("Plot")) && (!line.contains("fs"))) {
                    String[] lineSegments = line.split("\t");
                    System.out.println(Arrays.toString(lineSegments));
                    GeoEdge geoEdge = null;

                    for (int i = 0; i < lineSegments.length; i++) {
                        String lineSegment = lineSegments[i];
                        if ((lineSegment != null) && (!lineSegment.trim().equals(""))) {
                            if (lineSegment.endsWith("T")) {
                                // this is a terminal
                                int length = lineSegment.length() - 2;
                                int terminalIndex = Integer.parseInt(lineSegment.substring(0, length));
//                                System.out.println("Got Terminal: " + terminalIndex);
                                if (geoEdge == null) {
                                    geoEdge = new GeoEdge();
                                }
                                if (geoEdge.getSrc() == null) {
                                    geoEdge.setSrc(multicastGroup.get(terminalIndex));
                                } else if (geoEdge.getDst() == null) {
                                    geoEdge.setDst(multicastGroup.get(terminalIndex));
                                } else {
                                    log.error("Don't know where to put it");
                                }
                            } else if (!lineSegment.equals("S")) {
                                double xCoord = Double.parseDouble(lineSegment);
                                i++;
                                lineSegment = lineSegments[i];
                                double yCoord = Double.parseDouble(lineSegment);
                                GeoNode geoNode = new GeoNode(xCoord, yCoord);
                                steinerNodes.add(geoNode);
                                GeoNode closestRealNode = getClosestNodeToSteiner(geoNode);
                                if (geoEdge == null) {
                                    geoEdge = new GeoEdge();
                                }
                                if (geoEdge.getSrc() == null) {
                                    geoEdge.setSrc(closestRealNode);
                                } else if (geoEdge.getDst() == null) {
                                    geoEdge.setDst(closestRealNode);
                                } else {
                                    log.error("Don't know where to put it");
                                }
//                                System.out.println("Got Steiner Point: " + xCoord + " " + yCoord);
                            }
                        }
                    }
                    if (geoEdge != null) {
                        steinerGraph.addEdge(geoEdge);
                        log.info("Adding edge {}", geoEdge.toString());
                    }
                } else {
                    cnt++;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getEuclideanSteinerGraph() {
        String input = "";
        String output = null;
        if ((multicastGroup != null) && (multicastGroup.size() > 0)) {
            for (GeoNode geoNode : multicastGroup) {
                if (geoNode.getDeviceId().toString().startsWith("sdnwise")) {
                    double x = geoNode.getxCoord();
                    double y = geoNode.getyCoord();
                    input = input + " " + x + "  " + y + "\n";
                }
            }
            try {
                FileOutputStream fout = new FileOutputStream(pointsFilename);
                fout.write(input.getBytes());
                fout.flush();
                fout.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String command = path + "plot_steiner.sh " + pointsFilename + " " + steinerOutputFilename;
            log.info(command);
            output = executeCommand(command);
        }
        return output;
    }

    private String executeCommand(String command) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output.toString();

    }

    @Override
    public List<GeoNode> geoNodes() {
        return new ArrayList<>(geoNodes.values());
    }

    @Override
    public List<GeoNode> euclideanSteinerPoints() {
        return this.steinerNodes;
    }

    @Override
    public GeoNode geoNode(DeviceId deviceId) {
        return geoNodes.get(deviceId);
    }

    @Override
    public GeoGraph euclideanSteinerGraph() {
        return this.steinerGraph;
    }

    @Override
    public List<GeoNode> mappedSteinerPoints() {
        List<GeoNode> mappedSteinerPoints = new ArrayList<>();

        if ((steinerNodes != null) && (steinerNodes.size() > 0)) {
            for (GeoNode steinerNode : steinerNodes) {
                double minDistance = Double.MAX_VALUE;
                GeoNode nodeWithMinDistance = null;
                for (GeoNode geoNode : geoNodes.values()) {
                    double distance = euclideanDistance(steinerNode, geoNode);
                    if (distance < minDistance) {
                        nodeWithMinDistance = geoNode;
                        minDistance = distance;
                    }
                }
                mappedSteinerPoints.add(nodeWithMinDistance);
                // set the mapped node to be a steiner point
                geoNodes.get(nodeWithMinDistance.getDeviceId()).setSteiner();
            }
        }

        return mappedSteinerPoints;
    }

    private GeoNode getClosestNodeToSteiner(GeoNode steinerNode) {
        double minDistance = Double.MAX_VALUE;
        GeoNode nodeWithMinDistance = null;
        for (GeoNode geoNode : geoNodes.values()) {
            double distance = euclideanDistance(steinerNode, geoNode);
            if (distance < minDistance) {
                nodeWithMinDistance = geoNode;
                minDistance = distance;
            }
        }

        return nodeWithMinDistance;
    }

    private double euclideanDistance(GeoNode node1, GeoNode node2) {
        double x1 = node1.getxCoord();
        double y1 = node1.getyCoord();
        double x2 = node2.getxCoord();
        double y2 = node2.getyCoord();

        double distance = Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));

        return distance;
    }

    public List<GeoNode> geographicPath(GeoNode src, GeoNode dst) {
        List<GeoNode> path = new ArrayList<>();

        GeoNode nextHop = nextIntermediateHop(src, dst);
        while (nextHop != dst) {
            path.add(nextHop);
            nextHop = nextIntermediateHop(src, dst);
        }

        return path;
    }

    public GeoNode nextIntermediateHop(GeoNode src, GeoNode dst) {
        GeoNode nextHop = null;
//        log.info("Searching for next hop between {} and {}", src.getDeviceId(), dst.getDeviceId());

        String srcDeviceId = src.getDeviceId().toString();

        Iterator<Edge> edges = graph.getEdgeIterator();
        List<String> srcNeighborNodeIds = new ArrayList<>();
        while (edges.hasNext()) {
            Edge edge = edges.next();
            if (edge.getSourceNode().getId().equals(srcDeviceId)) {
                String neighborId = edge.getTargetNode().getId();
                srcNeighborNodeIds.add(neighborId);
            } else if (edge.getTargetNode().getId().equals(srcDeviceId)) {
                String neighborId = edge.getSourceNode().getId();
                srcNeighborNodeIds.add(neighborId);
            }
        }

        if (srcNeighborNodeIds.size() > 0) {
            double minDistance = Double.MAX_VALUE;
            for (String srcNeighborNodeId : srcNeighborNodeIds) {
                DeviceId deviceId = DeviceId.deviceId(srcNeighborNodeId);

                GeoNode geoNode = geoNodes.get(deviceId);
                double distance = euclideanDistance(geoNode, dst);
//                log.info("Checking device {} with distance {}", deviceId, distance);
                if (distance < minDistance) {
                    minDistance = distance;
                    nextHop = geoNode;
                }
            }
        }

        return nextHop;
    }

    private void path(GeoNode prev, GeoNode cur) {
        visitNode(cur);

        if (cur.isSteiner()) {
            GeoNode[] neighbors = nxDoubleHops(prev, cur);
            for (int i = 0; i < neighbors.length; i++) {
                GeoNode next = neighbors[i];
                GeoNode nextIntermediateNode = nextIntermediateHop(cur, next);
                while (nextIntermediateNode != next) {
                    visitNode(nextIntermediateNode);
                    nextIntermediateNode = nextIntermediateHop(cur, next);
                }
                path(cur, next);
            }
        } else if (cur.isMulticastGroupMember()) {
            GeoNode next = nxSingleHop(prev, cur);
            GeoNode nextIntermediateNode = nextIntermediateHop(cur, next);
            while (nextIntermediateNode != next) {
                visitNode(nextIntermediateNode);
                nextIntermediateNode = nextIntermediateHop(cur, next);
            }
            path(cur, next);
        }

    }

    private void visitNode(GeoNode node) {
        Integer timesVisited = visitedNodes.get(node.getDeviceId());
        if (timesVisited == null) {
            timesVisited = 0;
        }
        timesVisited++;
        visitedNodes.put(node.getDeviceId(), timesVisited);
    }

    public List<GeoNode> getNextMulticastHops(GeoNode prev, GeoNode cur) {
        List<GeoNode> nextMulticastHops = new ArrayList<>();

        List<GeoEdge> geoEdges = steinerGraph.getGeoEdges(cur);
        // check if this is the initiator
        if (prev == null) {
            if ((geoEdges != null) && (geoEdges.size() > 0)) {
                for (GeoEdge geoEdge : geoEdges) {
                    if (geoEdge.getSrc().equals(cur)) {
                        nextMulticastHops.add(geoEdge.getDst());
                    } else {
                        nextMulticastHops.add(geoEdge.getSrc());
                    }
                }
            }
        } else {
            if ((geoEdges != null) && (geoEdges.size() > 0)) {
                for (GeoEdge geoEdge : geoEdges) {
                    if (geoEdge.getSrc().equals(cur)) {
                        if (!geoEdge.getDst().equals(prev)) {
                            nextMulticastHops.add(geoEdge.getDst());
                        }
                    } else {
                        if (!geoEdge.getSrc().equals(prev)) {
                            nextMulticastHops.add(geoEdge.getSrc());
                        }
                    }
                }
            }
        }

        return nextMulticastHops;
    }

    public Path getIntermediatePath(GeoNode prevNode, GeoNode curNode) {
        List<GeoNode> intermediateNodes = new ArrayList<>();
        intermediateNodes.add(prevNode);

        GeoNode nextIntermediateNode = nextIntermediateHop(prevNode, curNode);
        while (!nextIntermediateNode.equals(curNode)) {
//            log.info("Adding {} as intermediate node", nextIntermediateNode.getDeviceId());
            if (intermediateNodes.contains(nextIntermediateNode)) {
                log.warn("Recursion cycle in {}-->{}",
                        (prevNode != null ? prevNode.getDeviceId() : "none"), curNode.getDeviceId());
                return null;
            }
            intermediateNodes.add(nextIntermediateNode);
            nextIntermediateNode = nextIntermediateHop(nextIntermediateNode, curNode);
        }
        intermediateNodes.add(curNode);

        Path path = new GeoPath(intermediateNodes);

        return path;
    }

    private GeoNode nxSingleHop(GeoNode prev, GeoNode cur) {
        GeoNode nextHop = null;
        GeoNode nextMulticastDestination = null;

        String prevDeviceId = prev.getDeviceId().toString();
        String curDeviceId = cur.getDeviceId().toString();

        Node curNode = graph.getNode(curDeviceId);
        Iterator<Edge> nodeEdges = curNode.getEachEdge().iterator();
        while (nodeEdges.hasNext()) {
            Edge edge = nodeEdges.next();
            if (!(edge.getSourceNode().getId().equals(prevDeviceId)) &&
                    !(edge.getTargetNode().getId().equals(prevDeviceId))) {
                String nextNodeId = null;
                if (!(edge.getSourceNode().getId().equals(prevDeviceId))) {
                    nextNodeId = edge.getSourceNode().getId();

                } else {
                    nextNodeId = edge.getTargetNode().getId();
                }
                nextHop = geoNodes.get(DeviceId.deviceId(nextNodeId));
                break;
            }
        }

        return nextHop;
    }

    // This is for a Steiner point
    private GeoNode[] nxDoubleHops(GeoNode prev, GeoNode cur) {
        List<GeoEdge> geoEdges = steinerGraph.getGeoEdges(cur);
        GeoNode[] nextHops = new GeoNode[2];
        if ((geoEdges != null) && (geoEdges.size() > 0)) {
            int index = 0;
            for (GeoEdge geoEdge : geoEdges) {
                if (geoEdge.getSrc().equals(cur)) {
                    if (!geoEdge.getDst().equals(prev)) {
                         nextHops[index++] = geoEdge.getDst();
                    }
                } else {
                    if (!geoEdge.getSrc().equals(prev)) {
                        nextHops[index++] = geoEdge.getSrc();
                    }
                }
            }
        }
        return nextHops;
    }

    // This is for taking the nodes connected with the source
    // If source is a leaf, there will be 1 node
    // If source is a regular multicast node, there will be 2 nodes
    // If source is a steiner point, there will be 3 nodes
    private GeoNode[] srcNxHops(GeoNode cur) {
        List<GeoEdge> geoEdges = steinerGraph.getGeoEdges(cur);
        GeoNode[] nextHops = new GeoNode[geoEdges.size()];
        if ((geoEdges != null) && (geoEdges.size() > 0)) {
            int index = 0;
            for (GeoEdge geoEdge : geoEdges) {
                if (geoEdge.getSrc().equals(cur)) {
                    nextHops[index++] = geoEdge.getDst();
                } else {
                    nextHops[index++] = geoEdge.getSrc();
                }
            }
        }
        return nextHops;
    }
}

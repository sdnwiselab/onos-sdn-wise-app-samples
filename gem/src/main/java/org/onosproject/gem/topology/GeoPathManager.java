package org.onosproject.gem.topology;

import com.google.common.collect.ImmutableList;
import org.onosproject.net.Path;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SensorNodeLocalization;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyVertex;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by aca on 5/27/15.
 */
public class GeoPathManager {
    private final Logger log = getLogger(getClass());
    private final String path = "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/";
    private final String pointsFilename = "points.txt";
    private final String steinerOutputFilename = "graph_points.txt";

    private SensorNodeService sensorNodeService;
    private TopologyService topologyService;

    private List<SensorNode> multicastGroup;
    private List<SensorNode> steinerNodes;
    private List<SensorNode> allNodes;

    private GeoSensorGraph steinerGraph;
    private SensorNodeLocalization sensorNodeLocalization;

    public GeoPathManager(SensorNodeService sensorNodeService, TopologyService topologyService,
                          List<SensorNode> multicastGroup, SensorNodeLocalization sensorNodeLocalization) {
        this.multicastGroup = ImmutableList.copyOf(multicastGroup);
        this.steinerNodes = new ArrayList<>();
        this.allNodes = new ArrayList<>();
        this.steinerGraph = new GeoSensorGraph();
        this.sensorNodeService = sensorNodeService;
        this.topologyService = topologyService;
        this.sensorNodeLocalization = sensorNodeLocalization;

        Set<TopologyVertex> vertexes = topologyService.getGraph(topologyService.currentTopology()).getVertexes();
        if ((vertexes != null) && (vertexes.size() > 0)) {
            for (TopologyVertex vertex : vertexes) {
                SensorNode sensorNode = sensorNodeService.getSensorNode(vertex.deviceId());
                if (sensorNode != null) {
                    allNodes.add(sensorNodeService.getSensorNode(vertex.deviceId()));
                }
            }
        }

        getEuclideanSteinerGraph(sensorNodeLocalization);
        parseGeoSteinerPlotOutput();
    }

    private String getEuclideanSteinerGraph(SensorNodeLocalization geoLocalizationAlgorithm) {
        String input = "";
        String output = null;
        if ((multicastGroup != null) && (multicastGroup.size() > 0)) {
            for (SensorNode sensorNode : multicastGroup) {
                if (sensorNode.deviceId().toString().startsWith("sdnwise")) {
//                    GeoLocalizationAlgorithm geoLocalizationAlgorithm = new GeoLocalizationAlgorithm();
                    double[] xyzCoordinates = sensorNode.xyzCoordinates(geoLocalizationAlgorithm);
                    double x = xyzCoordinates[0];
                    double y = xyzCoordinates[1];
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
//            log.info(command);
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

    private void parseGeoSteinerPlotOutput() {
        try {
            FileInputStream fin = new FileInputStream(steinerOutputFilename);
            Scanner scanner = new Scanner(fin);
            int cnt = 0;
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if ((cnt > 0) && (!line.contains("Plot")) && (!line.contains("fs"))) {
                    String[] lineSegments = line.split("\t");
//                    System.out.println(Arrays.toString(lineSegments));
                    GeoSensorEdge geoEdge = null;

                    for (int i = 0; i < lineSegments.length; i++) {
                        String lineSegment = lineSegments[i];
                        if ((lineSegment != null) && (!lineSegment.trim().equals(""))) {
                            if (lineSegment.endsWith("T")) {
                                // this is a terminal
                                int length = lineSegment.length() - 2;
                                int terminalIndex = Integer.parseInt(lineSegment.substring(0, length));
//                                log.info("Got Terminal: {}", multicastGroup.get(terminalIndex).deviceId());
                                if (geoEdge == null) {
                                    geoEdge = new GeoSensorEdge();
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
                                SensorNode closestRealNode = getClosestNodeToSteiner(xCoord, yCoord);
                                steinerNodes.add(closestRealNode);
                                if (geoEdge == null) {
                                    geoEdge = new GeoSensorEdge();
                                }
                                if (geoEdge.getSrc() == null) {
                                    geoEdge.setSrc(closestRealNode);
                                } else if (geoEdge.getDst() == null) {
                                    geoEdge.setDst(closestRealNode);
                                } else {
                                    log.error("Don't know where to put it");
                                }
//                                log.info("Got Steiner Point: {}", closestRealNode.deviceId());
                            }
                        }
                    }
                    if (geoEdge != null) {
                        steinerGraph.addGeoSensorEdge(geoEdge);
//                        log.info("Adding edge {}", geoEdge.toString());
                    }
                } else {
                    cnt++;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

//    public Path getIntermediatePath(SensorNode prevNode, SensorNode curNode) {
//        List<SensorNode> intermediateNodes = new ArrayList<>();
//        intermediateNodes.add(prevNode);
//
//        SensorNode nextIntermediateNode = nextIntermediateHop(prevNode, curNode, null);
//        while (!nextIntermediateNode.equals(curNode)) {
////            log.info("Adding {} as intermediate node", nextIntermediateNode.getDeviceId());
//            if (intermediateNodes.contains(nextIntermediateNode)) {
//                log.warn("Recursion cycle in {}-->{}",
//                        (prevNode != null ? prevNode.deviceId() : "none"), curNode.deviceId());
//                return null;
//            }
//            intermediateNodes.add(nextIntermediateNode);
//            nextIntermediateNode = nextIntermediateHop(nextIntermediateNode, curNode, intermediateNodes);
//        }
//        intermediateNodes.add(curNode);
//
//
//        Path path = new GeoSensorPath(intermediateNodes);
//
//        return path;
//    }

    public Path getIntermediatePath(SensorNode sinkNode, SensorNode prevNode, SensorNode curNode) {
        List<SensorNode> intermediateNodes = new ArrayList<>();
        intermediateNodes.add(sinkNode);

        SensorNode nextIntermediateNode = nextIntermediateHop(sinkNode, prevNode, null);
        while (!nextIntermediateNode.equals(prevNode)) {
//            log.info("Adding {} as intermediate node", nextIntermediateNode.deviceId());
            if (intermediateNodes.contains(nextIntermediateNode)) {
                log.warn("Recursion cycle in 1st round {}-->{}",
                        (sinkNode != null ? sinkNode.deviceId() : "none"), prevNode.deviceId());
                return null;
            }
            intermediateNodes.add(nextIntermediateNode);
            nextIntermediateNode = nextIntermediateHop(nextIntermediateNode, prevNode, intermediateNodes);
        }

        intermediateNodes.add(prevNode);

        nextIntermediateNode = nextIntermediateHop(prevNode, curNode, null);
        List<SensorNode> exclusionList = new ArrayList<>();
        while (!nextIntermediateNode.equals(curNode)) {
//            log.info("Adding {} as intermediate node", nextIntermediateNode.getDeviceId());
            if (intermediateNodes.contains(nextIntermediateNode)) {
                log.warn("Possible Recursion cycle in 2nd round {}-->{}",
                        (prevNode != null ? prevNode.deviceId() : "none"), curNode.deviceId());
//                return null;
            }
            exclusionList.add(nextIntermediateNode);
            intermediateNodes.add(nextIntermediateNode);
            nextIntermediateNode = nextIntermediateHop(nextIntermediateNode, curNode, exclusionList);
//            log.info("Next intermediate node is {}",
//                    (nextIntermediateNode != null ? nextIntermediateNode.deviceId() : "NONE"));
//            log.info("Cur node is {}",
//                    (curNode != null ? curNode.deviceId() : "NONE"));
        }
        intermediateNodes.add(curNode);

        Path path = new GeoSensorPath(intermediateNodes, sensorNodeLocalization);

        return path;
    }

    public Path getIntermediatePath(SensorNode prevNode, SensorNode curNode) {
        List<SensorNode> intermediateNodes = new ArrayList<>();

        intermediateNodes.add(prevNode);

        SensorNode nextIntermediateNode = nextIntermediateHop(prevNode, curNode, null);
        List<SensorNode> exclusionList = new ArrayList<>();
        while (!nextIntermediateNode.equals(curNode)) {
//            log.info("Adding {} as intermediate node", nextIntermediateNode.getDeviceId());
            if (intermediateNodes.contains(nextIntermediateNode)) {
                log.warn("Possible Recursion cycle in 2nd round {}-->{}",
                        (prevNode != null ? prevNode.deviceId() : "none"), curNode.deviceId());
//                return null;
            }
            exclusionList.add(nextIntermediateNode);
            intermediateNodes.add(nextIntermediateNode);
            nextIntermediateNode = nextIntermediateHop(nextIntermediateNode, curNode, exclusionList);
//            log.info("Next intermediate node is {}",
//                    (nextIntermediateNode != null ? nextIntermediateNode.deviceId() : "NONE"));
//            log.info("Cur node is {}",
//                    (curNode != null ? curNode.deviceId() : "NONE"));
        }
        intermediateNodes.add(curNode);

        Path path = new GeoSensorPath(intermediateNodes, sensorNodeLocalization);

        return path;
    }

    public SensorNode nextIntermediateHop(SensorNode src, SensorNode dst, List<SensorNode> exclusion) {
        SensorNode nextHop = null;
        if ((src != null) && (src.equals(dst))) {
            return dst;
        }
        List<SensorNode> nodesToExclude = new ArrayList<>();
        if ((exclusion != null) && (exclusion.size() > 0)) {
            for (SensorNode sensorNode : exclusion) {
                nodesToExclude.add(sensorNode);
            }
        }
//        log.info("Searching for next hop between {} and {}", src.getDeviceId(), dst.getDeviceId());

        // first get the src neighbors
        List<SensorNode> neighbors = new ArrayList<>();
        TopologyGraph topologyGraph = topologyService.getGraph(topologyService.currentTopology());

        Set<TopologyEdge> topologyEdges = topologyGraph.getEdgesFrom(new DefaultTopologyVertex(src.deviceId()));
        if ((topologyEdges != null) && (topologyEdges.size() > 0)) {
            for (TopologyEdge topologyEdge : topologyEdges) {
                TopologyVertex vertex = topologyEdge.dst();
                SensorNode neighbor = sensorNodeService.getSensorNode(vertex.deviceId());
                if ((neighbor != null) && (!neighbors.contains(neighbor))) {
                    neighbors.add(neighbor);
                }
            }
        }

        topologyEdges = topologyGraph.getEdgesTo(new DefaultTopologyVertex(src.deviceId()));
        if ((topologyEdges != null) && (topologyEdges.size() > 0)) {
            for (TopologyEdge topologyEdge : topologyEdges) {
                TopologyVertex vertex = topologyEdge.src();
                SensorNode neighbor = sensorNodeService.getSensorNode(vertex.deviceId());
                if ((neighbor != null) && (!neighbors.contains(neighbor))) {
                    neighbors.add(neighbor);
                }
            }
        }

        if (neighbors.size() > 0) {
            double minDistance = Double.MAX_VALUE;
            for (SensorNode neighbor : neighbors) {
                double distance = euclideanDistance(neighbor, dst);
//                log.info("Trying {} -> {}", neighbor.deviceId(), dst.deviceId());
                if ((distance < minDistance) && (!nodesToExclude.contains(neighbor))) {
                    minDistance = distance;
                    nextHop = neighbor;
                }
            }
        }

        return nextHop;
    }

    public List<SensorNode> getNextMulticastHops(SensorNode prev, SensorNode cur) {
        if ((prev != null) && (prev.equals(cur))) {
            return null;
        }
        List<SensorNode> nextMulticastHops = new ArrayList<>();

        List<GeoSensorEdge> geoEdges = steinerGraph.getGeoEdges(cur);
        // check if this is the initiator
        if (prev == null) {
            if ((geoEdges != null) && (geoEdges.size() > 0)) {
                for (GeoSensorEdge geoEdge : geoEdges) {
                    if (geoEdge.getSrc().equals(cur)) {
                        nextMulticastHops.add(geoEdge.getDst());
                    } else {
                        nextMulticastHops.add(geoEdge.getSrc());
                    }
                }
            }
        } else {
            if ((geoEdges != null) && (geoEdges.size() > 0)) {
                for (GeoSensorEdge geoEdge : geoEdges) {
                    if (geoEdge.getSrc().equals(cur)) {
                        if (!geoEdge.getDst().equals(prev)) {
                            nextMulticastHops.add(geoEdge.getDst());
                        }
                    } else if (geoEdge.getDst().equals(cur)) {
                        if (!geoEdge.getSrc().equals(prev)) {
                            nextMulticastHops.add(geoEdge.getSrc());
                        }
                    }
                }
            }
        }

        return nextMulticastHops;
    }


    private SensorNode getClosestNodeToSteiner(double xCoord, double yCoord) {
        double minDistance = Double.MAX_VALUE;
        SensorNode nodeWithMinDistance = null;
        for (SensorNode sensorNode : allNodes) {
            double distance = euclideanDistance(sensorNode, xCoord, yCoord);
            if (distance < minDistance) {
                nodeWithMinDistance = sensorNode;
                minDistance = distance;
            }
        }

        return nodeWithMinDistance;
    }

    private double euclideanDistance(SensorNode node1, SensorNode node2) {
//        log.info("Calculating distance between {} and {}",
//                (node1 != null ? node1.deviceId() : "NONE"),
//                (node2 != null ? node2.deviceId() : "NONE"));
        double[] node1Coordinates = node1.xyzCoordinates(sensorNodeLocalization);
        double[] node2Coordinates = node2.xyzCoordinates(sensorNodeLocalization);

        double x1 = node1Coordinates[0];
        double y1 = node1Coordinates[1];
        double x2 = node2Coordinates[0];
        double y2 = node2Coordinates[1];

        double distance = Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));

        return distance;
    }

    private double euclideanDistance(SensorNode node, double xCoord, double yCoord) {
        double[] nodeCoordinates = node.xyzCoordinates(sensorNodeLocalization);

        double x1 = nodeCoordinates[0];
        double y1 = nodeCoordinates[1];
        double x2 = xCoord;
        double y2 = yCoord;

        double distance = Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));

        return distance;
    }
}

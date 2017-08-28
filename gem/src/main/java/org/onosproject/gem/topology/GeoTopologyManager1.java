package org.onosproject.gem.topology;

import org.graphstream.algorithm.Toolkit;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.ProxyPipe;
import org.graphstream.ui.view.Viewer;
import org.onosproject.net.Annotations;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by aca on 5/5/15.
 */
public class GeoTopologyManager1 implements GeoTopologyService {
    private final Logger log = getLogger(getClass());
    private final String path = "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/";

    private TopologyService topologyService;
    private Graph graph;
    private Map<DeviceId, GeoNode> geoNodes;
//    private List<GeoNode> geoNodes;
    private List<GeoNode> sensorNodes;
    private List<GeoNode> steinerNodes;
    private GeoGraph steinerGraph;

    public GeoTopologyManager1(TopologyService topologyService) {
        this.topologyService = topologyService;
        this.geoNodes = new HashMap<>();
        this.graph = new SingleGraph("topology");
        this.steinerNodes = new ArrayList<>();
        this.sensorNodes = new ArrayList<>();
        this.steinerGraph = new GeoGraph();
        buildGraph();
        buildGeoNodes();
        getEuclideanSteinerGraph();
        parseGeoSteinerPlotOutput();
    }

    private void buildGraph() {
        TopologyGraph topologyGraph = topologyService.getGraph(topologyService.currentTopology());
        Map<String, Node> nodeMap = new HashMap<>();

        Set<TopologyVertex> topologyVertexes = topologyGraph.getVertexes();
        for (TopologyVertex topologyVertex : topologyVertexes) {
            String deviceId = topologyVertex.deviceId().toString();
            Node node = graph.addNode(deviceId);
            nodeMap.put(deviceId, node);
        }

        Set<TopologyEdge> topologyEdges = topologyGraph.getEdges();
        for (TopologyEdge topologyEdge : topologyEdges) {
            String srcDeviceId = topologyEdge.src().deviceId().toString();
            String dstDeviceId = topologyEdge.dst().deviceId().toString();
            String edgeId = srcDeviceId + "-" + dstDeviceId;
            String reverseEdgeId = dstDeviceId + "-" + srcDeviceId;

//            log.info("Trying edge {}", edgeId);

            if ((graph.getEdge(edgeId) == null) &&
                    (graph.getEdge(reverseEdgeId) == null)) {
                graph.addEdge(edgeId, srcDeviceId, dstDeviceId, false)
                        .addAttribute("layout.weight", linkWeight(topologyEdge.link()));
//                log.info("Added edge {}", edgeId);
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
                    weight = (double) (1000 / (double) value);
                }
            }
        }
        return weight;
    }

    private void buildGeoNodes() {
        Viewer viewer = graph.display();
        ProxyPipe pipe = viewer.newViewerPipe();
        pipe.addAttributeSink(graph);

        boolean flag = true;
        Collection<Node> nodes = graph.getNodeSet();

        int cnt = 0;
        while (cnt < 20) {
            pipe.pump();

            for (Node node : nodes) {
                double[] xyz = Toolkit.nodePosition(graph, node.getId());
                cnt++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        for (Node node : nodes) {
            double[] xyz = Toolkit.nodePosition(graph, node.getId());
            GeoNode geoNode = new GeoNode(DeviceId.deviceId(node.getId()), xyz[0], xyz[1]);
            geoNodes.put(DeviceId.deviceId(node.getId()), geoNode);
            if (node.getId().startsWith("sdnwise")) {
                sensorNodes.add(geoNode);
            }
        }

        viewer.close();
    }

    @Override
    public List<GeoNode> geoNodes() {
        return new ArrayList<GeoNode>(geoNodes.values());
    }

    public List<GeoNode> euclideanSteinerPoints(List<DeviceId> multicastGroup) {
        // Create a list with the Geo Nodes for the mutlicast group
        List<GeoNode> multicastGroupGeoNodes = new ArrayList<>();
        for (DeviceId mutlicastMember : multicastGroup) {
            multicastGroupGeoNodes.add(geoNodes.get(mutlicastMember));
        }

        // Write the coordinates into the GeoSteiner input file
        String fileName = path + "mutlicast_points.txt";
        if ((multicastGroupGeoNodes != null) && (multicastGroupGeoNodes.size() > 0)) {
            String input = "";
            for (GeoNode geoNode : multicastGroupGeoNodes) {
                double x = geoNode.getxCoord();
                double y = geoNode.getyCoord();
                input = input + " " + x + "  " + y + "\n";
            }
            try {
                FileOutputStream fout = new FileOutputStream(fileName);
                fout.write(input.getBytes());
                fout.flush();
                fout.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String command = path + "steiner.sh " + fileName;
            log.info(command);
            String output = executeCommand(command);

            int beginIndex = 0;
            boolean more = true;
            while (more) {
                int endIndex = output.indexOf("\n");
                String intermediate = output.substring(beginIndex, endIndex);
                beginIndex = endIndex + 1;
                int spaceIndex = intermediate.indexOf(" ");
                String xCoord = intermediate.substring(0, spaceIndex);
                String yCoord = intermediate.substring(spaceIndex + 1);
                GeoNode geoNode = new GeoNode(Double.valueOf(xCoord), Double.valueOf(yCoord));
                steinerNodes.add(geoNode);
                try {
                    output = output.substring(endIndex + 1);
                } catch (IndexOutOfBoundsException e) {
                    more = false;
                }

            }
        }

        return steinerNodes;
    }

    public GeoGraph euclideanSteinerGraph(List<DeviceId> multicastGroup) {

        // Create a list with the Geo Nodes for the mutlicast group
        List<GeoNode> multicastGroupGeoNodes = new ArrayList<>();
        for (DeviceId mutlicastMember : multicastGroup) {
            multicastGroupGeoNodes.add(geoNodes.get(mutlicastMember));
        }

        // Write the coordinates into the GeoSteiner input file
        String fileName = path + "mutlicast_points.txt";
        String outputFileName = path + "steiner_graph.txt";
        if ((multicastGroupGeoNodes != null) && (multicastGroupGeoNodes.size() > 0)) {
            String input = "";
            for (GeoNode geoNode : multicastGroupGeoNodes) {
                double x = geoNode.getxCoord();
                double y = geoNode.getyCoord();
                input = input + " " + x + "  " + y + "\n";
            }
            try {
                FileOutputStream fout = new FileOutputStream(fileName);
                fout.write(input.getBytes());
                fout.flush();
                fout.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String command = path + "plot_steiner.sh " + fileName + " " + outputFileName;
            log.info(command);
            executeCommand(command);

            try {
                FileInputStream fin = new FileInputStream(outputFileName);
                Scanner scanner = new Scanner(fin);
                int cnt = 0;
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    if (cnt > 0) {
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
                                        geoEdge.setSrc(sensorNodes.get(terminalIndex));
                                    } else if (geoEdge.getDst() == null) {
                                        geoEdge.setDst(sensorNodes.get(terminalIndex));
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
                                    if (geoEdge == null) {
                                        geoEdge = new GeoEdge();
                                    }
                                    if (geoEdge.getSrc() == null) {
                                        geoEdge.setSrc(geoNode);
                                    } else if (geoEdge.getDst() == null) {
                                        geoEdge.setDst(geoNode);
                                    } else {
                                        log.error("Don't know where to put it");
                                    }
//                                System.out.println("Got Steiner Point: " + xCoord + " " + yCoord);
                                }
                            }
                        }
                        if (geoEdge != null) {
                            steinerGraph.addEdge(geoEdge);
                        }
                    } else {
                        cnt++;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public List<GeoNode> euclideanSteinerPoints() {
        return steinerNodes;
    }

    @Override
    public GeoNode geoNode(DeviceId deviceId) {
        return null;
    }

    public GeoGraph euclideanSteinerGraph() {
        return steinerGraph;
    }

    @Override
    public List<GeoNode> mappedSteinerPoints() {
        return null;
    }

    @Override
    public Path getIntermediatePath(GeoNode prevNode, GeoNode curNode) {
        return null;
    }

    @Override
    public List<GeoNode> getNextMulticastHops(GeoNode prev, GeoNode cur) {
        return null;
    }

    private void parseGeoSteinerPlotOutput() {
        try {
            FileInputStream fin = new FileInputStream(
                    "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/steiner_graph.txt");
            Scanner scanner = new Scanner(fin);
            int cnt = 0;
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (cnt > 0) {
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
                                    geoEdge.setSrc(sensorNodes.get(terminalIndex));
                                } else if (geoEdge.getDst() == null) {
                                    geoEdge.setDst(sensorNodes.get(terminalIndex));
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
                                if (geoEdge == null) {
                                    geoEdge = new GeoEdge();
                                }
                                if (geoEdge.getSrc() == null) {
                                    geoEdge.setSrc(geoNode);
                                } else if (geoEdge.getDst() == null) {
                                    geoEdge.setDst(geoNode);
                                } else {
                                    log.error("Don't know where to put it");
                                }
//                                System.out.println("Got Steiner Point: " + xCoord + " " + yCoord);
                            }
                        }
                    }
                    if (geoEdge != null) {
                        steinerGraph.addEdge(geoEdge);
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
        String filename = "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/points.txt";
        if ((geoNodes != null) && (geoNodes.size() > 0)) {
            for (GeoNode geoNode : geoNodes.values()) {
                if (geoNode.getDeviceId().toString().startsWith("sdnwise")) {
                    double x = geoNode.getxCoord();
                    double y = geoNode.getyCoord();
                    input = input + " " + x + "  " + y + "\n";
                }
            }
            try {
                FileOutputStream fout = new FileOutputStream(filename);
                fout.write(input.getBytes());
                fout.flush();
                fout.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String command = "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/plot_steiner.sh " + filename;
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


//    public static void main(String args[]) {
//        List<GeoNode> steinerPoints = new ArrayList<>();
//        steinerPoints.add(new GeoNode(18.61754699659967, 50.31895680801704));
//        steinerPoints.add(new GeoNode(19.23266678559023, 53.13466216697692));
//        steinerPoints.add(new GeoNode(17.66618247249584, 54.56121536118677));
//        List<GeoNode> terminals = new ArrayList<>();
//        terminals.add(new GeoNode(20.52786799586230, 53.54743469566112));
//        terminals.add(new GeoNode(15.75181182727815, 53.95111740436095));
//        terminals.add(new GeoNode(18.37321666659701, 57.79765809141587));
//        terminals.add(new GeoNode(19.77977082789737, 49.26055226156524));
//        terminals.add(new GeoNode(15.44070616536275, 49.30651749531640));
//        try {
//            FileInputStream fin = new FileInputStream(
//                    "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/steiner_graph.txt");
//            Scanner scanner = new Scanner(fin);
//            int cnt = 0;
//            GeoGraph geoGraph = new GeoGraph();
//            while (scanner.hasNext()) {
//                String line = scanner.nextLine();
//                if (cnt > 0) {
//                    String[] lineSegments = line.split("\t");
//                    System.out.println(Arrays.toString(lineSegments));
//                    GeoEdge geoEdge = null;
//
//                    for (int i = 0; i < lineSegments.length; i++) {
//                        String lineSegment = lineSegments[i];
//                        geoEdge = new GeoEdge();
//                        if ((lineSegment != null) && (!lineSegment.trim().equals(""))) {
//                            if (lineSegment.endsWith("T")) {
//                                // this is a terminal
//                                int length = lineSegment.length() - 2;
//                                int terminalIndex = Integer.parseInt(lineSegment.substring(0, length));
//                                System.out.println("Got Terminal: " + terminalIndex);
//                                if (geoEdge.getSrc() == null) {
//                                    geoEdge.setSrc(terminals.get(terminalIndex));
//                                } else if (geoEdge.getDst() == null) {
//                                    geoEdge.setDst(terminals.get(terminalIndex));
//                                } else {
//                                    System.err.println("Don't know where to put it");
//                                }
//                            } else if (!lineSegment.equals("S")) {
//                                double xCoord = Double.parseDouble(lineSegment);
//                                i++;
//                                lineSegment = lineSegments[i];
//                                double yCoord = Double.parseDouble(lineSegment);
//                                GeoNode geoNode = new GeoNode(xCoord, yCoord);
//                                if (geoEdge.getSrc() == null) {
//                                    geoEdge.setSrc(geoNode);
//                                } else if (geoEdge.getDst() == null) {
//                                    geoEdge.setDst(geoNode);
//                                } else {
//                                    System.err.println("Don't know where to put it");
//                                }
//                                System.out.println("Got Steiner Point: " + xCoord + " " + yCoord);
//                            }
//                        }
//                    }
//                    if (geoEdge != null) {
//                        geoGraph.addEdge(geoEdge);
//                    }
//                } else {
//                    cnt++;
//                }
//            }
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//
//
//    }
}

package org.onosproject.mapreduce.util;

import com.google.common.collect.Maps;
import org.onosproject.mapreduce.profile.SensorType;
import org.onosproject.mapreduce.topology.RSSILinkWeight;
import org.onosproject.net.Path;
import org.onosproject.net.SensorNode;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.sensor.SensorNodeStore;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.TopologyService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Created by aca on 9/7/15.
 */
public final class MapReduceDeployer {
    private static MapReduceDeployer mapReduceDeployer = null;

    private final String path = "/home/user/";
    private final String command = path + "calculate_optimal_cost";
    private final String outputFilename = path + "output.dat";

    private SensorNodeService sensorNodeService;
    private SensorNodeStore sensorNodeStore;
    private TopologyService topologyService;
    private double[] costMatrix;
    private int nofNodes;

    // We keep the sensor nodes in this table to maintain order
    private SensorNode[] tmpForOptim;

    public static MapReduceDeployer getInstance(SensorNodeService sensorNodeService, SensorNodeStore sensorNodeStore,
                                                TopologyService topologyService) {
        if (mapReduceDeployer == null) {
            mapReduceDeployer = new MapReduceDeployer(sensorNodeService, sensorNodeStore, topologyService);
        }
        return mapReduceDeployer;
    }

    protected MapReduceDeployer(SensorNodeService sensorNodeService, SensorNodeStore sensorNodeStore,
                                TopologyService topologyService) {
        this.sensorNodeService = sensorNodeService;
        this.sensorNodeStore = sensorNodeStore;
        this.topologyService = topologyService;

        buildCostMatrix();
    }

    private void buildCostMatrix() {
        nofNodes = sensorNodeService.getSensorNodeCount();
        tmpForOptim = new SensorNode[nofNodes];
        Iterable<SensorNode> sensorNodes = sensorNodeService.getSensorNodes();
        int nodeIndex = 0;
        if (sensorNodes != null) {
            for (SensorNode node : sensorNodes) {
                tmpForOptim[nodeIndex++] = node;
            }
        }

        LinkWeight linkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore,
                RSSILinkWeight.SMALL_OF_LINK_WEIGHT);
//        LinkWeight linkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore,
//                RSSILinkWeight.BIG_OF_LINK_WEIGHT);

        int nofKeys = SensorType.values().length;
        costMatrix = new double[nofKeys * nofNodes];
        Arrays.fill(costMatrix, 0);

        double[][] tmpCostMatrix = new double[nofNodes][nofNodes];
        for (int i = 0; i < nofNodes; i++) {
            SensorNode src = tmpForOptim[i];
            for (int j = i; j < nofNodes; j++) {
                SensorNode dst = tmpForOptim[j];
                if (i == j) {
                    tmpCostMatrix[i][j] = 0;
                } else {
                    if (src.equals(dst)) {
                        tmpCostMatrix[i][j] = 0;
                    } else {
                        Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                                src.deviceId(), dst.deviceId(), linkWeight);
                        if (paths.size() == 0) {
                            System.out.println("No PATH found from " + Arrays.toString(src.addr())
                                    + " to " + Arrays.toString(dst.addr()));
                            tmpCostMatrix[i][j] = Double.MAX_VALUE;
                        } else {
                            Path path = paths.iterator().next();
                            tmpCostMatrix[i][j] = BigDecimal.valueOf(path.cost())
                                    .setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
//                            System.out.println("Path from " + Arrays.toString(src.nodeAddress().getAddr())
//                                    + " to " + Arrays.toString(dst.addr())
//                                    + " has cost " + tmpCostMatrix[i][j]);
                        }
                    }
                }
                tmpCostMatrix[j][i] = tmpCostMatrix[i][j];
            }
        }

//        System.out.println("Initialized");
        for (int k = 0; k < nofKeys; k++) {
            for (int j = 0; j < nofNodes; j++) {
                SensorNode dst = tmpForOptim[j];
                int l = k * nofNodes + j;
                for (int i = 0; i < nofNodes; i++) {
                    costMatrix[l] += tmpCostMatrix[i][j];
                }
                SensorNode sink = dst.associatedSink();
                if (!dst.equals(sink)) {
                    Set<Path> reducerToSinkPaths = topologyService.getPaths(topologyService.currentTopology(),
                            dst.deviceId(), sink.deviceId(), linkWeight);
                    if (reducerToSinkPaths.size() == 0) {
                        System.out.println("No PATH found from " + Arrays.toString(dst.addr())
                                + " to " + Arrays.toString(sink.addr()));
                        costMatrix[l] = Double.MAX_VALUE;
                    } else {
                        Path reducerToSinkPath = reducerToSinkPaths.iterator().next();
                        costMatrix[l] += BigDecimal.valueOf(reducerToSinkPath.cost())
                                .setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
                    }
                }
            }
        }

//        System.out.println("Cost matrix built");
    }

    public Map<SensorType, SensorNode> getReducersDeployment() {
        String costFilename = path + "cost.dat";
        int nofKeys = SensorType.values().length;
        Map<SensorType, SensorNode> reducersDeployment = Maps.newHashMap();

        File file = new File(outputFilename);
        if (!file.exists()) {
            String matrix = "";
            for (int i = 0; i < costMatrix.length; i++) {
                if (i != 0) {
                    matrix = matrix + ",";
                }
                double val = BigDecimal.valueOf(costMatrix[i]).setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
                matrix = matrix + val;
//                System.out.println("Writing " + val + " to matrix");
            }
            // save the matrix to a file
            try {
                FileOutputStream fout = new FileOutputStream(costFilename);
                fout.write(matrix.getBytes());
                fout.flush();
                fout.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String fullCommand = command + " " + nofNodes + " " + nofKeys + " " + costFilename;
            executeCommand(fullCommand);

        }

        int[][] configuration = readConfiguration(outputFilename);

        for (int k = 0; k < nofKeys; k++) {
            for (int i = 0; i < nofNodes; i++) {
                if (configuration[k][i] != 0) {
                    SensorNode reducer = tmpForOptim[i];
                    SensorType key = SensorType.getSensorType((byte) (k + 1));
                    reducersDeployment.put(key, reducer);
                }
            }
        }

        return reducersDeployment;
    }

    public SensorNode getNodeAtPosition(int pos) {
        return tmpForOptim[pos];
    }

    public int getPosForSensorNode(SensorNode sensorNode) {
        for (int i = 0; i < tmpForOptim.length; i++) {
            if (tmpForOptim[i].equals(sensorNode)) {
                return i;
            }
        }

        return -1;
    }

    private void executeCommand(String command) {
//        System.out.println("Executing command " + command);
        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int[][] readConfiguration(String outputFilename) {
        int nofKeys = SensorType.values().length;
        int[][] configuration = new int[nofKeys][nofNodes];
        int k = 0;
        int j = 0;
        try {
            BufferedReader br = new BufferedReader(new FileReader(outputFilename));
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                for (String str : values) {
                    configuration[k][j] = Integer.parseInt(str);
                    j = (j + 1) % nofNodes;
                    if (j == 0) {
                        k = (k + 1) % nofKeys;
                    }
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return configuration;
    }
}

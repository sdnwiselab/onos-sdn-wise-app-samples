package org.onosproject.mapreduce.stats;

import org.onosproject.mapreduce.util.DevicesPair;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Path;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by aca on 10/5/15.
 */
public class PathCostStatistics {
    private static PathCostStatistics pathCostStatistics = null;

    private Map<DevicesPair, Double> cost;
    private String logFileName;

    protected PathCostStatistics() {
        cost = new HashMap<>();
        logFileName = "path_cost_" + System.currentTimeMillis() + ".log";
    }

    public static PathCostStatistics getInstance() {
        if (pathCostStatistics == null) {
            pathCostStatistics = new PathCostStatistics();
        }
        return pathCostStatistics;
    }

    public synchronized PathCostStatistics update(final Path path) {
        DevicesPair devicesPair = new DevicesPair(path.src().deviceId(), path.dst().deviceId());
        cost.put(devicesPair, path.cost());

        return this;
    }

    public Double getCost(DevicesPair devicesPair) {
        return cost.get(devicesPair);
    }

    public Double getCost(DeviceId dev1, DeviceId dev2) {
        DevicesPair devicesPair = new DevicesPair(dev1, dev2);
        return cost.get(devicesPair);
    }

    public synchronized void log() {
        try {
            FileOutputStream fout = new FileOutputStream(logFileName, false);
            double totalCost = cost.entrySet().stream().mapToDouble(value -> value.getValue()).sum();
            String str = "" + totalCost + "\n";
            fout.write(str.getBytes());
            fout.flush();
            fout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

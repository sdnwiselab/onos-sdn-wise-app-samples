package org.onosproject.mapreduce.stats;

import org.onosproject.mapreduce.util.DevicesPair;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Created by aca on 10/5/15.
 */
public class PathHopsStatistics {
    private static PathHopsStatistics pathHopsStatistics = null;
    private final int sensorBandwidth = 256;
    private final int switchBandwidth = 102400;
    private final int avgPktSize = 15;

    private Map<DevicesPair, Integer> hopCount;
    private Map<DevicesPair, Double> delay;
    private String hopsLogFileName;
    private String delayLogFileName;
    private String allDelaysLogFileName;

    Predicate<Link> switchDst = link -> link.dst().deviceId().toString().startsWith("of");
    Predicate<Link> sensorDst = link -> link.dst().deviceId().toString().startsWith("sdnwise");

    protected PathHopsStatistics() {
        hopCount = new HashMap<>();
        delay = new HashMap<>();
        long curTime = System.currentTimeMillis();
        hopsLogFileName = "path_hops_" + curTime + ".log";
        delayLogFileName = "path_delay_" + curTime + ".log";
        allDelaysLogFileName = "delay_" + curTime + ".log";
    }

    public static PathHopsStatistics getInstance() {
        if (pathHopsStatistics == null) {
            pathHopsStatistics = new PathHopsStatistics();
        }
        return pathHopsStatistics;
    }

    public synchronized PathHopsStatistics update(final Path path) {
        DevicesPair devicesPair = new DevicesPair(path.src().deviceId(), path.dst().deviceId());
        hopCount.put(devicesPair, path.links().size());

        double switchDelay = path.links().stream().filter(switchDst)
                .mapToDouble(link -> ((double) avgPktSize) / (double) switchBandwidth).sum();
        double sensorDelay = path.links().stream().filter(sensorDst)
                .mapToDouble(link -> ((double) avgPktSize) / (double) sensorBandwidth).sum();
        double totalDelay = switchDelay + sensorDelay;

        delay.put(devicesPair, totalDelay);

        return this;
    }

    public int getHopCount(DevicesPair devicesPair) {
        return hopCount.get(devicesPair);
    }

    public int getHopCount(DeviceId dev1, DeviceId dev2) {
        DevicesPair devicesPair = new DevicesPair(dev1, dev2);
        return hopCount.get(devicesPair);
    }

    public synchronized void log() {
        try {
            FileOutputStream fout = new FileOutputStream(hopsLogFileName, false);
            int totalCost = hopCount.entrySet().stream().mapToInt(value -> value.getValue()).sum();
            String str = "" + totalCost + "\n";
            fout.write(str.getBytes());
            fout.flush();
            fout.close();

            FileOutputStream fout1 = new FileOutputStream(delayLogFileName, false);
            double totalDelay = delay.entrySet().stream().mapToDouble(value -> value.getValue()).sum();
            str = "" + totalDelay + "\n";
            fout1.write(str.getBytes());
            fout1.flush();
            fout1.close();

            FileOutputStream fout2 = new FileOutputStream(allDelaysLogFileName, false);
            delay.entrySet().forEach(devicePairDelay -> {
                DevicesPair devicesPair = devicePairDelay.getKey();
                String delayString = devicesPair.toString() + " " + devicePairDelay.getValue() + "\n";
                try {
                    fout2.write(delayString.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            fout2.flush();
            fout2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

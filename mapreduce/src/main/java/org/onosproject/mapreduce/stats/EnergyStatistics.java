package org.onosproject.mapreduce.stats;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.onosproject.net.SensorNode;
import org.onosproject.net.sensor.SensorNodeStore;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Created by aca on 10/3/15.
 */
public class EnergyStatistics {
    private static EnergyStatistics energyStatistics = null;
    private SensorNodeStore sensorNodeStore;
    private Multimap<SensorNode, Float> energyLevel;
    private String logFileName;

    protected EnergyStatistics(SensorNodeStore sensorNodeStore) {
        energyLevel = ArrayListMultimap.create();
        this.sensorNodeStore = sensorNodeStore;
        logFileName = "energy_" + System.currentTimeMillis() + ".log";
    }

    public static EnergyStatistics getInstance(SensorNodeStore sensorNodeStore) {
        if (energyStatistics == null) {
            energyStatistics = new EnergyStatistics(sensorNodeStore);
        }

        return energyStatistics;
    }

    public synchronized EnergyStatistics updateNode(SensorNode sensorNode) {
        float currentEnergyLevel = sensorNodeStore.getSensorNodeBatteryLevel(sensorNode.id());
        energyLevel.put(sensorNode, currentEnergyLevel);

        return this;
    }

    public synchronized void log() {

        try {
            FileOutputStream fout = new FileOutputStream(logFileName, false);
            energyLevel.asMap().entrySet().stream().map(sensorNodeCollectionEntry -> sensorNodeCollectionEntry
                    .getKey().nodeAddress() + " " + sensorNodeCollectionEntry.getValue().stream().map(energy ->
                    energy.toString()).collect(Collectors.joining(" ")) + "\n").forEach(s1 -> {
                try {
                    fout.write(s1.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            fout.flush();
            fout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

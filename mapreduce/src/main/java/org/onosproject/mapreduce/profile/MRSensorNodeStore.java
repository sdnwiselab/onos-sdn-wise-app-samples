package org.onosproject.mapreduce.profile;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.onosproject.net.SensorNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by aca on 9/18/15.
 */
public final class MRSensorNodeStore {
    private static MRSensorNodeStore mrSensorNodeStore = null;
    private static Multimap<SensorNode, SensorType> keysPerSensor;

    protected MRSensorNodeStore() {
        keysPerSensor = ArrayListMultimap.create();
    }

    public static MRSensorNodeStore getInstance() {
        if (mrSensorNodeStore == null) {
            mrSensorNodeStore = new MRSensorNodeStore();
        }
        return mrSensorNodeStore;
    }

    public void addSensorTypeToNode(SensorNode sensorNode, SensorType sensorType) {
        if (!keysPerSensor.get(sensorNode).contains(sensorType)) {
            keysPerSensor.put(sensorNode, sensorType);
        }
    }

    public Collection<SensorType> getSensorTypes(SensorNode sensorNode) {
        return keysPerSensor.get(sensorNode);
    }

    public List<SensorNode> getSensorNodesForSensorType(SensorType sensorType) {
        List<SensorNode> sensorNodes = keysPerSensor.asMap().entrySet().stream().filter(entry -> entry.getValue()
                .contains(sensorType)).map(Map.Entry::getKey).collect(Collectors
                .toList());

        return sensorNodes;
    }
}

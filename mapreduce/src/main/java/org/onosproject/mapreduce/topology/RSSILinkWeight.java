package org.onosproject.mapreduce.topology;

import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SensorNodeId;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.sensor.SensorNodeStore;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.TopologyEdge;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by aca on 5/24/15.
 */
public class RSSILinkWeight implements LinkWeight {
    private final Logger log = getLogger(getClass());

    public static final double SMALL_OF_LINK_WEIGHT = 1.0;
    public static final double BIG_OF_LINK_WEIGHT = 1000.0;

    private SensorNodeService sensorNodeService;
    private SensorNodeStore sensorNodeStore;

    private double sensorOFSwitchLinkWeight = 1.0;

    Function<Integer, Double> distanceFromRssi = rssi -> rssi == null ? Double.MAX_VALUE :
            Math.pow(10, ((double) (255 - rssi)) / (double) 58);

    public RSSILinkWeight(SensorNodeService sensorNodeService, SensorNodeStore sensorNodeStore) {
        this.sensorNodeService = sensorNodeService;
        this.sensorNodeStore = sensorNodeStore;
    }

    public RSSILinkWeight(SensorNodeService sensorNodeService, SensorNodeStore sensorNodeStore,
                          double sensorOFSwitchLinkWeight) {
        this.sensorNodeService = sensorNodeService;
        this.sensorNodeStore = sensorNodeStore;
        this.sensorOFSwitchLinkWeight = sensorOFSwitchLinkWeight;
    }

    @Override
    public double weight(TopologyEdge edge) {
        Link link = edge.link();
        DeviceId srcDeviceId = link.src().deviceId();
        DeviceId dstDeviceId = link.dst().deviceId();

//        log.info("Checking link {} -> {}", link.src().deviceId(), link.dst().deviceId());

        SensorNode srcSensorNode = sensorNodeService.getSensorNode(srcDeviceId);
        SensorNode dstSensorNode = sensorNodeService.getSensorNode(dstDeviceId);

        if ((srcSensorNode == null) && (dstSensorNode == null)) {
            return SMALL_OF_LINK_WEIGHT;
        } else if ((srcSensorNode == null) || (dstSensorNode == null)) {
            return sensorOFSwitchLinkWeight;
        }

        Map<SensorNodeId, Integer> neighborhood = sensorNodeStore.getSensorNodeNeighbors(srcSensorNode.id());
        Integer rssi = neighborhood.get(dstSensorNode.id());

        if (rssi == null) {
            log.warn("Got no RSSI for connection {} - {}", srcSensorNode.nodeAddress(), dstSensorNode.nodeAddress());
        }
        double distance = BigDecimal.valueOf(distanceFromRssi.apply(rssi))
                .setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
//        log.info("Distance for link {} -> {} is {}",
//                srcSensorNode.nodeAddress(), dstSensorNode.nodeAddress(), distance);

        return distance;
    }
}

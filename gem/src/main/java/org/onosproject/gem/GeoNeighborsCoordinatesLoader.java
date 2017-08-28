package org.onosproject.gem;

import org.onosproject.gem.topology.GeoLocalizationAlgorithm;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SensorNodeId;
import org.onosproject.net.devicecontrol.DefaultDeviceControlRule;
import org.onosproject.net.devicecontrol.DefaultDeviceTreatment;
import org.onosproject.net.devicecontrol.DeviceControlRule;
import org.onosproject.net.devicecontrol.DeviceControlRuleService;
import org.onosproject.net.devicecontrol.DeviceTreatment;
import org.onosproject.net.sensor.SensorNodeAddress;
import org.onosproject.net.sensor.SensorNodeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by aca on 6/21/15.
 */
public class GeoNeighborsCoordinatesLoader {
    private SensorNodeService sensorNodeService;
    private DeviceControlRuleService deviceControlRuleService;

    public GeoNeighborsCoordinatesLoader(SensorNodeService sensorNodeService,
                                         DeviceControlRuleService deviceControlRuleService) {
        this.sensorNodeService = sensorNodeService;
        this.deviceControlRuleService = deviceControlRuleService;
    }

    public void loadNeighborsCoordinates(SensorNode dst) {
        List<SensorNode> neighbors = new ArrayList<>();
        Map<SensorNodeId, Integer> neighborhood = sensorNodeService.getSensorNodeNeighbors(dst.id());
        if ((neighborhood != null) && (neighborhood.size() > 0)) {
            for (SensorNodeId sensorNodeId : neighborhood.keySet()) {
                neighbors.add(sensorNodeService.getSensorNode(sensorNodeId));
            }
        }
        SensorNode sink = sensorNodeService.getSinks().get(0);
        SensorNodeAddress sinkAddr = new SensorNodeAddress((byte) sink.netId(), sink.addr());
        GeoLocalizationAlgorithm localizationAlgorithm = new GeoLocalizationAlgorithm();
        DeviceTreatment deviceTreatment = DefaultDeviceTreatment.builder().setNeighborsCoordinates(sinkAddr,
                neighbors, localizationAlgorithm).build();
        DeviceControlRule deviceControlRule = new DefaultDeviceControlRule(dst.deviceId(), deviceTreatment);

        deviceControlRuleService.applyDeviceControlRules(deviceControlRule);
    }
}

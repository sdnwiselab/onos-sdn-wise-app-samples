package org.onosproject.gem;

import org.onosproject.net.SensorNode;
import org.onosproject.net.devicecontrol.DefaultDeviceControlRule;
import org.onosproject.net.devicecontrol.DefaultDeviceTreatment;
import org.onosproject.net.devicecontrol.DeviceControlRule;
import org.onosproject.net.devicecontrol.DeviceControlRuleService;
import org.onosproject.net.devicecontrol.DeviceTreatment;
import org.onosproject.net.sensor.SensorNodeAddress;
import org.onosproject.net.sensor.SensorNodeService;

/**
 * Created by aca on 6/21/15.
 */
public class GeoCoordinatesLoader {
    private SensorNodeService sensorNodeService;
    private DeviceControlRuleService deviceControlRuleService;

    public GeoCoordinatesLoader(SensorNodeService sensorNodeService, DeviceControlRuleService
            deviceControlRuleService) {
        this.sensorNodeService = sensorNodeService;
        this.deviceControlRuleService = deviceControlRuleService;
    }

    public void loadGeoCoordinates(SensorNode dst, Double x, Double y, Double z) {
        SensorNode sink = sensorNodeService.getSinks().get(0);
        SensorNodeAddress sinkAddr = new SensorNodeAddress((byte) sink.netId(), sink.addr());
        DeviceTreatment deviceTreatment = DefaultDeviceTreatment.builder().setCoordinates(sinkAddr, x, y, z).build();
        DeviceControlRule deviceControlRule = new DefaultDeviceControlRule(dst.deviceId(), deviceTreatment);

        deviceControlRuleService.applyDeviceControlRules(deviceControlRule);
    }
}

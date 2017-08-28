package org.onosproject.gem;

import org.onosproject.net.SensorNode;
import org.onosproject.net.devicecontrol.DefaultDeviceControlRule;
import org.onosproject.net.devicecontrol.DefaultDeviceTreatment;
import org.onosproject.net.devicecontrol.DeviceControlRule;
import org.onosproject.net.devicecontrol.DeviceControlRuleService;
import org.onosproject.net.devicecontrol.DeviceTreatment;
import org.onosproject.net.sensor.SensorNodeAddress;
import org.onosproject.net.sensor.SensorNodeService;

import java.net.URI;

/**
 * Created by aca on 6/21/15.
 */
public class GeoNxHopFunctionLoader {
    private SensorNodeService sensorNodeService;
    private DeviceControlRuleService deviceControlRuleService;

    public GeoNxHopFunctionLoader(SensorNodeService sensorNodeService,
                                  DeviceControlRuleService deviceControlRuleService) {
        this.sensorNodeService = sensorNodeService;
        this.deviceControlRuleService = deviceControlRuleService;
    }

    public void loadFunction(URI functionLocation, URI functionCallback, SensorNode dst) {
        SensorNode sink = sensorNodeService.getSinks().get(0);
        SensorNodeAddress sinkAddr = new SensorNodeAddress((byte) sink.netId(), sink.addr());
        DeviceTreatment deviceTreatment = DefaultDeviceTreatment.builder().installFunction(sinkAddr,
                functionLocation, functionCallback).build();
        DeviceControlRule deviceControlRule = new DefaultDeviceControlRule(dst.deviceId(), deviceTreatment);

        deviceControlRuleService.applyDeviceControlRules(deviceControlRule);
    }
}

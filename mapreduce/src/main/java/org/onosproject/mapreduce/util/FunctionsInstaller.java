package org.onosproject.mapreduce.util;

import org.onosproject.core.ApplicationId;
import org.onosproject.mapreduce.function.MapFunction;
import org.onosproject.mapreduce.function.ReduceFunction;
import org.onosproject.mapreduce.function.ToUdp;
import org.onosproject.net.SensorNode;
import org.onosproject.net.devicecontrol.DefaultDeviceControlRule;
import org.onosproject.net.devicecontrol.DefaultDeviceTreatment;
import org.onosproject.net.devicecontrol.DeviceControlRule;
import org.onosproject.net.devicecontrol.DeviceControlRuleService;
import org.onosproject.net.devicecontrol.DeviceTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.sensor.SensorNodeAddress;

import java.net.URI;
import java.net.URISyntaxException;

import static org.onosproject.mapreduce.protocol.MapReduceFunctionType.MAP_FUNCTION;
import static org.onosproject.mapreduce.protocol.MapReduceFunctionType.PROTO_ENCAPSULATION_FUNCTION;
import static org.onosproject.mapreduce.protocol.MapReduceFunctionType.REDUCE_FUNCTION;

/**
 * Created by aca on 9/11/15.
 */
public final class FunctionsInstaller {
    private DeviceControlRuleService deviceControlRuleService;
    private FlowRuleService flowRuleService;
    private ApplicationId callerId;

    public FunctionsInstaller(DeviceControlRuleService deviceControlRuleService, FlowRuleService flowRuleService,
                              ApplicationId callerId) {
        this.deviceControlRuleService = deviceControlRuleService;
        this.flowRuleService = flowRuleService;
        this.callerId = callerId;
    }

    public void installMapFunction(SensorNode sensorNode) {
        URI functionLocation = null;
        URI functionCallback = null;
        try {
//            functionLocation = new URI("file", "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0" +
//                    ".2/MapFunction.class", null);
            functionLocation = MapFunction.class.getResource("MapFunction.class").toURI();
            functionCallback = new URI("" + MAP_FUNCTION.functionId());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        installFunction(sensorNode, functionLocation, functionCallback);
    }

    public void installReduceFunction(SensorNode sensorNode) {
        URI functionLocation = null;
        URI functionCallback = null;
        try {
//            functionLocation = new URI("file", "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0" +
//                    ".2/ReduceFunction.class", null);
            functionLocation = ReduceFunction.class.getResource("ReduceFunction.class").toURI();
            functionCallback = new URI("" + REDUCE_FUNCTION.functionId());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        installFunction(sensorNode, functionLocation, functionCallback);
    }

    public void installProtoEncapsulationFunction(SensorNode sensorNode) {
        URI functionLocation = null;
        URI functionCallback = null;
        try {
//            functionLocation = new URI("file", "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0" +
//                    ".2/ToUdp.class", null);
            functionLocation = ToUdp.class.getResource("ToUdp.class").toURI();
            functionCallback = new URI("" + PROTO_ENCAPSULATION_FUNCTION.functionId());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        installFunction(sensorNode, functionLocation, functionCallback);
    }

    private void installFunction(SensorNode sensorNode, URI functionLocation, URI functionCallback) {
        SensorNode sinkNode = sensorNode.associatedSink();
        SensorNodeAddress sinkNodeAddress = new SensorNodeAddress((byte) sinkNode.netId(), sinkNode.addr());
        DeviceTreatment deviceTreatment = DefaultDeviceTreatment.builder()
                .installFunction(sinkNodeAddress, functionLocation, functionCallback).build();
        DeviceControlRule deviceControlRule = new DefaultDeviceControlRule(sensorNode.deviceId(), deviceTreatment);
        deviceControlRuleService.applyDeviceControlRules(deviceControlRule);

//        SDNWiseTrafficSelector sdnWiseTrafficSelector =
//                (SDNWiseTrafficSelector) SDNWiseEnabledTrafficSelector.builder()
//                        .matchSensorPacketType(SDNWiseMessageType.MULTICAST_DATA)
//                        .matchSensorNodeMutlicastCurHop(sensorNode.nodeAddress(), NOT_EQUAL)
//                        .build();
//
//        SDNWiseTrafficTreatment sdnWiseTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                .setForwardFunction(Byte.valueOf(functionCallback.toString()).byteValue(), false)
//                .buildSensorFlow();
//
//        FlowRule flowRule = new DefaultFlowRule(sensorNode.deviceId(), sdnWiseTrafficSelector,
//                sdnWiseTrafficTreatment, 10, callerId, 10, false);
//        flowRuleService.applyFlowRules(flowRule);
    }
}

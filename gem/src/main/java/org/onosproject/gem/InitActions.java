package org.onosproject.gem;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Path;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SensorNodeId;
import org.onosproject.net.SensorNodeLocalization;
import org.onosproject.net.devicecontrol.DefaultDeviceControlRule;
import org.onosproject.net.devicecontrol.DefaultDeviceTreatment;
import org.onosproject.net.devicecontrol.DeviceControlRule;
import org.onosproject.net.devicecontrol.DeviceControlRuleService;
import org.onosproject.net.devicecontrol.DeviceTreatment;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.sensor.SensorNodeAddress;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.sensorflow.SensorEnabledTrafficTreatment;
import org.onosproject.net.sensorflow.SensorEnabledTrafficSelector;
import org.onosproject.net.sensorflow.SensorTrafficSelector;
import org.onosproject.net.sensorflow.SensorTrafficTreatment;
import org.onosproject.net.topology.TopologyService;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.onosproject.net.sensorflow.SensorFlowCriterion.SensorNodeCriterionMatchType.EQUAL;
import static org.onosproject.net.sensorflow.SensorFlowCriterion.SensorNodeCriterionMatchType.NOT_EQUAL;

/**
 * Created by aca on 7/7/15.
 */
public class InitActions {
    private DeviceControlRuleService deviceControlRuleService;
    private TopologyService topologyService;
    private FlowRuleService flowRuleService;
    private SensorNodeService sensorNodeService;
    private ApplicationId callerId;
    private SensorNode sinkNode;
    private SensorNode sensorNode;
    private DeviceId sinkDeviceId;
    private DeviceId sensorNodeDeviceId;
    private SensorNodeLocalization sensorNodeLocalization;


    public InitActions(DeviceControlRuleService deviceControlRuleService, TopologyService topologyService,
                       FlowRuleService flowRuleService, SensorNodeService sensorNodeService, ApplicationId callerId,
                       SensorNode sinkNode, SensorNode sensorNode, SensorNodeLocalization sensorNodeLocalization) {
        this.deviceControlRuleService = deviceControlRuleService;
        this.topologyService = topologyService;
        this.flowRuleService = flowRuleService;
        this.sensorNodeService = sensorNodeService;
        this.callerId = callerId;
        this.sinkNode = sinkNode;
        this.sensorNode = sensorNode;
        this.sensorNodeLocalization = sensorNodeLocalization;

        // Open the path from the sink to the new node
        sinkDeviceId = DeviceId.deviceId(sinkNode.id().uri("sdnwise"));
        sensorNodeDeviceId = DeviceId.deviceId(sensorNode.id().uri("sdnwise"));

        if (!sinkDeviceId.equals(sensorNodeDeviceId)) {
            Path path = topologyService
                    .getPaths(topologyService.currentTopology(), sinkDeviceId, sensorNodeDeviceId).iterator().next();

            SensorTrafficSelector sensorTrafficSelector =
                    (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                            .matchNodeSrcAddr(new SensorNodeAddress((byte) sinkNode.netId(), sinkNode.addr()))
                            .matchNodeDstAddr(new SensorNodeAddress((byte) sensorNode.netId(), sensorNode.addr()))
                            .build();
            SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .setOpenPath(path)
                    .buildSensorFlow();

//            SDNWiseTrafficTreatment sdnWiseTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                    .setForwardFunction((byte) 1, true)
//                    .buildSensorFlow();

            FlowRule flowRule = new DefaultFlowRule(sensorNode.deviceId(), sensorTrafficSelector,
                    sensorTrafficTreatment, 10, callerId, 10, false);
            flowRuleService.applyFlowRules(flowRule);
        }

    }

    public void installFunction(URI functionLocation, URI functionCallback) {
        SensorNodeAddress sinkNodeAddress = new SensorNodeAddress((byte) sinkNode.netId(), sinkNode.addr());
        DeviceTreatment deviceTreatment = DefaultDeviceTreatment.builder()
                .installFunction(sinkNodeAddress, functionLocation, functionCallback).build();
        DeviceControlRule deviceControlRule = new DefaultDeviceControlRule(sensorNodeDeviceId, deviceTreatment);
        deviceControlRuleService.applyDeviceControlRules(deviceControlRule);

//        SDNWiseTrafficSelector sdnWiseTrafficSelector =
//                (SDNWiseTrafficSelector) SDNWiseEnabledTrafficSelector.builder()
//                .matchSensorNodeMutlicastCurHop(sensorNode.nodeAddress(), NOT_EQUAL)
//                .matchSensorPacketType(SDNWiseMessageType.MULTICAST_DATA)
//                .build();
//
//        SDNWiseTrafficTreatment sdnWiseTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                .setForwardFunction(Byte.valueOf(functionCallback.toString()).byteValue(), false)
//                .buildSensorFlow();
//
//        FlowRule flowRule = new DefaultFlowRule(sensorNode.deviceId(), sdnWiseTrafficSelector,
//                sdnWiseTrafficTreatment, 10, callerId, 10, false);
//        flowRuleService.applyFlowRules(flowRule);

        SensorTrafficSelector sensorTrafficSelector =
                (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                        .matchSensorPacketType(GemPacketType.MULTICAST_CTRL_DATA.getSensorPacketType())
                        .matchSensorNodeMutlicastPrevHop(sensorNode.nodeAddress(), EQUAL)
                        .build();

        SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                .setForwardFunction(Byte.valueOf(functionCallback.toString()).byteValue(), false)
                .buildSensorFlow();

        FlowRule flowRule = new DefaultFlowRule(sensorNode.deviceId(), sensorTrafficSelector,
                sensorTrafficTreatment, 10, callerId, 10, false);
        flowRuleService.applyFlowRules(flowRule);

        sensorTrafficSelector =
                (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                        .matchSensorPacketType(GemPacketType.MULTICAST_DATA.getSensorPacketType())
                        .matchSensorNodeMutlicastCurHop(sensorNode.nodeAddress(), NOT_EQUAL)
                        .build();

        flowRule = new DefaultFlowRule(sensorNode.deviceId(), sensorTrafficSelector,
                sensorTrafficTreatment, 10, callerId, 10, false);
        flowRuleService.applyFlowRules(flowRule);
    }

    public void setCoordinates() {
//        SensorNodeLocalization sensorNodeLocalization = new BiasedLocalizationAlgorithm();
//        SensorNodeLocalization sensorNodeLocalization = new GeoLocalizationAlgorithm();
        double[] coords = sensorNode.xyzCoordinates(sensorNodeLocalization);
        SensorNodeAddress sinkNodeAddress = new SensorNodeAddress((byte) sinkNode.netId(), sinkNode.addr());
        DeviceTreatment deviceTreatment = DefaultDeviceTreatment
                .builder().setCoordinates(sinkNodeAddress, coords[0], coords[1], coords[2]).build();
        DeviceControlRule deviceControlRule = new DefaultDeviceControlRule(sensorNodeDeviceId, deviceTreatment);
        deviceControlRuleService.applyDeviceControlRules(deviceControlRule);
    }

    public void setNeighborCoordinates() {
//        SensorNodeLocalization sensorNodeLocalization = new BiasedLocalizationAlgorithm();
//        SensorNodeLocalization sensorNodeLocalization = new GeoLocalizationAlgorithm();
        Map<SensorNodeId, Integer> neighborhood = sensorNodeService.getSensorNodeNeighbors(sensorNode.id());
        List<SensorNode> neighbors = new ArrayList<>();
        if ((neighborhood != null) && (neighborhood.size() > 0)) {
            neighborhood.forEach((id, rssi) -> neighbors.add(sensorNodeService.getSensorNode(id)));
        }

        SensorNodeAddress sinkNodeAddress = new SensorNodeAddress((byte) sinkNode.netId(), sinkNode.addr());
        DeviceTreatment deviceTreatment = DefaultDeviceTreatment
                .builder().setNeighborsCoordinates(sinkNodeAddress, neighbors, sensorNodeLocalization).build();
        DeviceControlRule deviceControlRule = new DefaultDeviceControlRule(sensorNodeDeviceId, deviceTreatment);
        deviceControlRuleService.applyDeviceControlRules(deviceControlRule);
    }
}

package org.onosproject.mapreduce;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mapreduce.profile.MRSensorNodeStore;
import org.onosproject.mapreduce.profile.SensorType;
import org.onosproject.mapreduce.protocol.MapFunctionTriggerPacket;
import org.onosproject.mapreduce.protocol.MapPacket;
import org.onosproject.mapreduce.protocol.MapReduceFunctionType;
import org.onosproject.mapreduce.stats.EnergyStatistics;
import org.onosproject.mapreduce.stats.PathCostStatistics;
import org.onosproject.mapreduce.stats.PathHopsStatistics;
import org.onosproject.mapreduce.topology.RSSILinkWeight;
import org.onosproject.mapreduce.util.FunctionsInstaller;
import org.onosproject.mapreduce.util.MapReduceDeployer;
import org.onosproject.net.Annotations;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SparseAnnotations;
import org.onosproject.net.devicecontrol.DeviceControlRuleService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.sensor.DefaultSensorNodeDescription;
import org.onosproject.net.sensor.SensorNodeDesciption;
import org.onosproject.net.sensor.SensorNodeEvent;
import org.onosproject.net.sensor.SensorNodeListener;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.sensor.SensorNodeStore;
import org.onosproject.net.sensorflow.SensorEnabledTrafficSelector;
import org.onosproject.net.sensorflow.SensorEnabledTrafficTreatment;
import org.onosproject.net.sensorflow.SensorTrafficSelector;
import org.onosproject.net.sensorflow.SensorTrafficTreatment;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.onosproject.mapreduce.protocol.MapPacket.KEY_BYTE_POS;
import static org.onosproject.mapreduce.protocol.MapPacket.VALUE_BYTE_POS;
import static org.onosproject.mapreduce.protocol.MapReduceFunctionType.REDUCE_FUNCTION;
import static org.onosproject.mapreduce.protocol.MapReducePacketType.MAP_DATA;
import static org.onosproject.mapreduce.protocol.MapReducePacketType.MAP_FUNCTION_TRIGGER;
import static org.onosproject.mapreduce.protocol.MapReducePacketType.REDUCE_DATA;
import static org.onosproject.net.sensorflow.SensorFlowCriterion.SensorNodeCriterionMatchType.EQUAL;
import static org.onosproject.net.sensorflow.SensorFlowCriterion.SensorNodeCriterionMatchType.NOT_EQUAL;
import static org.onosproject.net.sensorflow.SensorFlowInstruction.Operator.ADD;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by aca on 9/11/15.
 */
@Component(immediate = true)
public final class MapReduceInit {
    private final Logger log = getLogger(getClass());
    public static final String MR_KEY_PREFIX = "MR_KEY";

    private static final int TIMEOUT = 10;
    private static final int PRIORITY = 10;

//    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
//    protected PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceControlRuleService deviceControlRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SensorNodeService sensorNodeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SensorNodeStore sensorNodeStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    private ApplicationId appId;

    private List<SensorNode> initializedNodes;
    private MapReduceDeployer mapReduceDeployer;
    private int[] keysPerReducer;
    Function<SensorNode, List<SensorType>> mrSensorNode;

    Predicate<DeviceId> isSensorNode = deviceId -> sensorNodeService.getSensorNode(deviceId) != null;
    Predicate<Link> linkContainsOnlySensors = link -> isSensorNode.test(link.src().deviceId()) ?
            isSensorNode.test(link.dst().deviceId()) : false;
    Predicate<Path> sensorsPath = path ->
            path.links().stream().filter(linkContainsOnlySensors).count() == path.links().size();
    Comparator<Path> minCost = (o1, o2) -> Double.compare(o1.cost(), o2.cost());
    Function<Set<Path>, Path> findBestPath = paths -> paths.stream().filter(sensorsPath).min(minCost).get();

    private Map<SensorType, SensorNode> reducersDeployment;
    private RSSILinkWeight rssiLinkWeight;

    private Object eventLock = new Object();

    private EnergyStatistics energyStatistics;
    private PathHopsStatistics pathHopsStatistics;
    private PathCostStatistics pathCostStatistics;

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.onosproject.mapreduceinit");
        sensorNodeService.addListener(new InternalSensorNodeListener());
        initializedNodes = new ArrayList<>();
        mrSensorNode = sensorNode -> {
            List<SensorType> sensorTypes = new ArrayList<>();
            Annotations annotations = sensorNode.annotations();
            sensorTypes.addAll(annotations.keys().stream().filter(key -> key.startsWith(MapReduceInit.MR_KEY_PREFIX))
                    .map(key -> SensorType.getSensorType(
                            Byte.valueOf(annotations.value(key)))).collect(Collectors.toList()));

            return sensorTypes;
        };

        energyStatistics = EnergyStatistics.getInstance(sensorNodeStore);
        pathCostStatistics = PathCostStatistics.getInstance();
        pathHopsStatistics = PathHopsStatistics.getInstance();

        rssiLinkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore, RSSILinkWeight.SMALL_OF_LINK_WEIGHT);
//        rssiLinkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore, RSSILinkWeight.BIG_OF_LINK_WEIGHT);
        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        initializedNodes.clear();
        log.info("Stopped Application with ID {}", appId.id());
    }

    public class InternalSensorNodeListener implements SensorNodeListener {
        @Override
        public void event(SensorNodeEvent event) {
            synchronized (eventLock) {
                switch (event.type()) {
                    case SENSOR_ADDED: case SENSOR_UPDATED:
                        energyStatistics.updateNode(event.subject()).log();
                        // TODO: FIXME!!!
                        if (topologyService.currentTopology().linkCount() == 68) {
                            // If all nodes have been initialized, we can start
                            if (initializedNodes.size() < sensorNodeService.getSensorNodeCount()) {
                                log.info("Have latest topology version: {}",
                                        topologyService.isLatest(topologyService.currentTopology()));
//                                try {
//                                    Thread.sleep(10000);
//                                } catch (InterruptedException e) {
//                                    e.printStackTrace();
//                                }
                                // Get the reducers deployment
                                log.info("Preparing for Reducers deployment");
                                mapReduceDeployer = MapReduceDeployer.getInstance(sensorNodeService, sensorNodeStore,
                                        topologyService);
                                log.info("Deployer initialized");
                                reducersDeployment = mapReduceDeployer.getReducersDeployment();
                                if ((reducersDeployment == null) || reducersDeployment.size() == 0) {
                                    log.info("No deployment found!!!");
                                } else {
                                    log.info("Deployment done");
                                }

                                sensorNodeService.getSensorNodes().forEach(this::intializeSensorNode);
                                log.info("All nodes initialized");
                                log.info("{} has type {}", MAP_DATA, MAP_DATA.getSensorPacketType().originalId());
                                log.info("{} has type {}", REDUCE_DATA, REDUCE_DATA.getSensorPacketType().originalId());
                                log.info("{} has type {}", MAP_FUNCTION_TRIGGER,
                                        MAP_FUNCTION_TRIGGER.getSensorPacketType().originalId());
                                int nofNodes = initializedNodes.size();
                                keysPerReducer = new int[nofNodes];
                                Arrays.fill(keysPerReducer, 0);

                                // Setup the appropriate rules
                                reducersDeployment.forEach(this::setupReducers);
                                // Trigger map function on all nodes
                                initializedNodes.forEach(this::triggerMap);
                            }
                        } else {
                            log.info("Waiting for more sensors to come in. Currently have {}",
                                    sensorNodeService.getSensorNodeCount());
                        }
                        break;
                    default:
                }
            }
        }

        private void intializeSensorNode(SensorNode sensorNode) {
            FunctionsInstaller functionsInstaller = new FunctionsInstaller(
                    deviceControlRuleService, flowRuleService, appId);
            if (!initializedNodes.contains(sensorNode)) {
                // In case this is the sink, deploy the encapsulation function
                if (sensorNode.equals(sensorNode.associatedSink())) {
                    log.info("Deploying encapsulation function to node {}", sensorNode.deviceId());
                    functionsInstaller.installProtoEncapsulationFunction(sensorNode);
                }
                if (openPath(sensorNode)) {
                    // All sensor nodes are mappers
                    log.info("Updating sensor node {}", sensorNode.nodeAddress());
                    sensorNode = updateSensorNode(sensorNode, pickTypesAtRandom());
                    log.info("Installing map function to node {}", sensorNode.nodeAddress());
                    functionsInstaller.installMapFunction(sensorNode);
                    // Set a rule to ask the controller when the function generates a key-value pair
                    log.info("Setting up map function first execution to node {}", sensorNode.nodeAddress());
                    setupFunctionFirstExecution(sensorNode);
                    // Set the rule to trigger the map function
                    log.info("Deploying map function to node {}", sensorNode.nodeAddress());
                    setupMapFunctionCall(sensorNode);
                    log.info("Node {} initialized", sensorNode.nodeAddress());
                    initializedNodes.add(sensorNode);
                }
            }
        }

        private void triggerMap(SensorNode dst) {
            MapFunctionTriggerPacket triggerPacket = new MapFunctionTriggerPacket(mrSensorNode.apply(dst));

            SensorTrafficTreatment localTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .setPacketSrcAddress(dst.associatedSink().nodeAddress())
                    .setPacketDstAddress(dst.nodeAddress())
                    .setPacketType(MAP_FUNCTION_TRIGGER.getSensorPacketType())
                    .buildSensorFlow();

            OutboundPacket outboundPacket = new DefaultOutboundPacket(dst.deviceId(), localTrafficTreatment,
                    ByteBuffer.wrap(triggerPacket.serialize()));
            log.info("Sending back packet with type {}", MAP_FUNCTION_TRIGGER.getSensorPacketType());
            packetService.emit(outboundPacket);
        }

        private boolean openPath(SensorNode dst) {
            SensorNode sink = dst.associatedSink();
            if (!dst.equals(sink)) {
                log.info("Discovering path from {} to {}", Arrays.toString(sink.addr()), Arrays.toString(dst.addr()));
                Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                        sink.deviceId(), dst.deviceId(), rssiLinkWeight);

                if ((paths != null) && (paths.iterator().hasNext())) {
                    Path path = findBestPath.apply(paths);
                    pathHopsStatistics.update(path).log();
                    pathCostStatistics.update(path).log();
//                    SensorTrafficSelector sensorTrafficSelector =
//                            (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
//                                    .matchNodeSrcAddr(new SensorNodeAddress((byte) sink.netId(), sink.addr()))
//                                    .matchNodeDstAddr(new SensorNodeAddress((byte) dst.netId(), dst.addr()))
//                                    .build();
                    SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                            .setOpenPath(path)
                            .buildSensorFlow();

                    FlowRule flowRule = new DefaultFlowRule(path.src().deviceId(), null,
                            sensorTrafficTreatment, 10, appId, 10, false);
                    flowRuleService.applyFlowRules(flowRule);

                    return true;
                } else {
                    log.warn("No path found from {} to {}", Arrays.toString(sink.addr()), Arrays.toString(dst.addr()));
                }
            } else {
                return true;
            }

            return false;
        }

        private void setupMapFunctionCall(SensorNode dst) {
            SensorTrafficSelector trafficSelector =
                    (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                            .matchSensorPacketType(EQUAL, MAP_FUNCTION_TRIGGER.getSensorPacketType())
                            .matchNodeDstAddr(dst.nodeAddress(), EQUAL)
                            .build();

            SensorTrafficTreatment trafficTreatment =
                    (SensorTrafficTreatment) SensorEnabledTrafficTreatment.builder()
                            .setForwardFunction(MapReduceFunctionType.MAP_FUNCTION.functionId(), false)
                            .buildSensorFlow();

            FlowRule flowRule = new DefaultFlowRule(dst.deviceId(), trafficSelector, trafficTreatment,
                    10, appId, 10, false);
            flowRuleService.applyFlowRules(flowRule);
        }

        private void setupFunctionFirstExecution(SensorNode dst) {
            for (SensorType sensorType : mrSensorNode.apply(dst)) {
                SensorNode reducer = reducersDeployment.get(sensorType);

                SensorTrafficSelector trafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                        .matchSensorPacketType(EQUAL, MAP_DATA.getSensorPacketType())
                        .matchPacketFieldWithConst(MapPacket.KEY_BYTE_POS, sensorType.getValue(), 1, EQUAL)
                        .matchNodeDstAddr(reducer.nodeAddress(), NOT_EQUAL)
                        .build();

                SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                        .setPacketDstAddress(reducer.nodeAddress())
                        .reMatchPacket()
                        .buildSensorFlow();

                FlowRule flowRule = new DefaultFlowRule(dst.deviceId(), trafficSelector, sensorTrafficTreatment,
                        10, appId, 10, false);
                flowRuleService.applyFlowRules(flowRule);
            }
        }

        private SensorNode updateSensorNode(SensorNode sensorNode, List<SensorType> sensorTypes) {
            SparseAnnotations sensorTypeAnnotations = null;
            int i = 1;
            DefaultAnnotations.Builder annotationsBuilder = DefaultAnnotations.builder();
            for (SensorType sensorType : sensorTypes) {
                log.info("Adding sensor {} to node {}", sensorType, sensorNode.nodeAddress());
                MRSensorNodeStore.getInstance().addSensorTypeToNode(sensorNode, sensorType);
                annotationsBuilder = annotationsBuilder.set(MR_KEY_PREFIX + i, "" + sensorType.getValue());
                i++;
            }

            sensorTypeAnnotations = annotationsBuilder.build();

            SparseAnnotations annotations = sensorTypeAnnotations;
            if (sensorNode.annotations() != null) {
                annotations = DefaultAnnotations
                        .merge((DefaultAnnotations) sensorNode.annotations(), sensorTypeAnnotations);
            }

            SensorNodeDesciption sensorNodeDesciption = new DefaultSensorNodeDescription(sensorNode.mac(),
                    sensorNode.sinkMac(), sensorNode.sinkAddress(), sensorNode.sinkPort(),
                    sensorNode.sinkConnectionAddress(), sensorNode.sinkConnectionPort(), sensorNode.sinkLocation(),
                    sensorNode.netId(), sensorNode.addr(), sensorNodeStore.getSensorNodeNeighbors(sensorNode.id()),
                    sensorNodeStore.getSensorNodeBatteryLevel(sensorNode.id()), annotations);

            SensorNodeEvent sensorNodeEvent = sensorNodeStore.createOrUpdateSensorNode(sensorNode.providerId(),
                    sensorNode.id(), sensorNode.deviceId(), sensorNodeDesciption);

            return sensorNodeEvent.subject();
        }

        private void setupReducers(SensorType sensorType, SensorNode reducerNode) {
            log.info("Setting up node {} as reducer for key {}", Arrays.toString(reducerNode.addr()), sensorType);
            int reducerNodePos = mapReduceDeployer.getPosForSensorNode(reducerNode);
            byte key = sensorType.getValue();
            int keyMappers = MRSensorNodeStore.getInstance()
                    .getSensorNodesForSensorType(SensorType.getSensorType(key)).size();
            log.info("Found {} mappers for KEY {}", keyMappers, sensorType);
            int nofNodes = sensorNodeService.getSensorNodeCount();

            // Install the function
            FunctionsInstaller functionsInstaller = new FunctionsInstaller(
                    deviceControlRuleService, flowRuleService, appId);
            functionsInstaller.installReduceFunction(reducerNode);

            int valueSize = 1;
            int valueArraySize = valueSize * nofNodes;
            int valueArrBeginPos = 0;
            int valueArrEndPos = valueArrBeginPos + valueArraySize - 1;
            int arrivalsArrBeginPos = valueArrEndPos + 1;
            int arrivalSize = 1;
            int sumCellPos = arrivalsArrBeginPos + arrivalSize * nofNodes;
            int xorCellPos = sumCellPos + 1;
            int totalSizePerKey = xorCellPos + 1;

            int curValueArrBeginPos = totalSizePerKey * keysPerReducer[reducerNodePos];
            int curArrivalsArrBeginPos = curValueArrBeginPos + nofNodes * valueSize;
            int curSumCellPos = curArrivalsArrBeginPos + nofNodes * arrivalSize;
            int curXorCellPos = curSumCellPos + 1;

            log.info("Values: {} - {}", curValueArrBeginPos, (curArrivalsArrBeginPos - 1));
            log.info("Arrivals: {} - {}", curArrivalsArrBeginPos, (curSumCellPos - 1));
            log.info("SUM: {}", curSumCellPos);
            log.info("XOR: {}", curXorCellPos);

            keysPerReducer[reducerNodePos]++;

            SensorTrafficSelector trafficSelector;
            SensorTrafficTreatment trafficTreatment;
            FlowRule flowRule;

            List<SensorNode> mappers = MRSensorNodeStore.getInstance().getSensorNodesForSensorType(sensorType);
            for (SensorNode mapper : mappers) {
                int nodeIndex = mapReduceDeployer.getPosForSensorNode(mapper);

                // Install the rules to change the state
                trafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                        .matchSensorPacketType(MAP_DATA.getSensorPacketType())
                        .matchPacketFieldWithConst(KEY_BYTE_POS, key, 1, EQUAL)
                        .matchNodeSrcAddr(mapper.nodeAddress())
                        .matchPacketFieldWithConst(MapPacket.LAST_PAYLOAD_BYTE + 1, 1, 1, NOT_EQUAL)
                        .build();

                trafficTreatment = SensorEnabledTrafficTreatment.builder()
//                        .setStateValueWithOpConst(
//                                curArrivalsArrBeginPos + nodeIndex, curXorCellPos, 1, arrivalSize, XOR)
                        .setStateValueConst(curXorCellPos, arrivalSize, 1, false)
                        .setStateValueWithOpState(curSumCellPos, curXorCellPos, curSumCellPos, arrivalSize, ADD)
                        .setPacketValueAtPosConst(MapPacket.LAST_PAYLOAD_BYTE + 1, 1)
                        .setStateValuePacket(curValueArrBeginPos + nodeIndex, valueSize, VALUE_BYTE_POS + 3, false)
                        .setStateValueConst(curArrivalsArrBeginPos + nodeIndex, arrivalSize, 1, false)
                        .reMatchPacket()
                        .buildSensorFlow();

                flowRule = new DefaultFlowRule(reducerNode.deviceId(), trafficSelector, trafficTreatment,
                        PRIORITY, appId, TIMEOUT, false);
                flowRuleService.applyFlowRules(flowRule);
            }

            // Install the rules to call the function
            trafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                    .matchSensorPacketType(MAP_DATA.getSensorPacketType())
                    .matchStateConst(curSumCellPos, (curSumCellPos + 1), keyMappers, EQUAL)
                    .matchPacketFieldWithConst(MapPacket.KEY_BYTE_POS, key, 1, EQUAL)
                    .build();

            trafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .setForwardFunction(REDUCE_FUNCTION.functionId(), false,
                            new int[]{curValueArrBeginPos, nofNodes, REDUCE_DATA.getSensorPacketType()
                                    .originalId()})
                    .setStateValueConst(curSumCellPos, 1, 0, false)
                    .buildSensorFlow();

            flowRule = new DefaultFlowRule(reducerNode.deviceId(), trafficSelector, trafficTreatment,
                    PRIORITY, appId, TIMEOUT, false);
            flowRuleService.applyFlowRules(flowRule);

            // In case the reduce function cannot be called, drop the packet
            trafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                    .matchSensorPacketType(MAP_DATA.getSensorPacketType())
                    .matchPacketFieldWithConst(MapPacket.KEY_BYTE_POS, key, 1, EQUAL)
                    .matchStateConst(
                            curSumCellPos, (curSumCellPos + 1), keyMappers, NOT_EQUAL)
                    .matchPacketFieldWithConst(MapPacket.LAST_PAYLOAD_BYTE + 1, 1, 1, EQUAL)
                    .build();

            trafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .dropPacket(false)
                    .buildSensorFlow();

            flowRule = new DefaultFlowRule(reducerNode.deviceId(), trafficSelector, trafficTreatment,
                    PRIORITY, appId, TIMEOUT, false);
            flowRuleService.applyFlowRules(flowRule);


            // Install the rules to ask the controller when generating the reduce packet
            trafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                    .matchSensorPacketType(REDUCE_DATA.getSensorPacketType())
                    .build();

            trafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .askController(false)
                    .buildSensorFlow();

            flowRule = new DefaultFlowRule(reducerNode.deviceId(), trafficSelector, trafficTreatment,
                    PRIORITY, appId, TIMEOUT, false);
            flowRuleService.applyFlowRules(flowRule);
        }


        private List<SensorType> pickTypesAtRandom() {
            List<SensorType> sensorTypes = new ArrayList<>();
            int availableTypes = SensorType.values().length;
            Random random = new Random();

            int typesToPick = random.nextInt(availableTypes) + 1;
            int i = 0;
            while (i < typesToPick) {
                byte type = (byte) (random.nextInt(availableTypes) + 1);
                SensorType sensorType = SensorType.getSensorType(type);
                if (!sensorTypes.contains(sensorType)) {
                    sensorTypes.add(sensorType);
                    i++;
                }
            }

            return sensorTypes;
        }
    }
}

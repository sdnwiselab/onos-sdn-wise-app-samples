package org.onosproject.gem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.gem.topology.GeoPathManager;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SensorNodeId;
import org.onosproject.net.SensorNodeLocalization;
import org.onosproject.net.devicecontrol.DeviceControlRuleService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.multicast.Group;
import org.onosproject.net.multicast.GroupManagementService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.sensorflow.SensorEnabledTrafficTreatment;
import org.onosproject.net.sensorflow.SensorEnabledTrafficSelector;
import org.onosproject.net.sensorflow.SensorTrafficSelector;
import org.onosproject.net.sensorflow.SensorTrafficTreatment;
import org.onosproject.net.topology.DefaultTopologyEdge;
import org.onosproject.net.topology.DefaultTopologyVertex;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.onosproject.gem.GemPacketType.MULTICAST_CTRL_DATA;
import static org.onosproject.net.sensorflow.SensorFlowCriterion.SensorNodeCriterionMatchType.EQUAL;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by aca on 4/29/15.
 */
@Component(immediate = true)
public class GemForwarding {
    private static final int TIMEOUT = 10;
    private static final int PRIORITY = 10;

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SensorNodeService sensorNodeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupManagementService groupManagementService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceControlRuleService deviceControlRuleService;

//    private List<SensorNode> sensorNodesStats = new CopyOnWriteArrayList<>();
    private Map<SensorNode, Integer> rulesPerNode = new ConcurrentHashMap<>();
    private SensorNodeLocalization sensorNodeLocalization;

    private PacketProcessor packetProcessor = new GeographicMulticastPacketProcessor();
    private ApplicationId appId;

    private List<SensorNode> initializedNodes = new ArrayList<>();
    private URI functionCallback;
    private URI function2Callback;

    private double cost = 0;
    private long hopCount = 0;

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.onosproject.gem");
        packetService.addProcessor(packetProcessor, 11);

        BiasedLocalizationAlgorithm.maxErrorShift = 0.0;
        sensorNodeLocalization = new BiasedLocalizationAlgorithm();
//        sensorNodeLocalization = new GeoLocalizationAlgorithm();


        try {
            functionCallback =  new URI("1");
//            function2Callback =  new URI("2");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
//        init();
        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(packetProcessor);
        packetProcessor = null;

        try {
            FileOutputStream outputStream = new FileOutputStream("rules.log", false);
            rulesPerNode.forEach((sensorNode, rules) -> {
                String str = "" + sensorNode.addr()[1] + " " + rules + "\n";
                try {
                    outputStream.write(str.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            String hopString = "Hops " + hopCount + "\n";
            outputStream.write(hopString.getBytes());
            String costString = "Cost " + cost + "\n";
            outputStream.write(costString.getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Stopped Application with ID {}", appId.id());
    }

    // TODO: Only for Distributed Geographic
    private void init() {
        Group group = groupManagementService.groups().get(0);

//        List<SensorNode> multicastGroupNodes =
//                groupManagementService.sensorNodes(group.getId());

        Iterable<SensorNode> multicastGroupNodes = sensorNodeService.getSensorNodes();

//        List<SensorNode> multicastGroupNodes = new ArrayList<>();
//        Set<SensorNode> allNodes = sensorNodeService.getSensorNodesInNetwork(1);
//        Iterator<SensorNode> allNodesIterator = allNodes.iterator();
//        for (int i = 1; i < 16; i++) {
//            multicastGroupNodes.add(allNodesIterator.next());
//        }

        // find the sink
        SensorNode sink = sensorNodeService.getSinks().get(0);
        log.info("Sink is node {}", (sink != null ? sink.deviceId() : "NONE"));
        List<InitActions> initActionsList = new ArrayList<>();

        if (multicastGroupNodes != null) {
            for (SensorNode sensorNode : multicastGroupNodes) {
                if (!initializedNodes.contains(sensorNode)) {
                    log.info("Initializing actions for node {}", sensorNode.deviceId().uri());
                    InitActions initActions = new InitActions(
                            deviceControlRuleService, topologyService, flowRuleService, sensorNodeService,
                            appId, sink, sensorNode, sensorNodeLocalization);
                    initActionsList.add(initActions);
                    initializedNodes.add(sensorNode);
                }
            }

            for (InitActions initActions : initActionsList) {
                initActions.setCoordinates();
                initActions.setNeighborCoordinates();
                URI functionLocation = null;
                try {
                    functionLocation = new URI("file", "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0" +
                            ".2/GeoRouting.class", null);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                initActions.installFunction(functionLocation, functionCallback);
            }

        } else {
            log.info("No nodes found in group");
        }
    }

    private class GeographicMulticastPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt.getDestinationMAC().isMulticast()) {
                log.info("Processing packet {}", Arrays.toString(pkt.unparsed().array()));

                List<SensorNode> multicastGroupNodes =
                        groupManagementService.sensorNodes(ethPkt.getDestinationMAC());

                // find the sink
                SensorNode sink = sensorNodeService.getSinks().get(0);

                GeoPathManager geoPathManager = new GeoPathManager(sensorNodeService, topologyService,
                        multicastGroupNodes, sensorNodeLocalization);

                MacAddress prevMacAddress = ethPkt.getSourceMAC();
                DeviceId srcDeviceId = pkt.receivedFrom().deviceId();
                SensorNode prevSensorNode = null;
                SensorNode curSensorNode = null;
                Set<SensorNode> prevCandidates = sensorNodeService.getSensorNodesByMac(prevMacAddress);
                if ((prevCandidates != null) && (prevCandidates.size() > 0)) {
                    prevSensorNode = prevCandidates.iterator().next();
                }
                curSensorNode = sensorNodeService.getSensorNode(srcDeviceId);
//                SensorNode curSensorNode =
//                        sensorNodeService.getSensorNodesByMac(pkt.parsed().getSourceMAC()).iterator().next();


                // TODO: Geographic Distributed
//                deliverMulticastMessages(geoPathManager, sink, prevSensorNode, curSensorNode, pkt);

                // TODO: Geographic Centralized
                deliverMulticastMessagesOpenPath(geoPathManager, sink, prevSensorNode, curSensorNode, pkt);

                // TODO: Dijkstra
//                deliverMulticastMessagesPlain(geoPathManager, sink, prevSensorNode, curSensorNode, pkt);

//                if ((multicastGroupNodes != null) && (multicastGroupNodes.size() > 0)) {
//                    List<DeviceId> multicastGroup = new ArrayList<>();
//                    for (SensorNode multicastMember : multicastGroupNodes) {
//                        multicastGroup.add(multicastMember.deviceId());
//                    }
//
//
//                    GeoTopologyService geoTopologyService = new GeoTopologyManager(
//                            topologyService.getGraph(topologyService.currentTopology()), multicastGroup);
//                    GeoTopologyService geoTopologyService = GeoTopologyManager.getInstance(
//                            topologyService.getGraph(topologyService.currentTopology()), multicastGroup);

//                    List<GeoNode> geoCoordinates = geoTopologyService.geoNodes();
//                    if ((geoCoordinates != null) && (geoCoordinates.size() > 0)) {
//                        for (GeoNode coordinates : geoCoordinates) {
//                            log.info("{} = [{} {}]", coordinates.getDeviceId().toString(),
//                                    coordinates.getxCoord(), coordinates.getyCoord());
//                        }
//                    } else {
//                        log.info("No GeoCoordinates");
//                    }
//                    GeoGraph euclideanSteinerGraph = geoTopologyService.euclideanSteinerGraph();
//                    List<GeoEdge> geoEdges = euclideanSteinerGraph.getGeoEdges();
//                    if ((geoEdges != null) && (geoEdges.size() > 0)) {
//                        for (GeoEdge geoEdge : geoEdges) {
//                            GeoNode src = geoEdge.getSrc();
//                            GeoNode dst = geoEdge.getDst();
//                            log.info("Edge connecting [{} {}] - [{} {}]",
//                                    src.getxCoord(), src.getyCoord(),
//                                    dst.getxCoord(), dst.getyCoord());
//                        }
//                    }

//                    GeoNode cur = geoTopologyService.geoNode(pkt.receivedFrom().deviceId());
//                    deliverMulticastMessages(geoTopologyService, null, cur, pkt);
//                }
            }


        }

        private void deliverMulticastMessages(GeoPathManager geoPathManager, SensorNode sink,
                                              SensorNode prev, SensorNode cur, InboundPacket inboundPacket) {
            writeNodesStatsToFile();
            List<SensorNode> multicastNeighbors = geoPathManager.getNextMulticastHops(prev, cur);
            if ((multicastNeighbors != null) && (multicastNeighbors.size() > 0)) {
                for (SensorNode next : multicastNeighbors) {
                    if (!next.equals(cur)) {
                        log.info("Delivering from {} to {}", cur.deviceId(), next.deviceId());
//                        Path intermediateNodesPath = geoPathManager.getIntermediatePath(sink, cur, next);
//                        log.info("{}-->{}: {}", inboundPacket.receivedFrom().deviceId(), cur.deviceId(),
//                                intermediateNodesPath.toString());
//                        writePathsToFile(cur, next, intermediateNodesPath);
                        installRules(sink, cur, next, inboundPacket);
//                        deliverMulticastMessages(geoPathManager, sink, cur, next, inboundPacket);
                    }
                }
            }
        }

        private void deliverMulticastMessagesOpenPath(GeoPathManager geoPathManager, SensorNode sink,
                                              SensorNode prev, SensorNode cur, InboundPacket inboundPacket) {
            writeNodesStatsToFile();
            List<SensorNode> multicastNeighbors = geoPathManager.getNextMulticastHops(prev, cur);
            if ((multicastNeighbors != null) && (multicastNeighbors.size() > 0)) {
                for (SensorNode next : multicastNeighbors) {
                    if (!next.equals(cur)) {
                        log.info("Delivering from {} to {}", cur.deviceId(), next.deviceId());
                        Path intermediateNodesPath = geoPathManager.getIntermediatePath(sink, cur, next);
                        log.info("{}-->{}: {}", inboundPacket.receivedFrom().deviceId(), cur.deviceId(),
                                intermediateNodesPath.toString());
                        writePathsToFile(cur, next, intermediateNodesPath);
                        installRulesOpenPath(sink, cur, next, intermediateNodesPath, inboundPacket);
                        installRulesDropAtDestination(next);

                        SensorTrafficTreatment localTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                                .setPacketSrcAddress(sink.nodeAddress())
                                .setPacketDstAddress(next.nodeAddress())
                                .setPacketType(MULTICAST_CTRL_DATA.getSensorPacketType())
                                .buildSensorFlow();

                        OutboundPacket outboundPacket = new DefaultOutboundPacket(
                                next.deviceId(), localTrafficTreatment, inboundPacket.unparsed());
                        log.info("Sending packet back with provider {}", next.providerId().toString());
                        packetService.emit(outboundPacket);

                        deliverMulticastMessagesOpenPath(geoPathManager, sink, cur, next, inboundPacket);
                    }
                }
            }

        }

        private void deliverMulticastMessagesPlain(GeoPathManager geoPathManager, SensorNode sink,
                                                   SensorNode prev, SensorNode cur, InboundPacket inboundPacket) {
            List<SensorNode> multicastNeighbors = geoPathManager.getNextMulticastHops(prev, cur);
            if ((multicastNeighbors != null) && (multicastNeighbors.size() > 0)) {
                RSSILinkWeight weight = new RSSILinkWeight(sensorNodeService);
                for (SensorNode next : multicastNeighbors) {
                    if (!next.equals(cur)) {
                        log.info("Delivering from {} to {}", cur.deviceId(), next.deviceId());

                        Path sinkPrevPath = topologyService.getPaths(topologyService.currentTopology(), sink.deviceId(),
                                cur.deviceId(), weight).iterator().next();
                        Path prevCurPath = topologyService.getPaths(topologyService.currentTopology(), cur.deviceId(),
                                next.deviceId(), weight).iterator().next();

                        Path path = concatenatePaths(sinkPrevPath, prevCurPath);
//                        installRulesOpenPath(sink, prev, cur, path, inboundPacket);

//                        log.info("{}-->{}: {}", inboundPacket.receivedFrom().deviceId(), cur.deviceId(),
//                                intermediateNodesPath.toString());
                        writePathsToFile(cur, next, path);
                        installRulesOpenPath(sink, cur, next, path, inboundPacket);
                        installRulesDropAtDestination(next);

                        SensorTrafficTreatment localTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                                .setPacketSrcAddress(sink.nodeAddress())
                                .setPacketDstAddress(next.nodeAddress())
                                .setPacketType(MULTICAST_CTRL_DATA.getSensorPacketType())
                                .buildSensorFlow();

                        OutboundPacket outboundPacket = new DefaultOutboundPacket(
                                next.deviceId(), localTrafficTreatment, inboundPacket.unparsed());
                        log.info("Sending packet back with provider {}", next.providerId().toString());
                        packetService.emit(outboundPacket);

                        deliverMulticastMessagesPlain(geoPathManager, sink, cur, next, inboundPacket);
                    }
                }
            }
        }

        private Path concatenatePaths(Path path1, Path path2) {
            List<Link> path1Links = path1.links();
            List<Link> path2Links = path2.links();

            Iterable<Link> pathLinks = Iterables.concat(path1Links, path2Links);
            Path path = new DefaultPath(new ProviderId("dummy", "id"), Lists.newArrayList(pathLinks),
                    path1.cost() + path2.cost());

            return path;
        }

        private void writeNodesStatsToFile() {
            List<SensorNode> allNodes = ImmutableList.copyOf(sensorNodeService.getSensorNodes());
            try {
                FileOutputStream fileOutputStream = new FileOutputStream("stats.dat", true);
                float energy = 0;
                for (SensorNode sensorNode : allNodes) {
                    energy += sensorNodeService.getSensorNodeBatteryLevel(SensorNodeId.sensorNodeId(sensorNode.mac()));
                }
                String energyString = "" + energy + " ";
                fileOutputStream.write(energyString.getBytes());
                fileOutputStream.flush();
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private synchronized void writePathsToFile(SensorNode cur, SensorNode next, Path path) {
            cost += getPathCost(path, new RSSILinkWeight(sensorNodeService));
            hopCount += path.links().size() - 1;

            String str = "";
            List<Link> links = path.links();

            int index = 0;
            for (int i = 0; i < links.size(); i++) {
                Link link = links.get(i);
                if (link.src().deviceId().equals(cur.deviceId())) {
                    SensorNode node = sensorNodeService.getSensorNode(link.src().deviceId());
                    str = str + node.addr()[1];
                    index = i;
                    Integer rules = rulesPerNode.get(node);
                    if (rules == null) {
                        rules = 0;
                    }
                    rules++;
                    rulesPerNode.put(node, rules);
                    break;
                }
            }

            for (int i = index; i < links.size(); i++) {
                Link link = links.get(i);
                SensorNode node = sensorNodeService.getSensorNode(link.dst().deviceId());
                str = str + "," + node.addr()[1];
                Integer rules = rulesPerNode.get(node);
                if (rules == null) {
                    rules = 0;
                }
                rules++;
                rulesPerNode.put(node, rules);
            }
            str = str + "\n";

//            String str = cur.deviceId() + " --> " + next.deviceId() + " "
//                    + path.cost() + " "
//                    + path.links().size() + "\n";
            try {
                FileOutputStream fout = new FileOutputStream("path.log", true);
                fout.write(str.getBytes());
                fout.flush();
                fout.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void deliverMulticastMessages(GeoPathManager geoPathManager, SensorNode prev, SensorNode cur,
                                              InboundPacket inboundPacket) {
            List<SensorNode> multicastNeighbors = geoPathManager.getNextMulticastHops(prev, cur);
            if ((multicastNeighbors != null) && (multicastNeighbors.size() > 0)) {
                for (SensorNode next : multicastNeighbors) {
                    Path intermediateNodesPath = geoPathManager.getIntermediatePath(cur, next);
                    log.info("{}-->{}: {}", inboundPacket.receivedFrom().deviceId(), cur.deviceId(),
                            intermediateNodesPath.toString());
//                    installRules(cur, next, intermediateNodesPath, inboundPacket);
                    deliverMulticastMessages(geoPathManager, cur, next, inboundPacket);
                }
            }
        }

        private void installRules(SensorNode sinkSensorNode, SensorNode srcSensorNode, SensorNode dstSensorNode,
                                  InboundPacket pkt) {
//            SDNWiseTrafficSelector sdnWiseTrafficSelector =
//                    (SDNWiseTrafficSelector) SDNWiseEnabledTrafficSelector.builder()
//                            .matchNodeSrcAddr(srcSensorNode.deviceId().uri())
//                            .matchNodeDstAddr(dstSensorNode.deviceId().uri())
//                            .build();

//            SDNWiseTrafficSelector sdnWiseTrafficSelector =
//                    (SDNWiseTrafficSelector) SDNWiseEnabledTrafficSelector.builder()
//                            .matchSensorPacketType(SDNWiseMessageType.MULTICAST_DATA)
//                            .matchSensorNodeMutlicastPrevHop(
//                                    new SensorNodeAddress((byte) srcSensorNode.netId(), srcSensorNode.addr()))
//                            .build();

//            SensorNodeLocalization sensorNodeLocalization = new BiasedLocalizationAlgorithm();
//            SensorNodeLocalization sensorNodeLocalization = new GeoLocalizationAlgorithm();
            double[] coords = dstSensorNode.xyzCoordinates(sensorNodeLocalization);
            int[] args = new int[3];
            args[0] = (int) coords[0];
            args[1] = (int) coords[1];
            args[2] = (int) coords[2];
            byte[] addr = dstSensorNode.addr();
            int[] dstAddr = new int[3];
            dstAddr[0] = addr[0] * 256 + addr[1];
//            SDNWiseTrafficTreatment sdnWiseTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                    .setForwardFunction((byte) 1, true, args)
//                    .setForwardFunction((byte) 2, true, dstAddr)
//                    .buildSensorFlow();
//            SDNWiseTrafficTreatment sdnWiseTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                    .setForwardFunction((byte) 1, true, dstAddr)
//                    .buildSensorFlow();
//
//            FlowRule flowRule = new DefaultFlowRule(srcSensorNode.deviceId(), sdnWiseTrafficSelector,
//                    sdnWiseTrafficTreatment, PRIORITY, appId, TIMEOUT, false);
//            flowRuleService.applyFlowRules(flowRule);

//            SDNWiseTrafficTreatment localTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                    .setPacketSrcAddress(srcSensorNode.addr())
//                    .setPacketDstAddress(dstSensorNode.addr())
//                    .buildSensorFlow();

            byte[] coordinates = new  byte[6];
            for (int i = 0; i < 3; i++) {
                coordinates[2 * i] = (byte) (args[i] >> 8);
                coordinates[2 * i + 1] = (byte) (args[i]);
            }

            SensorTrafficTreatment localTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .setPacketSrcAddress(sinkSensorNode.nodeAddress())
                    .setPacketDstAddress(srcSensorNode.nodeAddress())
                    .setGeoNxHopAddress(dstSensorNode.nodeAddress())
                    .setGeoNxHopCoordinates(coordinates)
                    .setGeoPrvHopAddress(srcSensorNode.nodeAddress())
                    .setPacketType(MULTICAST_CTRL_DATA.getSensorPacketType())
                    .buildSensorFlow();

            if (pkt.unparsed() == null) {
                log.info("EMPTY PACKET");
            } else {
                log.info(Arrays.toString(pkt.unparsed().array()));
            }

            OutboundPacket outboundPacket =
                    new DefaultOutboundPacket(srcSensorNode.deviceId(), localTrafficTreatment, pkt.unparsed());
            log.info("Sending packet back with provider {}", srcSensorNode.providerId().toString());
            packetService.emit(outboundPacket);
        }

        private void installRulesOpenPath(SensorNode sinkSensorNode, SensorNode srcSensorNode, SensorNode dstSensorNode,
                                          Path path, InboundPacket pkt) {
            SensorTrafficSelector sensorTrafficSelector =
                    (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                            .matchNodeSrcAddr(srcSensorNode.nodeAddress())
                            .matchNodeDstAddr(dstSensorNode.nodeAddress())
                            .build();
            SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .setOpenPath(path)
                    .buildSensorFlow();

            FlowRule flowRule = new DefaultFlowRule(srcSensorNode.deviceId(), sensorTrafficSelector,
                    sensorTrafficTreatment, PRIORITY, appId, TIMEOUT, false);
            flowRuleService.applyFlowRules(flowRule);
        }

        private void installRulesDropAtDestination(SensorNode destination) {
            SensorTrafficSelector sensorTrafficSelector =
                    (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                        .matchNodeDstAddr(destination.nodeAddress(), EQUAL)
                        .matchSensorPacketType(MULTICAST_CTRL_DATA.getSensorPacketType())
                        .build();

            SensorTrafficTreatment trafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .dropPacket()
                    .buildSensorFlow();
            FlowRule flowRule = new DefaultFlowRule(destination.deviceId(), sensorTrafficSelector,
                    trafficTreatment, PRIORITY, appId, TIMEOUT, false);
            flowRuleService.applyFlowRules(flowRule);
        }

        private double getPathCost(Path path, LinkWeight linkWeight) {
            List<Link> links = path.links();
            double cost = 0;
            for (Link link : links) {
                TopologyEdge topologyEdge = new DefaultTopologyEdge(new DefaultTopologyVertex(link.src().deviceId()),
                        new DefaultTopologyVertex(link.dst().deviceId()), link);
                cost += linkWeight.weight(topologyEdge);
            }
            return cost;
        }



//        private void deliverMulticastMessages(GeoTopologyService geoTopologyService,
//                                              GeoNode prev, GeoNode cur,
//                                              InboundPacket inboundPacket) {
////            log.info("Delivering message from device {}", inboundPacket.receivedFrom().deviceId());
//            List<GeoNode> multicastNeighbors = geoTopologyService.getNextMulticastHops(prev, cur);
//            if ((multicastNeighbors != null) && (multicastNeighbors.size() > 0)) {
//                for (GeoNode next : multicastNeighbors) {
////                    log.info("Looking for path between {} and {}", cur.getDeviceId(), next.getDeviceId());
//                    Path intermediateNodesPath = geoTopologyService.getIntermediatePath(cur, next);
////                    if (prev != null) {
////                        log.info("Multicast Path {} -> {} length = {}",
////                                prev.getDeviceId(), cur.getDeviceId(), intermediateNodesPath.links().size());
////                    } else {
////                        log.info("Multicast Path from Source {} length = {}",
////                                cur.getDeviceId(), intermediateNodesPath.links().size());
////                    }
//
//                    log.info("{}-->{}: {}", inboundPacket.receivedFrom().deviceId(), cur.getDeviceId(),
//                            intermediateNodesPath.toString());
//
////                    installRules(cur, next, intermediateNodesPath, inboundPacket);
////                    deliverMulticastMessages(geoTopologyService, cur, next, inboundPacket);
//                }
//            } else {
//                log.info("No more multicast hops with prev {} and cur {}",
//                        (prev != null ? prev.getDeviceId() : "none"), cur.getDeviceId());
//            }
//        }

//        private void installRules(GeoNode src, GeoNode dst, Path path, InboundPacket pkt) {
//            DeviceId srcDeviceId = src.getDeviceId();
//            DeviceId dstDeviceId = dst.getDeviceId();
//
//            SensorNode srcSensorNode = sensorNodeService.getSensorNode(srcDeviceId);
//            SensorNode dstSensorNode = sensorNodeService.getSensorNode(dstDeviceId);
//
//            SDNWiseTrafficSelector sdnWiseTrafficSelector =
//                    (SDNWiseTrafficSelector) SDNWiseEnabledTrafficSelector.builder()
//                            .matchNodeSrcAddr(srcDeviceId.uri())
//                            .matchNodeDstAddr(dstDeviceId.uri())
//                            .build();
//            SDNWiseTrafficTreatment sdnWiseTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                    .setOpenPath(path)
//                    .buildSensorFlow();
//
//            FlowRule flowRule = new DefaultFlowRule(srcDeviceId, sdnWiseTrafficSelector, sdnWiseTrafficTreatment,
//                    PRIORITY, appId, TIMEOUT, false);
//            flowRuleService.applyFlowRules(flowRule);
//
//            SDNWiseTrafficTreatment localTrafficTreatment = SDNWiseEnabledTrafficTreatment.builder()
//                    .setPacketSrcAddress(srcSensorNode.addr())
//                    .setPacketDstAddress(dstSensorNode.addr())
//                    .buildSensorFlow();
//
//            OutboundPacket outboundPacket =
//                    new DefaultOutboundPacket(srcDeviceId, localTrafficTreatment, pkt.unparsed());
//            packetService.emit(outboundPacket);
//        }

        // Indicates whether this is a control packet, e.g. LLDP, BDDP
        private boolean isControlPacket(Ethernet eth) {
            short type = eth.getEtherType();
            return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
        }

        // Indicated whether this is an IPv6 multicast packet.
        private boolean isIpv6Multicast(Ethernet eth) {
            return eth.getEtherType() == Ethernet.TYPE_IPV6 && eth.isMulticast();
        }
    }
}

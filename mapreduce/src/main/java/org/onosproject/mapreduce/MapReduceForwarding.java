package org.onosproject.mapreduce;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mapreduce.profile.SensorType;
import org.onosproject.mapreduce.protocol.MapPacket;
import org.onosproject.mapreduce.protocol.ReducePacket;
import org.onosproject.mapreduce.stats.PathCostStatistics;
import org.onosproject.mapreduce.stats.PathHopsStatistics;
import org.onosproject.mapreduce.topology.RSSILinkWeight;
import org.onosproject.mapreduce.util.MapReduceDeployer;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.SensorNode;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.sensor.SensorNodeAddress;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.sensor.SensorNodeStore;
import org.onosproject.net.sensorflow.SensorEnabledTrafficSelector;
import org.onosproject.net.sensorflow.SensorEnabledTrafficTreatment;
import org.onosproject.net.sensorflow.SensorTrafficSelector;
import org.onosproject.net.sensorflow.SensorTrafficTreatment;
import org.onosproject.net.sensorpacket.SensorInboundPacket;
import org.onosproject.net.sensorpacket.SensorPacketTypeRegistry.SensorPacketType;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.onosproject.mapreduce.protocol.MapReduceFunctionType.PROTO_ENCAPSULATION_FUNCTION;
import static org.onosproject.mapreduce.protocol.MapReducePacketType.MAP_DATA;
import static org.onosproject.mapreduce.protocol.MapReducePacketType.REDUCE_DATA;
import static org.onosproject.net.sensorflow.SensorFlowCriterion.SensorNodeCriterionMatchType.EQUAL;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by aca on 9/3/15.
 */
@Component(immediate = true)
public final class MapReduceForwarding {
    private static final int TIMEOUT = 1;
    private static final int PRIORITY = 10;
    private static final int HOST_MAPREDUCE_PORT = 6000;

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SensorNodeService sensorNodeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SensorNodeStore sensorNodeStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    private ApplicationId appId;
    PacketProcessor packetProcessor = new MapReducePacketProcessor();

    private boolean packetOutOnly = false;

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.onosproject.mapreduce");
        packetService.addProcessor(packetProcessor, 11);
        log.info("Started with Application ID {}", appId.id());
    }

    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(packetProcessor);
        packetProcessor = null;
        log.info("Stopped Application with ID {}", appId.id());
    }

    private class MapReducePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (isControlPacket(ethPkt) || isIpv6Multicast(ethPkt) || ethPkt.isMulticast()) {
                return;
            }

            Path path;

            PathHopsStatistics pathHopsStatistics = PathHopsStatistics.getInstance();
            PathCostStatistics pathCostStatistics = PathCostStatistics.getInstance();

            DeviceId incomingDeviceId = pkt.receivedFrom().deviceId();

            if (isSensorNode(incomingDeviceId)) {
                path = handleDestinationSensor((SensorInboundPacket) pkt);
                if (path != null) {
                    pathHopsStatistics.update(path).log();
                    pathCostStatistics.update(path).log();

                    installIntegratedRules(pkt, path);
                }
            } else {
                path = handleDestinationHost(context);
                if (path != null) {
                    pathHopsStatistics.update(path).log();
                    pathCostStatistics.update(path).log();

                    installRule(context, path.src().port());
                }
            }

        }

        private Path handleDestinationHost(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            Path path;
            Set<SensorNode> dsts = sensorNodeService.getSensorNodesByMac(ethPkt.getDestinationMAC());
            if ((dsts == null) || (dsts.size() == 0)) {
                // consider a single host
                HostId id = HostId.hostId(ethPkt.getDestinationMAC());
                MacAddress hostMac = pkt.parsed().getDestinationMAC();

                // Do not process link-local addresses in any way.
                if (id.mac().isLinkLocal()) {
                    return null;
                }

                // Do we know who this is for? If not, flood and bail.
                Host dst = hostService.getHost(id);
                if (dst == null) {
                    flood(context);
                    return null;
                }

                // Are we on an edge switch that our destination is on? If so,
                // simply forward out to the destination and bail.
                if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                    if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                        installRule(context, dst.location().port());
                    }
                    return null;
                }

                // Otherwise, get a set of paths that lead from here to the
                // destination edge switch.
                Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                        pkt.receivedFrom().deviceId(), dst.location().deviceId());
                if (paths.isEmpty()) {
                    // If there are no paths, flood and bail.
                    flood(context);
                    return null;
                }

                path = pickForwardPath(paths, pkt.receivedFrom().port());
                if (path == null) {
                    log.warn("Doh... don't know where to go... {} -> {} received on {}",
                            ethPkt.getSourceMAC(), hostMac,
                            pkt.receivedFrom());
                    flood(context);
                    return null;
                }
            } else {
                SensorNode dst = sensorNodeService.getSensorNodesByMac(ethPkt.getDestinationMAC()).iterator().next();
                // Are we on an edge switch that our destination is on? If so,
                // simply forward out to the destination and bail.
                if (pkt.receivedFrom().deviceId().equals(dst.associatedSink().sinkLocation())) {
                    if (!context.inPacket().receivedFrom().port().equals(dst.associatedSink().sinkConnectionPort())) {
                        installRule(context, dst.associatedSink().sinkConnectionPort());
                    }
                    return null;
                }

                // Otherwise, get a set of paths that lead from here to the
                // destination edge switch.
                Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                        pkt.receivedFrom().deviceId(), dst.associatedSink().sinkLocation());
                if (paths.isEmpty()) {
                    // If there are no paths, flood and bail.
                    flood(context);
                    return null;
                }

                path = pickForwardPath(paths, pkt.receivedFrom().port());
                if (path == null) {
                    log.warn("Doh... don't know where to go... {} -> {} received on {}",
                            ethPkt.getSourceMAC(), ethPkt.getDestinationMAC(),
                            pkt.receivedFrom());
                    flood(context);
                    return null;
                }
            }


            // Otherwise forward and be done with it.
            return path;
        }

        private Path handleDestinationSensor(SensorInboundPacket pkt) {
            RSSILinkWeight rssiLinkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore,
                    RSSILinkWeight.SMALL_OF_LINK_WEIGHT);
//            RSSILinkWeight rssiLinkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore,
//                    RSSILinkWeight.BIG_OF_LINK_WEIGHT);
            DeviceId srcDeviceId, dstDeviceId;
            MacAddress dstMac = pkt.parsed().getDestinationMAC();
            Set<SensorNode> nodes = sensorNodeService.getSensorNodesByMac(dstMac);
            if ((nodes != null) && (nodes.size() > 0)) {
                dstDeviceId = nodes.iterator().next().deviceId();
            } else {
                // It should be a host
                HostId id = HostId.hostId(dstMac);
                Host dst = hostService.getHost(id);
                dstDeviceId = dst.location().deviceId();
            }
            MacAddress srcMac = pkt.parsed().getSourceMAC();
            nodes = sensorNodeService.getSensorNodesByMac(srcMac);
            if ((nodes != null) && (nodes.size() > 0)) {
                srcDeviceId = nodes.iterator().next().deviceId();
            } else {
                // It should be a host
                HostId id = HostId.hostId(dstMac);
                Host dst = hostService.getHost(id);
                srcDeviceId = dst.location().deviceId();
            }
            Set<Path> paths = null;
            MapReduceDeployer mapReduceDeployer = MapReduceDeployer.getInstance(sensorNodeService, sensorNodeStore,
                    topologyService);
//            log.info("Handling packet {}", Arrays.toString(pkt.unparsed().array()));
            if (pkt.sensorPacketType().equals(MAP_DATA.getSensorPacketType())) {
                // Create the packet representation
                MapPacket mapPacket = MapPacket.fromRawData(pkt.parsed().getPayload().serialize());
                // Find the reducer for that key
                // The number of keys equals the overall number of sensor types
                Map<SensorType, SensorNode> keyToNodeMap = mapReduceDeployer.getReducersDeployment();
                SensorNode reducer = keyToNodeMap.get(SensorType.getSensorType(mapPacket.getKey()));
                // Calculate the optimal path to the reducer
                log.info("Calculating path from mapper {} to reducer {}",
                        sensorNodeService.getSensorNode(srcDeviceId).nodeAddress(), reducer.nodeAddress());
                paths = topologyService.getPaths(topologyService.currentTopology(),
                        srcDeviceId, reducer.deviceId(), rssiLinkWeight);
                log.info("Returning path from mapper {} to reducer {}",
                        sensorNodeService.getSensorNode(srcDeviceId).nodeAddress(), reducer.nodeAddress());
            } else if (pkt.sensorPacketType().equals(REDUCE_DATA.getSensorPacketType())) {
                // In this case, the packet should go to the data center
                // We assume a single host running that service
                log.info("Received REDUCE packet");
                Set<Host> hosts = hostService.getHostsByIp(IpAddress.valueOf("10.0.1.253"));
                if (hosts.iterator().hasNext()) {
                    Host host = hosts.iterator().next();
                    log.info("Calculating path from reducer {} to host {}",
                            sensorNodeService.getSensorNode(srcDeviceId).nodeAddress(),
                            host.ipAddresses().iterator().next());
                    paths = topologyService.getPaths(topologyService.currentTopology(),
                            srcDeviceId, host.location().deviceId(),
                            rssiLinkWeight);
                    log.info("Returning path from reducer {} to host {}",
                            sensorNodeService.getSensorNode(srcDeviceId).nodeAddress(),
                            host.ipAddresses().iterator().next());
                } else {
                    log.info("No path found to send packet from reducer {}",
                            sensorNodeService.getSensorNode(srcDeviceId).nodeAddress());
                }
            } else {
                log.info("Getting path from {} to {}", pkt.receivedFrom().deviceId(), dstDeviceId);
                paths = topologyService.getPaths(topologyService.currentTopology(),
                        pkt.receivedFrom().deviceId(), dstDeviceId, rssiLinkWeight);
            }

            Path path = null;
            if ((paths != null) && (paths.size() > 0)) {
                path = paths.iterator().next();
            }

            return path;
        }

        // Indicates whether this is a control packet, e.g. LLDP, BDDP
        private boolean isControlPacket(Ethernet eth) {
            short type = eth.getEtherType();
            return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
        }

        // Indicated whether this is an IPv6 multicast packet.
        private boolean isIpv6Multicast(Ethernet eth) {
            return eth.getEtherType() == Ethernet.TYPE_IPV6 && eth.isMulticast();
        }

        // Floods the specified packet if permissible.
        private void flood(PacketContext context) {
            if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                    context.inPacket().receivedFrom())) {
                packetOut(context, PortNumber.FLOOD);
            } else {
                context.block();
            }
        }

        // Sends a packet out the specified port.
        private void packetOut(PacketContext context, PortNumber portNumber) {
            context.treatmentBuilder().setOutput(portNumber);
            context.send();
        }

        // Install a rule forwarding the packet to the specified port.
        private void installRule(PacketContext context, PortNumber portNumber) {
            // We don't yet support bufferids in the flowservice so packet out first.
            packetOut(context, portNumber);
            if (!packetOutOnly) {
                // Install the flow rule to handle this type of message from now on.
                Ethernet inPkt = context.inPacket().parsed();
                TrafficSelector.Builder builder = DefaultTrafficSelector.builder();
                builder.matchEthType(inPkt.getEtherType())
                        .matchEthSrc(inPkt.getSourceMAC())
                        .matchEthDst(inPkt.getDestinationMAC())
                        .matchInport(context.inPacket().receivedFrom().port());

                TrafficTreatment.Builder treat = DefaultTrafficTreatment.builder();
                treat.setOutput(portNumber);

                FlowRule f = new DefaultFlowRule(context.inPacket().receivedFrom().deviceId(),
                        builder.build(), treat.build(), PRIORITY, appId, TIMEOUT, false);

                flowRuleService.applyFlowRules(f);
            }
        }

        private void sendBackPacket(InboundPacket pkt, DeviceId targetDeviceId) {
            SensorInboundPacket packet = (SensorInboundPacket) pkt;
            SensorPacketType sensorPacketType = packet.sensorPacketType();
            SensorNode srcNode = sensorNodeService.getSensorNodesByMac(pkt.parsed().getSourceMAC()).iterator().next();
            SensorNode dstNode = sensorNodeService.getSensorNode(targetDeviceId);

            if (dstNode == null) {
                dstNode = srcNode.associatedSink();
            }

            // Open the path from the sink to the receiver node
            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                    dstNode.associatedSink().deviceId(), dstNode.deviceId());
            if ((paths != null) && (paths.size() > 0)) {
                Path path = paths.iterator().next();

//                SensorTrafficSelector trafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
//                        .matchNodeSrcAddr(srcNode.nodeAddress())
//                        .matchNodeDstAddr(dstNode.nodeAddress())
//                        .build();

                SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                        .setOpenPath(path)
                        .buildSensorFlow();

                FlowRule flowRule = new DefaultFlowRule(path.src().deviceId(), null,
                        sensorTrafficTreatment, 10, appId, 10, false);
                flowRuleService.applyFlowRules(flowRule);
            }

            SensorTrafficTreatment localTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .setPacketType(sensorPacketType)
                    .setPacketSrcAddress(srcNode.nodeAddress())
                    .setPacketDstAddress(dstNode.nodeAddress())
                    .buildSensorFlow();

            byte[] data = pkt.parsed().getPayload().serialize();
            OutboundPacket outboundPacket = new DefaultOutboundPacket(dstNode.deviceId(), localTrafficTreatment,
                    ByteBuffer.wrap(data));
            log.info("Sending back packet {} to node {}", Arrays.toString(pkt.unparsed().array()),
                    dstNode.nodeAddress());
            packetService.emit(outboundPacket);
        }

        private void openPath(InboundPacket pkt, Path path) {
            SensorInboundPacket sensorInboundPacket = (SensorInboundPacket) pkt;
            SensorTrafficSelector sensorTrafficSelector = null;
            if (sensorInboundPacket.sensorPacketType().originalId() == MAP_DATA.getSensorPacketType().originalId()) {
                // Create the packet representation
                MapPacket mapPacket = MapPacket.fromRawData(pkt.parsed().getPayload().serialize());
//                log.info("Opening the path for map packet {} sent by {}", mapPacket,
//                        sensorNodeService.getSensorNode(path.src().deviceId()).nodeAddress());
                sensorTrafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                        .matchSensorPacketType(EQUAL, MAP_DATA.getSensorPacketType())
                        .matchPacketFieldWithConst(MapPacket.KEY_BYTE_POS, mapPacket.getKey(), 1, EQUAL)
                        .build();
            }

            SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                    .setOpenPath(path)
                    .buildSensorFlow();

            FlowRule flowRule = new DefaultFlowRule(path.src().deviceId(), sensorTrafficSelector,
                    sensorTrafficTreatment, 10, appId, 10, false);
            flowRuleService.applyFlowRules(flowRule);
        }

        private boolean isSink(SensorNode sensorNode) {
            return sensorNode == null ? false : sensorNode.associatedSink().equals(sensorNode);
        }

        private void installIntegratedRules(InboundPacket pkt, Path path) {
            RSSILinkWeight rssiLinkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore,
                    RSSILinkWeight.SMALL_OF_LINK_WEIGHT);
//            RSSILinkWeight rssiLinkWeight = new RSSILinkWeight(sensorNodeService, sensorNodeStore,
//                    RSSILinkWeight.BIG_OF_LINK_WEIGHT);
            List<Pair<DeviceId, DeviceId>> sensorNodesPairs = Lists.newArrayList();
            List<Pair<DeviceId, DeviceId>> sensorNodesHostsPairs = Lists.newArrayList();
            Iterator<Link> pathLinks = path.links().iterator();
            DeviceId firstDeviceId = null;
            Link link = null;
            while (pathLinks.hasNext()) {
                link = pathLinks.next();
                if (firstDeviceId == null) {
                    firstDeviceId = link.src().deviceId();
                }
                SensorNode src = sensorNodeService.getSensorNode(link.src().deviceId());
                SensorNode dst = sensorNodeService.getSensorNode(link.dst().deviceId());
                if ((isSink(src)) && (!firstDeviceId.equals(src.deviceId())) && (dst == null)) {
                    log.info("Creating pair {} - {}", firstDeviceId, src.deviceId());
                    sensorNodesPairs.add(new ImmutablePair<>(firstDeviceId, src.deviceId()));
                    firstDeviceId = src.deviceId();
                } else if (isSink(dst) && (!firstDeviceId.equals(dst.deviceId())) && (src == null)) {
                    log.info("Creating pair {} - {}", firstDeviceId, dst.deviceId());
                    sensorNodesPairs.add(new ImmutablePair<>(firstDeviceId, dst.deviceId()));
                    firstDeviceId = dst.deviceId();
                }
            }
            if (link != null) {
                if ((!firstDeviceId.equals(link.src().deviceId()))
                        && (!firstDeviceId.equals(link.dst().deviceId()))) {
                    if (isSensorNode(link.dst().deviceId())) {
                        sensorNodesPairs.add(new ImmutablePair<>(firstDeviceId, link.dst().deviceId()));
                    } else {
                        sensorNodesHostsPairs.add(new ImmutablePair<>(firstDeviceId, link.dst().deviceId()));
                    }
                }
            }
            sensorNodesHostsPairs.forEach(sensorNodeHostPair -> {
                DeviceId srcNodeDeviceId = sensorNodeHostPair.getLeft();
                DeviceId dstNodeDeviceId = sensorNodeHostPair.getRight();
                SensorNode srcNode = sensorNodeService.getSensorNode(srcNodeDeviceId);
                SensorNode dstNode = sensorNodeService.getSensorNode(dstNodeDeviceId);
                SensorInboundPacket sensorInboundPacket = (SensorInboundPacket) pkt;
                ReducePacket reducePacket = ReducePacket.fromRawData(
                        sensorInboundPacket.parsed().getPayload().serialize());
                log.info("Received reduce packet (STAGE 1) with key {} and value {}",
                        reducePacket.getKey(), reducePacket.getValue());
                if (isSink(srcNode)) {
                    // This means that the destination is the host
                    Host host = hostService.getConnectedHosts(dstNodeDeviceId).iterator().next();
                    byte[] ip = host.ipAddresses().iterator().next().toOctets();
                    int port = HOST_MAPREDUCE_PORT;
                    SensorTrafficSelector sensorTrafficSelector =
                            (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
//                                    .matchNodeSrcAddr(new SensorNodeAddress((byte) srcNode.netId(), srcNode.addr()))
                                    .matchSensorPacketType(EQUAL, REDUCE_DATA.getSensorPacketType())
//                                    .matchPacketFieldWithConst(ReducePacket.KEY_POS, reducePacket.getKey(), 1, EQUAL)
                                    .build();
                    int[] args = new int[3];
                    args[0] = ((ip[0] & 0xFF) << 8) + (ip[1] & 0xFF);
                    args[1] = ((ip[2] & 0xFF) << 8) + (ip[3] & 0xFF);
                    args[2] = port + (reducePacket.getKey() - 1);
                    // Install the rule to call the function
                    SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                            .setForwardFunction(PROTO_ENCAPSULATION_FUNCTION.functionId(), false, args)
                            .buildSensorFlow();
                    FlowRule flowRule = new DefaultFlowRule(srcNodeDeviceId, sensorTrafficSelector,
                            sensorTrafficTreatment, PRIORITY, appId, TIMEOUT, false);
                    flowRuleService.applyFlowRules(flowRule);
                } else {
                    Host host = hostService.getConnectedHosts(srcNodeDeviceId).iterator().next();
                    byte[] ip = host.ipAddresses().iterator().next().toOctets();
                    int port = HOST_MAPREDUCE_PORT;
                    SensorTrafficSelector sensorTrafficSelector =
                            (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
//                                    .matchNodeSrcAddr(new SensorNodeAddress((byte) dstNode.netId(), dstNode.addr()))
                                    .matchSensorPacketType(EQUAL, REDUCE_DATA.getSensorPacketType())
//                                    .matchPacketFieldWithConst(ReducePacket.KEY_POS, reducePacket.getKey(), 1, EQUAL)
                                    .build();
                    int[] args = new int[3];
                    args[0] = ((ip[0] & 0xFF) << 8) + (ip[1] & 0xFF);
                    args[1] = ((ip[2] & 0xFF) << 8) + (ip[3] & 0xFF);
                    args[2] = port + reducePacket.getKey() - 1;
                    // Install the rule to call the function
                    SensorTrafficTreatment sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                            .setForwardFunction(PROTO_ENCAPSULATION_FUNCTION.functionId(), false, args)
                            .buildSensorFlow();
                    FlowRule flowRule = new DefaultFlowRule(dstNodeDeviceId, sensorTrafficSelector,
                            sensorTrafficTreatment, PRIORITY, appId, TIMEOUT, false);
                    flowRuleService.applyFlowRules(flowRule);
                }
            });
            sensorNodesPairs.forEach(sensorNodesPair -> {
                DeviceId srcNodeDeviceId = sensorNodesPair.getLeft();
                DeviceId dstNodeDeviceId = sensorNodesPair.getRight();
                SensorNode srcNode = sensorNodeService.getSensorNode(srcNodeDeviceId);
                SensorNode dstNode = sensorNodeService.getSensorNode(dstNodeDeviceId);
                if ((isSink(srcNode)) && isSink(dstNode)) {
                    log.info("Handling SINK to SINK {} -> {} communication",
                            srcNode.nodeAddress(), dstNode.nodeAddress());
                    // Then, prepare encapsulation for forwarding in OF network
                    byte[] ip;
                    int port = 0;
                    SensorTrafficSelector sensorTrafficSelector = null;
                    SensorTrafficTreatment sensorTrafficTreatment = null;
                    IpAddress dstNodeSinkIpAddress = dstNode.sinkAddress();
                    ip = dstNodeSinkIpAddress.toOctets();
                    port = (int) dstNode.sinkPort().toLong();

                    DeviceId finalDeviceId = path.dst().deviceId();
                    if (isSensorNode(finalDeviceId)) {
                        SensorNode finalNode = sensorNodeService.getSensorNode(finalDeviceId);
                        sensorTrafficSelector = (SensorTrafficSelector) SensorEnabledTrafficSelector.builder()
                                .matchNodeDstAddr(new SensorNodeAddress((byte) finalNode.netId(), finalNode.addr()))
                                .build();
                    }
                    int[] args = new int[3];
                    args[0] = ((ip[0] & 0xFF) << 8) + (ip[1] & 0xFF);
                    args[1] = ((ip[2] & 0xFF) << 8) + (ip[3] & 0xFF);
                    args[2] = port;
                    // Install the rule to call the function
                    sensorTrafficTreatment = SensorEnabledTrafficTreatment.builder()
                            .setForwardFunction(PROTO_ENCAPSULATION_FUNCTION.functionId(), false, args)
                            .buildSensorFlow();

                    FlowRule flowRule = new DefaultFlowRule(srcNodeDeviceId, sensorTrafficSelector,
                            sensorTrafficTreatment, PRIORITY, appId, TIMEOUT, false);
                    flowRuleService.applyFlowRules(flowRule);
                } else if ((isSensorNode(srcNodeDeviceId)) && (isSensorNode(dstNodeDeviceId))) {
                    Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                            srcNodeDeviceId, dstNodeDeviceId, rssiLinkWeight);
                    if ((paths != null) && (paths.size() > 0)) {
                        Path sensorPath = paths.iterator().next();
                        openPath(pkt, sensorPath);
                    }
                }
            });

            if (isSensorNode(path.src().deviceId())) {
                sendBackPacket(pkt, path.dst().deviceId());
            }
        }

        private boolean isSensorNode(DeviceId deviceId) {
            SensorNode node = sensorNodeService.getSensorNode(deviceId);

            return node == null ? false : true;
        }

        // Selects a path from the given set that does not lead back to the
        // specified port.
        private Path pickForwardPath(Set<Path> paths, PortNumber notToPort) {
            for (Path path : paths) {
                if (!path.src().port().equals(notToPort)) {
                    return path;
                }
            }
            return null;
        }

    }
}

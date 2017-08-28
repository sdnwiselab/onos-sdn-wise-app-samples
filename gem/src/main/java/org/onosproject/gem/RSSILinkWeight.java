package org.onosproject.gem;

import org.onosproject.gem.topology.GeoLocalizationAlgorithm;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SensorNodeId;
import org.onosproject.net.sensor.SensorNodeService;
import org.onosproject.net.topology.LinkWeight;
import org.onosproject.net.topology.TopologyEdge;

/**
 * Created by aca on 5/24/15.
 */
public class RSSILinkWeight implements LinkWeight {
    private SensorNodeService sensorNodeService;

    public RSSILinkWeight(SensorNodeService sensorNodeService) {
        this.sensorNodeService = sensorNodeService;
    }

    @Override
    public double weight(TopologyEdge edge) {
        Link link = edge.link();
//        System.out.println("Link is " + link.toString());
        DeviceId srcDeviceId = link.src().deviceId();
        DeviceId dstDeviceId = link.dst().deviceId();

        SensorNode srcSensorNode = sensorNodeService.getSensorNode(srcDeviceId);
        SensorNode dstSensorNode = sensorNodeService.getSensorNode(dstDeviceId);

        if ((srcSensorNode == null) || (dstSensorNode == null)) {
            return 1;
        }

        SensorNodeId srcSensorNodeId = srcSensorNode.id();
        SensorNodeId dstSensorNodeId = dstSensorNode.id();

        GeoLocalizationAlgorithm localizationAlgorithm = new GeoLocalizationAlgorithm();
        double[] srcCoordinates = localizationAlgorithm.xyzCoordinates(srcSensorNodeId);
        double[] dstCoordinates = localizationAlgorithm.xyzCoordinates(dstSensorNodeId);

        double distance = getEuclideanDistance(srcCoordinates, dstCoordinates);

        return distance;
    }

    private double getEuclideanDistance(double[] srcCoordinates, double[] dstCoordinates) {
        double distance = Math.sqrt(Math.pow((srcCoordinates[0] - dstCoordinates[0]), 2) + Math.pow(
                (srcCoordinates[1] - dstCoordinates[1]), 2) + Math.pow((srcCoordinates[2] - dstCoordinates[2]), 2));

        return distance;
    }

//    @Override
//    public double weight(TopologyEdge edge) {
//        double weight = 1;
//        Annotations annotations = edge.link().annotations();
//        Set<String> keys = annotations.keys();
//        if (keys != null) {
//            for (String key : keys) {
//                if (key.startsWith("sdnwise")) {
//                    byte val = Byte.parseByte(annotations.value(key));
//                    int value = val & 0xFF;
////                    weight = (double) (1000 / (double) value);
//                    weight = Math.pow(10, (double) ((double) (250 - value) / (double) 58));
//                    System.out.println("Got weight for link " + edge.link().toString() + " = " + weight);
//                } else {
//                    System.out.println("No sdnwise key found for link " + edge.link().toString());
//                }
//            }
//        } else {
//            System.out.println("No keys found for link " + edge.link().toString());
//        }
//
//        return weight;
//    }
}

package org.onosproject.gem.topology;

import com.google.common.collect.ImmutableList;
import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.SensorNode;
import org.onosproject.net.SensorNodeLocalization;
import org.onosproject.net.provider.ProviderId;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by aca on 5/27/15.
 */
public class GeoSensorPath implements Path {
    private List<Link> geoSensorLinks;
    private List<SensorNode> nodesSequence;
    private ConnectPoint srcConnectPoint;
    private ConnectPoint dstConnectPoint;

    private SensorNodeLocalization sensorNodeLocalization;

    private double pathCost;

    public GeoSensorPath(List<SensorNode> nodesSequence, SensorNodeLocalization sensorNodeLocalization) {
        this.geoSensorLinks = new ArrayList<>();
        this.nodesSequence = ImmutableList.copyOf(nodesSequence);
        this.sensorNodeLocalization = sensorNodeLocalization;
        this.pathCost = 0;
        if ((nodesSequence != null) && (nodesSequence.size() > 0)) {
            int i = 0;
            while (i < (nodesSequence.size() - 1)) {
                SensorNode node1 = nodesSequence.get(i++);
                SensorNode node2 = nodesSequence.get(i);
                GeoSensorLink link = new GeoSensorLink(node1, node2);
                geoSensorLinks.add(link);
                pathCost = pathCost + euclideanDistance(node1, node2);
            }

            srcConnectPoint = new ConnectPoint(nodesSequence.get(0).deviceId(), null);
            dstConnectPoint = new ConnectPoint(nodesSequence.get(nodesSequence.size() - 1).deviceId(), null);
        }
    }

    private double euclideanDistance(SensorNode node1, SensorNode node2) {
        double[] node1Coordinates = node1.xyzCoordinates(sensorNodeLocalization);
        double[] node2Coordinates = node2.xyzCoordinates(sensorNodeLocalization);

        double x1 = node1Coordinates[0];
        double y1 = node1Coordinates[1];
        double x2 = node2Coordinates[0];
        double y2 = node2Coordinates[1];

        double distance = Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));

        return distance;
    }


    @Override
    public List<Link> links() {
        return geoSensorLinks;
    }

    @Override
    public double cost() {
        return pathCost;
    }

    @Override
    public ConnectPoint src() {
        return srcConnectPoint;
    }

    @Override
    public ConnectPoint dst() {
        return dstConnectPoint;
    }

    @Override
    public Type type() {
        return null;
    }

    @Override
    public State state() {
        return null;
    }

    @Override
    public boolean isDurable() {
        return false;
    }

    @Override
    public Annotations annotations() {
        return null;
    }

    @Override
    public ProviderId providerId() {
        return null;
    }

    @Override
    public String toString() {
        String path = "";
        for (SensorNode node : nodesSequence) {
            path = path + node.deviceId() + " ";
        }

        return path;
    }
}

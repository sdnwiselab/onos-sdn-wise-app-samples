package org.onosproject.gem.topology;

import com.google.common.collect.ImmutableList;
import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.provider.ProviderId;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by aca on 5/20/15.
 */
public class GeoPath implements Path {
    List<GeoNode> nodesSequence;
    List<Link> geoLinks;

    public GeoPath(List<GeoNode> nodesSequence) {
        this.nodesSequence = ImmutableList.copyOf(nodesSequence);
        this.geoLinks = new ArrayList<>();

        int i = 0;
        while (i < (nodesSequence.size() - 1)) {
            GeoLink link = new GeoLink(nodesSequence.get(i++), nodesSequence.get(i));
            geoLinks.add(link);
        }
    }

    @Override
    public List<Link> links() {
        return geoLinks;
    }

    @Override
    public double cost() {
        return 0;
    }

    @Override
    public ConnectPoint src() {
        DeviceId srcDeviceId = nodesSequence.get(0).getDeviceId();
        return new ConnectPoint(srcDeviceId, null);
    }

    @Override
    public ConnectPoint dst() {
        DeviceId dstDeviceId = nodesSequence.get(nodesSequence.size() - 1).getDeviceId();
        return new ConnectPoint(dstDeviceId, null);
    }

    @Override
    public Type type() {
        return Type.DIRECT;
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
        for (GeoNode node : nodesSequence) {
            path = path + node.getDeviceId() + " ";
        }

        return path;
    }
}

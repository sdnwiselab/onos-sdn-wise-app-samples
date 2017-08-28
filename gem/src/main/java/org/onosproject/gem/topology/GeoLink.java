package org.onosproject.gem.topology;

import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.net.provider.ProviderId;

/**
 * Created by aca on 5/24/15.
 */
public class GeoLink implements Link {
    private GeoNode src;
    private GeoNode dst;

    public GeoLink(GeoNode src, GeoNode dst) {
        this.src = src;
        this.dst = dst;
    }

    @Override
    public ConnectPoint src() {
        return new ConnectPoint(src.getDeviceId(), null);
    }

    @Override
    public ConnectPoint dst() {
        return new ConnectPoint(dst.getDeviceId(), null);
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
}

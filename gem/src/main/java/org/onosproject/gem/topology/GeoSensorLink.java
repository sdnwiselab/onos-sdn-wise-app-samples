package org.onosproject.gem.topology;

import org.onosproject.net.Annotations;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.net.SensorNode;
import org.onosproject.net.provider.ProviderId;

/**
 * Created by aca on 5/27/15.
 */
public class GeoSensorLink implements Link {
    private SensorNode src;
    private SensorNode dst;
    private Annotations annotations;

    public GeoSensorLink(SensorNode src, SensorNode dst) {
        this.src = src;
        this.dst = dst;
    }

    public GeoSensorLink(SensorNode src, SensorNode dst, Annotations annotations) {
        this.src = src;
        this.dst = dst;
        this.annotations = annotations;
    }

    @Override
    public ConnectPoint src() {
        return new ConnectPoint(src.deviceId(), null);
    }

    @Override
    public ConnectPoint dst() {
        return new ConnectPoint(dst.deviceId(), null);
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
        return annotations;
    }

    @Override
    public ProviderId providerId() {
        return null;
    }
}

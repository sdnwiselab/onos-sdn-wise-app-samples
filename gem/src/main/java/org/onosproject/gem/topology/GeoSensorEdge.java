package org.onosproject.gem.topology;

import org.onosproject.net.SensorNode;

/**
 * Created by aca on 5/27/15.
 */
public class GeoSensorEdge {
    private SensorNode src;
    private SensorNode dst;

    public GeoSensorEdge() {
        src = null;
        dst = null;
    }

    public GeoSensorEdge(SensorNode src, SensorNode dst) {
        this.src = src;
        this.dst = dst;
    }

    public SensorNode getSrc() {
        return src;
    }

    public void setSrc(SensorNode src) {
        this.src = src;
    }

    public SensorNode getDst() {
        return dst;
    }

    public void setDst(SensorNode dst) {
        this.dst = dst;
    }

    @Override
    public String toString() {
        String str = "" + src.deviceId() + " - " + dst.deviceId();
        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoSensorEdge)) {
            return false;
        }

        GeoSensorEdge that = (GeoSensorEdge) o;

        if (!src.equals(that.src)) {
            return false;
        }
        return dst.equals(that.dst);

    }

    @Override
    public int hashCode() {
        int result = src.hashCode();
        result = 31 * result + dst.hashCode();
        return result;
    }
}

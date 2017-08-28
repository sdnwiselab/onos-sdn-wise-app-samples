package org.onosproject.gem.topology;

/**
 * Created by aca on 5/8/15.
 */
public class GeoEdge {
    private GeoNode src;
    private GeoNode dst;

    public GeoEdge() {
        src = null;
        dst = null;
    }

    public GeoEdge(GeoNode src, GeoNode dst) {
        this.src = src;
        this.dst = dst;
    }

    public GeoNode getSrc() {
        return src;
    }

    public GeoNode getDst() {
        return dst;
    }

    public void setSrc(GeoNode src) {
        this.src = src;
    }

    public void setDst(GeoNode dst) {
        this.dst = dst;
    }

    @Override
    public String toString() {
        String str = "" + src.getDeviceId() + " - " + dst.getDeviceId();
        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoEdge)) {
            return false;
        }

        GeoEdge geoEdge = (GeoEdge) o;

        if (((src.equals(geoEdge.src)) && (dst.equals(geoEdge.dst))) ||
                ((src.equals(geoEdge.dst)) && (dst.equals(geoEdge.src)))) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = src.hashCode();
        result = 31 * result + dst.hashCode();
        return result;
    }
}

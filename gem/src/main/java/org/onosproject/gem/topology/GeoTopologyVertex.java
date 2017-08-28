package org.onosproject.gem.topology;

import org.onosproject.net.DeviceId;
import org.onosproject.net.topology.TopologyVertex;

/**
 * Created by aca on 4/28/15.
 */
public class GeoTopologyVertex {
    private TopologyVertex topologyVertex;
    private GeoNode coordinates = null;

    public GeoTopologyVertex(TopologyVertex topologyVertex) {
        this.topologyVertex = topologyVertex;
    }

    public GeoNode getCoordinates() {
        return this.coordinates;
    }

    public void setCoordinates(double x, double y) {
        this.coordinates = new GeoNode(x, y);
    }

    public DeviceId deviceId() {
        return topologyVertex.deviceId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoTopologyVertex)) {
            return false;
        }

        GeoTopologyVertex that = (GeoTopologyVertex) o;

        if (topologyVertex != null ? !topologyVertex.equals(that.topologyVertex)
                : that.topologyVertex != null) {
            return false;
        }

        return !(coordinates != null ? !coordinates.equals(that.coordinates) : that.coordinates != null);

    }

    @Override
    public int hashCode() {
        int result = topologyVertex != null ? topologyVertex.hashCode() : 0;
        result = 31 * result + (coordinates != null ? coordinates.hashCode() : 0);
        return result;
    }
}

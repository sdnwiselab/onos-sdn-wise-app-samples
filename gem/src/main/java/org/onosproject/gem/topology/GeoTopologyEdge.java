package org.onosproject.gem.topology;

import org.onosproject.net.topology.TopologyEdge;

/**
 * Created by aca on 4/28/15.
 */
public class GeoTopologyEdge {
    private TopologyEdge topologyEdge;
    private Double rssi = null;

    public GeoTopologyEdge(TopologyEdge topologyEdge) {
        this.topologyEdge = topologyEdge;
    }

    public Double getRssi() {
        return this.rssi;
    }

    public void setRssi(Double rssi) {
        this.rssi = rssi;
    }

    public TopologyEdge getTopologyEdge() {
        return topologyEdge;
    }
}

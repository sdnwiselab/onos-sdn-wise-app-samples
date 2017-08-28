package org.onosproject.gem.topology;

import org.onosproject.net.SensorNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by aca on 5/27/15.
 */
public class GeoSensorGraph {
    private Map<SensorNode, List<GeoSensorEdge>> nodeEdges;
    private List<GeoSensorEdge> geoEdges;

    public GeoSensorGraph() {
        this.nodeEdges = new HashMap<>();
        this.geoEdges = new ArrayList<>();
    }

    public void addGeoSensorEdge(GeoSensorEdge edge) {
        geoEdges.add(edge);
        SensorNode src = edge.getSrc();
        SensorNode dst = edge.getDst();
        List<GeoSensorEdge> srcEdges = nodeEdges.get(src);
        if (srcEdges == null) {
            srcEdges = new ArrayList<>();
        }
        if (!srcEdges.contains(edge)) {
            srcEdges.add(edge);
        }
        nodeEdges.put(src, srcEdges);

        List<GeoSensorEdge> dstEdges = nodeEdges.get(dst);
        if (dstEdges == null) {
            dstEdges = new ArrayList<>();
        }
        if (!dstEdges.contains(edge)) {
            dstEdges.add(edge);
        }
        nodeEdges.put(dst, dstEdges);
    }

    public List<GeoSensorEdge> getGeoEdges(SensorNode node) {
        return nodeEdges.get(node);
    }
}

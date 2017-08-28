package org.onosproject.gem.topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by aca on 5/8/15.
 */
public class GeoGraph {
    private Map<GeoNode, List<GeoEdge>> nodeEdges;
    private List<GeoEdge> geoEdges;

    public GeoGraph() {
        geoEdges = new ArrayList<>();
        nodeEdges = new HashMap<>();
    }

    public void addEdge(GeoEdge geoEdge) {
        geoEdges.add(geoEdge);
        GeoNode src = geoEdge.getSrc();
        GeoNode dst = geoEdge.getDst();
        List<GeoEdge> srcEdges = nodeEdges.get(src);
        if (srcEdges == null) {
            srcEdges = new ArrayList<>();
        }
        if (!srcEdges.contains(geoEdge)) {
            srcEdges.add(geoEdge);
        }
        nodeEdges.put(src, srcEdges);

        List<GeoEdge> dstEdges = nodeEdges.get(dst);
        if (dstEdges == null) {
            dstEdges = new ArrayList<>();
        }
        if (!dstEdges.contains(geoEdge)) {
            dstEdges.add(geoEdge);
        }
        nodeEdges.put(dst, dstEdges);
    }

    public List<GeoEdge> getGeoEdges() {
        return geoEdges;
    }

    public List<GeoEdge> getGeoEdges(GeoNode node) {
        return nodeEdges.get(node);
    }
}

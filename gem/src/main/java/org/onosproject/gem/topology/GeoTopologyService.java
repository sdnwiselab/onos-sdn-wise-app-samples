package org.onosproject.gem.topology;

import org.onosproject.net.DeviceId;
import org.onosproject.net.Path;

import java.util.List;

/**
 * Created by aca on 5/4/15.
 */
public interface GeoTopologyService {
    List<GeoNode> geoNodes();

    List<GeoNode> euclideanSteinerPoints();

    GeoNode geoNode(DeviceId deviceId);

    GeoGraph euclideanSteinerGraph();

    List<GeoNode> mappedSteinerPoints();

    Path getIntermediatePath(GeoNode prevNode, GeoNode curNode);

    List<GeoNode> getNextMulticastHops(GeoNode prev, GeoNode cur);
}

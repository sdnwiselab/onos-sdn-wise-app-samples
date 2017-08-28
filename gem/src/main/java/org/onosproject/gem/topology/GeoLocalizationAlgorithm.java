package org.onosproject.gem.topology;

import org.onosproject.net.SensorNodeId;
import org.onosproject.net.SensorNodeLocalization;

/**
 * Created by aca on 5/27/15.
 */
public class GeoLocalizationAlgorithm implements SensorNodeLocalization {
//    double xCoord;
//    double yCoord;
//    double zCoord;

//    public GeoLocalizationAlgorithm(SensorNodeId sensorNodeId) {
//        GeoLocationAccess geoLocationAccess = GeoLocationAccess.getInstance();
//        double[] coordinates = geoLocationAccess.getSensorNodeCoordinates(sensorNodeId).toArray();
//        xCoord = coordinates[0];
//        yCoord = coordinates[1];
//        zCoord = coordinates[2];
//    }

    @Override
    public double[] xyzCoordinates(SensorNodeId sensorNodeId) {
        GeoLocationAccess geoLocationAccess = GeoLocationAccess.getInstance();
        double[] coordinates = geoLocationAccess.getSensorNodeCoordinates(sensorNodeId).toArray();
        return coordinates;
    }
}

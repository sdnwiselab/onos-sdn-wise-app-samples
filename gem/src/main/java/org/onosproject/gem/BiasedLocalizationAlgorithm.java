package org.onosproject.gem;

import org.onosproject.gem.topology.GeoLocationAccess;
import org.onosproject.net.SensorNodeId;
import org.onosproject.net.SensorNodeLocalization;

import java.util.Random;

/**
 * Created by aca on 6/21/15.
 */
public class BiasedLocalizationAlgorithm implements SensorNodeLocalization {
    public static double maxErrorShift = 0.1;
    public static double maxRadius = 16;
    @Override
    public double[] xyzCoordinates(SensorNodeId sensorNodeId) {
        GeoLocationAccess geoLocationAccess = GeoLocationAccess.getInstance();
        double[] coordinates = geoLocationAccess.getSensorNodeCoordinates(sensorNodeId).toArray();

        double[] biasedCoordinates = new double[coordinates.length];
        Random random = new Random(sensorNodeId.hashCode());
        double randomNumber = random.nextDouble();
        double biasFactor = maxErrorShift * randomNumber;
        boolean add = true;
        if (randomNumber > 0.5) {
            add = false;
        }
        for (int i = 0; i < coordinates.length; i++) {
            if (add) {
                biasedCoordinates[i] = coordinates[i] + biasFactor * maxRadius;
            } else {
                biasedCoordinates[i] = coordinates[i] - biasFactor * maxRadius;
            }
        }

//        System.out.println("Error shift = " + maxErrorShift);
//        System.out.println("Coordinates = " + Arrays.toString(coordinates));
//        System.out.println("Biased Coordinates = " + Arrays.toString(biasedCoordinates));

        return biasedCoordinates;
    }
}

package org.onosproject.gem.topology;

/**
 * Created by aca on 5/27/15.
 */
public class GeoCoordinates {
    private double x;
    private double y;
    private double z;

    public GeoCoordinates(double xCoord, double yCoord) {
        this.x = xCoord;
        this.y = yCoord;
        this.z = 0;
    }

    public GeoCoordinates(double xCoord, double yCoord, double zCoord) {
        this.x = xCoord;
        this.y = yCoord;
        this.z = zCoord;
    }

    public double getXCoord() {
        return x;
    }

    public double getYCoord() {
        return y;
    }

    public double getZCoord() {
        return z;
    }

    public double[] toArray() {
        double[] coords = new double[3];
        coords[0] = x;
        coords[1] = y;
        coords[2] = z;

        return coords;
    }
}

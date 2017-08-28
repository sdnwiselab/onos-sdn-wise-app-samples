package org.onosproject.gem.topology;

import org.onlab.packet.MacAddress;
import org.onosproject.net.SensorNodeId;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by aca on 5/27/15.
 */
public class GeoLocationAccess {
    private static GeoLocationAccess geoLocationAccess = null;

    private final String filename = "/home/aca/Development/JAVA/OSGi/apache-karaf-3.0.2/coordinates.dat";
    private Map<String, GeoCoordinates> coordinates;

    public static GeoLocationAccess getInstance() {
        if (geoLocationAccess == null) {
            geoLocationAccess = new GeoLocationAccess();
        }
        return geoLocationAccess;
    }

    protected GeoLocationAccess() {
        this.coordinates = new HashMap<>();

        try {
            Scanner scanner = new Scanner(new FileInputStream(filename));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] split = line.split("\t");
                int netId = Integer.parseInt(split[0]);
                String addrHigh = split[1].substring(0, split[1].indexOf("."));
                String addrLow = split[1].substring(split[1].indexOf(".") + 1);
                byte addrH = Byte.parseByte(addrHigh);
                byte addrL = Byte.parseByte(addrLow);
                MacAddress macAddress = generateMacAddress(netId, addrH, addrL);
                SensorNodeId sensorNodeId = SensorNodeId.sensorNodeId(macAddress, netId);
                double x = Double.parseDouble(split[2]);
                double y = Double.parseDouble(split[3]);
                double z = Double.parseDouble(split[4]);
                GeoCoordinates geoCoordinates = new GeoCoordinates(x, y, z);
                coordinates.put(sensorNodeId.toString(), geoCoordinates);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private MacAddress generateMacAddress(int netId, byte addrH, byte addrL) {
        byte[] suffix = {addrH, addrL};

        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byte[] prefix = byteBuffer.putInt(netId).array();

        byte[] mac = new byte[6];
        System.arraycopy(prefix, 0, mac, 0, 4);
        System.arraycopy(suffix, 0, mac, 4, 2);
        MacAddress macAddress = new MacAddress(mac);

        return macAddress;
    }

    public GeoCoordinates getSensorNodeCoordinates(SensorNodeId id) {
        return coordinates.get(id.toString());
    }
}

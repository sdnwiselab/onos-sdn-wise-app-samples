package org.onosproject.mapreduce.function;

import com.github.sdnwiselab.sdnwise.flowtable.FlowTableEntry;
import com.github.sdnwiselab.sdnwise.function.FunctionInterface;
import com.github.sdnwiselab.sdnwise.packet.NetworkPacket;
import com.github.sdnwiselab.sdnwise.util.Neighbor;
import com.github.sdnwiselab.sdnwise.util.NodeAddress;
import com.google.common.graph.Network;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by aca on 9/16/15.
 */
public final class MapFunction extends TimerTask implements FunctionInterface {
    private NetworkPacket np;
    private ArrayBlockingQueue<NetworkPacket> flowTableQueue;
    private byte packetType;
    private byte key;


    public MapFunction(byte packetType, byte key, NetworkPacket np, ArrayBlockingQueue<NetworkPacket> flowTableQueue) {
        this.packetType = packetType;
        this.key = key;
        this.np = np;
        this.flowTableQueue = flowTableQueue;
    }

    @Override
    public void function(HashMap<String, Object> adcRegister,
                         List<FlowTableEntry> flowTable,
                         Set<Neighbor> neighborTable,
                         ArrayList<Integer> statusRegister,
                         List<NodeAddress> acceptedId,
                         ArrayBlockingQueue<NetworkPacket> flowTableQueue,
                         ArrayBlockingQueue<NetworkPacket> txQueue,
                         byte[] bytes,
                         NetworkPacket np) {

        // Empty the state
        int size = statusRegister.size();
        statusRegister.clear();
        System.out.println("Status size is " + size);
        for (int i = 0; i < size; i++) {
            statusRegister.add(0);
        }

        this.np = np;
        this.flowTableQueue = flowTableQueue;
        byte packType = getPacketType(np);
        byte[] keys = getKeys(np);

        Timer timer = new Timer();
        for (byte curKey : keys) {
            int dt = new Random().nextInt(300);
            System.out.println("Scheduling MAP for key " + curKey);
            timer.schedule(new MapFunction(packType, curKey, np, flowTableQueue), (1000 + dt), 60000);
//            timer.schedule(new MapFunction(packType, curKey, np, flowTableQueue), (1000 + dt));
        }
    }


    private byte[] getPayload(NetworkPacket np){
        return Arrays.copyOfRange(np.toByteArray(), NetworkPacket.DFLT_HDR_LEN, np.getLen());
    }


    private byte getPacketType(NetworkPacket np) {
        return getPayload(np)[0];
    }

    private byte[] getKeys(NetworkPacket np) {
        int keyLength = (np.getLen() - 10 - 1);
        byte[] keys = new byte[keyLength];

        byte[] payload = getPayload(np);
        for (int j = 1; j < payload.length; j++) {
            keys[j - 1] = payload[j];
        }

        return keys;
    }

    @Override
    public void run() {
        System.out.println("Executing MAP for key " + key);
        // Generate a random value in the range 15-30
        Random random = new Random();
        int dt = random.nextInt(15);
        byte[] temp = new byte[6];
        temp[0] = key;

//        temp[1] = (byte) (dt + 15);
//        byte[] buf = ByteBuffer.allocate(4).putInt(dt + 15).array();
//        System.arraycopy(buf, 0, temp, 1, 4);
        temp[1] = 0;
        temp[2] = 0;
        temp[3] = 0;
        temp[4] = (byte) (dt + 15);
        temp[5] = 0;

        // Create a network packet with src and dst the current node
        NetworkPacket networkPacket = new NetworkPacket(np.getNet(), np.getDst(), np.getDst());
        networkPacket.setTyp(packetType);
        networkPacket.setTtl((byte) 100);
        networkPacket.setLen((byte) (10 + temp.length));

        byte[] tmp = networkPacket.toByteArray();

        if (temp.length + NetworkPacket.DFLT_HDR_LEN <= NetworkPacket.MAX_PACKET_LENGTH) {
            System.arraycopy(temp, 0, tmp, NetworkPacket.DFLT_HDR_LEN, temp.length);
            tmp[NetworkPacket.LEN_INDEX] = ((byte) (temp.length + NetworkPacket.DFLT_HDR_LEN));
        } else {
            throw new IllegalArgumentException("Payload exceeds packet size");
        }

        networkPacket.setArray(tmp);

        // Send the packet to the flowtable
        flowTableQueue.add(networkPacket);
    }
}

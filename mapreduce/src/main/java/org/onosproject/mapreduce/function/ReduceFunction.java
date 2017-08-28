package org.onosproject.mapreduce.function;

import com.github.sdnwiselab.sdnwise.flowtable.FlowTableEntry;
import com.github.sdnwiselab.sdnwise.function.FunctionInterface;
import com.github.sdnwiselab.sdnwise.packet.NetworkPacket;
import com.github.sdnwiselab.sdnwise.util.Neighbor;
import com.github.sdnwiselab.sdnwise.util.NodeAddress;

import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.IntStream;

/**
 * Created by aca on 9/18/15.
 */
public final class ReduceFunction implements FunctionInterface {
    private static JFrame frame = null;
    private static Box box = null;

    @Override
    public void function(HashMap<String, Object> adcRegister,
                         ArrayList<FlowTableEntry> flowTable,
                         ArrayList<Neighbor> neighborTable,
                         ArrayList<Integer> statusRegister,
                         ArrayList<NodeAddress> acceptedId,
                         ArrayBlockingQueue<NetworkPacket> flowTableQueue,
                         ArrayBlockingQueue<NetworkPacket> txQueue,
                         int arg1,
                         int arg2,
                         int arg3,
                         NetworkPacket np) {

        int valueArrayBeginPos = arg1;
        int nofNodes = arg2;
        int packetType = arg3;
        int valueArrayEndPos = valueArrayBeginPos + nofNodes - 1;

        if (frame == null) {
            String nodeId = "" + np.getDst().getLow();
            frame = new JFrame("REDUCE Log for Node " + nodeId);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 300);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
            box = Box.createVerticalBox();
            frame.add(new JScrollPane(box));
        }

        IntStream statusStream = statusRegister.stream().mapToInt(value -> value.intValue());
        int[] state = statusStream.toArray();
        OptionalDouble resDouble = Arrays.stream(state, valueArrayBeginPos, valueArrayEndPos).average();

        String msg = "REDUCE function called and calculated average " + resDouble.getAsDouble()
                + " for KEY " + np.getPayload()[0];

        JLabel label = new JLabel(msg);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        box.add(label);
        frame.revalidate();
        frame.repaint();
        box.scrollRectToVisible(new Rectangle(label.getX(), label.getY(), frame.getWidth(), frame.getHeight()));
        frame.revalidate();
        frame.repaint();

        byte[] res = ByteBuffer.allocate(Double.BYTES).putDouble(resDouble.getAsDouble()).array();

        byte[] payload = new byte[res.length + 1];
        payload[0] = np.getPayload()[0];
        System.arraycopy(res, 0, payload, 1, res.length);

        // Create a network packet with src and dst the current node
        NetworkPacket networkPacket = new NetworkPacket(np.getNetId(), np.getDst(), np.getDst());
        networkPacket.setType((byte) packetType);
        networkPacket.setTtl((byte) 100);
        networkPacket.setLen((byte) (10 + payload.length));
        networkPacket.setPayload(payload);

        // Send the packet to the flowtable
        flowTableQueue.add(networkPacket);

        // Reset the state
        int arrivalsArrBeginPos = valueArrayEndPos + 1;
        int arrivalsArrEndPos = arrivalsArrBeginPos + nofNodes;
        for (int i = arrivalsArrBeginPos; i < (arrivalsArrEndPos + 2); i++) {
            statusRegister.set(i, 0);
        }
    }
}

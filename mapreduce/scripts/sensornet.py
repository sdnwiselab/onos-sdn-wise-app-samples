import math

from mininet.link import TCLink
from mininet.net import Mininet
from mininet.node import RemoteController, OVSSwitch

from sensornode import SensorNode


def get_rssi(distance):
    return int(-58 * math.log10(distance) + 255)


class MiniSensorNet(Mininet):
    def __init__(self):
        super(MiniSensorNet, self).__init__(controller=RemoteController, switch=OVSSwitch, link=TCLink)
        self.sensorNodes = {}
        self.proxyNodes = {}
        self.proxyCnt = 0
        self.filePrefix = 'cfg/Node'
        self.gwLinks = 0
        self.switchGWPort = 1000
        self.udpProxyPort = 50000
        self.tcpProxyPort = 40000
        self.sensorNodePort = 7000

    def add_sensor(self, name, node_id, net_id=1, x=None, y=None):
        host = self.addHost(name)
        sensor_node = SensorNode(node_id, net_id, x, y)
        self.sensorNodes[host] = sensor_node
        return host

    def add_link_sensor_switch(self, name, node_id, switch, controller_ip, controller_port, switch_port=None, net_id=1,
                               x=None, y=None):
        # This is the gateway; subnet 1 is reserved for this reason
        self.gwLinks = self.gwLinks + 1
        gw_ip = '10.0.1.' + str(self.gwLinks)
        host = self.addHost(name, ip=str(gw_ip + '/24'), defaultRoute='via 10.0.1.254')
        sensor_node = SensorNode(node_id, net_id, x, y)
        sensor_node.gw_ip = gw_ip
        if switch_port is not None:
            sensor_node.switch_port = switch_port
            ss_link = self.addLink(host, switch, port2=switch_port)
        else:
            sensor_node.switch_port = self.switchGWPort
            ss_link = self.addLink(host, switch, port2=self.switchGWPort)
        self.gwLinks = self.gwLinks + 1
        sensor_node.setup_gw(controller_ip, controller_port, ss_link.intf1, switch)
        self.sensorNodes[host] = sensor_node
        return host

    def add_link_sensor_sensor(self, src, dst, rssi=None):
        link = self.addLink(src, dst)
        subnet_id = self.next_available_common_subnet(src, dst)
        ip1 = '10.0.' + str(subnet_id) + '.1'
        full_ip1 = ip1 + '/24'
        ip2 = '10.0.' + str(subnet_id) + '.2'
        full_ip2 = ip2 + '/24'

        link.intf1.setIP(str(full_ip1))
        link.intf2.setIP(str(full_ip2))

        port1 = self.sensorNodePort + self.sensorNodes.keys().index(src)
        port2 = self.sensorNodePort + self.sensorNodes.keys().index(dst)

        src_sensor = self.sensorNodes[src]
        dst_sensor = self.sensorNodes[dst]

        src_sensor.ip = ip1
        src_sensor.port = port1
        dst_sensor.ip = ip2
        dst_sensor.port = port2

        print "[%s-%s] Adding a link between %s and %s" % (src_sensor.node_id, dst_sensor.node_id, full_ip1, full_ip2)

        distance = src_sensor.euclidean_distance(dst_sensor)
        link_rssi = 200

        if distance != 0:
            link_rssi = get_rssi(distance)

        if rssi is not None:
            link_rssi = rssi

        filename = self.filePrefix + '0.' + str(src_sensor.node_id) + '.cfg'
        line = '0.' + str(dst_sensor.node_id) + ',' + str(dst_sensor.ip) + ',' + str(
            dst_sensor.port) + ',' + str(link_rssi) + '\n'
        with open(filename, "a") as nodeConfigFile:
            nodeConfigFile.write(line)
            nodeConfigFile.close()

        filename = self.filePrefix + '0.' + str(dst_sensor.node_id) + '.cfg'
        line = '0.' + str(src_sensor.node_id) + ',' + str(src_sensor.ip) + ',' + str(
            src_sensor.port) + ',' + str(link_rssi) + '\n'
        with open(filename, "a") as nodeConfigFile:
            nodeConfigFile.write(line)
            nodeConfigFile.close()
        return link

    def next_available_common_subnet(self, src, dst):
        src_sensor = self.sensorNodes[src]
        dst_sensor = self.sensorNodes[dst]
        for i in range(2, 254):
            if src_sensor.is_subnet_available(i) and dst_sensor.is_subnet_available(i):
                src_sensor.allocate_subnet(i)
                dst_sensor.allocate_subnet(i)
                return i
        return -1

    def start_sdnwise(self):
        port_cnt = 7000
        for h, s in self.sensorNodes.iteritems():
            filename = self.filePrefix + '0.' + s.node_id + '.cfg'
            port_cnt = port_cnt + 1
            if s.is_gw:
                switch_dpid = str(s.proxy_switch.defaultDpid())
                cmd = 'java -jar jar/sdn-wise-data.jar ' +\
                      '-n %s -a %s -p %s -c %s:%s -t %s -sd %s -sm %s -sp %s -l FINEST &' %\
                      (s.net_id,
                       s.node_id,
                       s.port,
                       s.controller_ip,
                       s.controller_port,
                       filename,
                       str(switch_dpid)[8:],
                       str(s.proxy_intf.MAC()),
                       str(s.switch_port))
            else:
                cmd = 'java -jar jar/sdn-wise-data.jar ' +\
                      '-n %s -a %s -p %s -t %s -l FINEST &' %\
                      (s.net_id,
                       s.node_id,
                       s.port,
                       filename)

            print('Starting node %s' % s.node_id)
            print(cmd)
            h.cmd(cmd)

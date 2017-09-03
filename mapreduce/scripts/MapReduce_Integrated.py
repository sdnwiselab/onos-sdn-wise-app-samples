#!/usr/bin/python

from threading import Thread
from time import sleep

from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.node import Node

from sensornet import MiniSensorNet
import glob
import os

# TODO change it with the IP address of the controller
CONTROLLER_IP_ADDRESS = '192.168.64.147'

def fix_network_manager(root, intf):
    cfile = '/etc/network/interfaces'
    line = '\niface %s inet manual\n' % intf
    config = open(cfile).read()
    if line not in config:
        print '*** Adding', line.strip(), 'to', cfile
        with open(cfile, 'a') as f:
            f.write(line)
        root.cmd('service network-manager restart')


def start_nat(root, inet_intf='eth0', subnet='10.0.1/24'):
    # Identify the interface connecting to the mininet network
    local_intf = root.defaultIntf()
    print('Root local IP: ' + local_intf.IP())

    # Flush any currently active rules
    root.cmd('iptables -F')
    root.cmd('iptables -t nat -F')

    # Create default entries for unmatched traffic
    root.cmd('iptables -P INPUT ACCEPT')
    root.cmd('iptables -P OUTPUT ACCEPT')
    root.cmd('iptables -P FORWARD DROP')

    # Configure NAT
    root.cmd('iptables -I FORWARD -i', local_intf, '-d', subnet, '-j DROP')
    root.cmd('iptables -A FORWARD -i', local_intf, '-s', subnet, '-j ACCEPT')
    root.cmd('iptables -A FORWARD -i', inet_intf, '-d', subnet, '-j ACCEPT')
    root.cmd('iptables -t nat -A POSTROUTING -o ', inet_intf, '-j MASQUERADE')

    # Instruct the kernel to perform forwarding
    root.cmd('sysctl net.ipv4.ip_forward=1')


def run(net):
    # Delete existing .cfg files
    files = glob.glob("cfg/*.cfg")
    for f in files:
        os.remove(f)

    c0 = net.addController('c0', ip=CONTROLLER_IP_ADDRESS)
    sensors = {}
    network_size = 23

    for i in range(1, network_size - 1):
        sensor_name = 'sens' + str(i)
        sensor_id = str(i)
        sensors[i] = net.add_sensor(sensor_name, sensor_id)

    s1 = net.addSwitch('s1')
    s2 = net.addSwitch('s2')
    s3 = net.addSwitch('s3')
    s4 = net.addSwitch('s4')
    s5 = net.addSwitch('s5')
    s6 = net.addSwitch('s6')

    net.addLink(s1, s2)
    net.addLink(s1, s5)
    net.addLink(s2, s3)
    net.addLink(s2, s5)
    net.addLink(s3, s4)
    net.addLink(s5, s6)

    print('Starting net')

    # connect sinks to core network
    sensors[0] = net.add_link_sensor_switch('sens0', '0', s1, CONTROLLER_IP_ADDRESS, '9999', 1000)
    sensors[22] = net.add_link_sensor_switch('sens22', '22', s4, CONTROLLER_IP_ADDRESS, '9999', 1001)

    # connect host
    h1 = net.addHost('h1', ip='10.0.1.253/24', defaultRoute='via 10.0.1.254')
    net.addLink(h1, s6)
    h2 = net.addHost('h2', ip='10.0.1.252/24', defaultRoute='via 10.0.1.254')
    net.addLink(h2, s6)

    # Create a node in root namespace
    root = Node('root', inNamespace=False)
    fix_network_manager(root, 'root-eth0')
    rootip = '10.0.1.254'
    subnet = '10.0/8'
    link = net.addLink(root, s5)
    prefix_len = subnet.split('/')[1]
    link.intf1.setIP(rootip, prefix_len)

    net.start()

    print('Starting switches')
    s1.start([c0])
    s2.start([c0])
    s3.start([c0])
    s4.start([c0])
    s5.start([c0])
    s6.start([c0])
    print('Switches started')

    # connect nodes
    net.add_link_sensor_sensor(sensors[0], sensors[1])
    net.add_link_sensor_sensor(sensors[0], sensors[2])
    net.add_link_sensor_sensor(sensors[0], sensors[6])
    net.add_link_sensor_sensor(sensors[1], sensors[3])
    net.add_link_sensor_sensor(sensors[1], sensors[4])
    net.add_link_sensor_sensor(sensors[2], sensors[5])
    net.add_link_sensor_sensor(sensors[5], sensors[7])
    net.add_link_sensor_sensor(sensors[6], sensors[8])
    net.add_link_sensor_sensor(sensors[7], sensors[10])
    net.add_link_sensor_sensor(sensors[8], sensors[9])
    net.add_link_sensor_sensor(sensors[8], sensors[11])
    net.add_link_sensor_sensor(sensors[8], sensors[13])
    net.add_link_sensor_sensor(sensors[9], sensors[10])
    net.add_link_sensor_sensor(sensors[10], sensors[12])
    net.add_link_sensor_sensor(sensors[11], sensors[12])
    net.add_link_sensor_sensor(sensors[13], sensors[14])
    net.add_link_sensor_sensor(sensors[13], sensors[17])
    net.add_link_sensor_sensor(sensors[14], sensors[15])
    net.add_link_sensor_sensor(sensors[15], sensors[16])
    net.add_link_sensor_sensor(sensors[16], sensors[17])
    net.add_link_sensor_sensor(sensors[17], sensors[18])
    net.add_link_sensor_sensor(sensors[17], sensors[22])
    net.add_link_sensor_sensor(sensors[18], sensors[19])
    net.add_link_sensor_sensor(sensors[19], sensors[20])
    net.add_link_sensor_sensor(sensors[20], sensors[21])
    net.add_link_sensor_sensor(sensors[21], sensors[22])

    start_nat(root)
    net.start_sdnwise()
    h1.cmd('java -jar jar/UDPServer.jar 10.0.1.253 6000 &')
    CLI(net)
    net.stop()


if __name__ == '__main__':
    setLogLevel('info')
    network = MiniSensorNet()
    t = Thread(target=run, args=[network])
    t.daemon = True
    t.start()
    sleep(600)
    network.stop()

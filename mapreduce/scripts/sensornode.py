import math


class SensorNode:
    def __init__(self, node_id, net_id=1, x=None, y=None):
        self.subnets = {}
        self.x = -1
        self.y = -1
        self.ip = ''
        self.controller_ip = None
        self.controller_port = None
        self.gw_ip = ''
        self.port = 0
        self.is_gw = False
        self.ctrl_ip = ''
        self.ctrl_port = 0
        self.proxy = None
        self.proxy_switch = None
        self.node_id = node_id
        self.net_id = net_id
        self.proxy_intf = None
        self.switch_port = None
        if x is not None and y is not None:
            self.x = x
            self.y = y
        for i in range(1, 254):
            self.subnets[i] = 0

    def setup_gw(self, controller_ip, controller_port, proxy_intf, proxy_switch):
        self.is_gw = True
        self.controller_ip = controller_ip
        self.controller_port = controller_port
        self.proxy_switch = proxy_switch
        self.proxy_intf = proxy_intf

    def allocate_subnet(self, subnet_index):
        self.subnets[subnet_index] = 1

    def is_subnet_available(self, subnet_index):
        return self.subnets[subnet_index] == 0

    def euclidean_distance(self, other):
        pow1 = math.pow(self.x - other.x, 2)
        pow2 = math.pow(self.y - other.y, 2)
        return math.sqrt(pow1 + pow2)

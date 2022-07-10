# -*- coding: utf-8 -*-
"""
Created on Sun Apr 25 16:33:08 2021

@author: chenj
"""

import config
from utils import cal_bandwidth, lru_update


class aqc_agent():
    def __init__(self):
        self.CLIENT_NUM = config.CLIENT_NUM
        self.BITRATE_LEVELS = config.BITRATE_LEVELS
        self.GAMMA = config.GAMMA
        self.safety_margin = 0.9
        self.RESERVE = 0 # 0.75*total_budget/CLIENT_NUM
        self.TILE_SIZE = config.TILE_SIZE
        # # gap to judge whether the total budget is approximate to 0
        # self.gap = 1

        self.lru_index = [i for i in range(config.CLIENT_NUM)]
        self.label = 'FireFly'
        
    # TODO: FireFly algorithm
    def allocate(self,prev_qualities, users):
        self.total_budget = config.RATE_LIMIT_SERVER * self.safety_margin
        bandwidth_clients = [rate*self.safety_margin for rate in config.RATE_LIMIT_CLIENT_EST]
        qualities = prev_qualities.copy()
        for i in range(self.CLIENT_NUM):
            rate_client = cal_bandwidth(qualities[i])
            while(rate_client>=bandwidth_clients[i] and qualities[i] > 1):
                qualities[i] -= 1
                rate_client = cal_bandwidth(qualities[i])
                self.lru_index.remove(i)
                self.lru_index.append(i)
            self.total_budget -= rate_client # min(bandwidth_clients[i],max(self.RESERVE,rate_client))
        if(self.total_budget<0):
            temp_index = [i for i in range(config.CLIENT_NUM)]
            # iterate until there is positive remaining budget or all qualities is 1
            while(self.total_budget<0 and temp_index):
                # lru decrease
                index = lru_update(self.lru_index)
                if qualities[index] > 1:
                    rate_client = cal_bandwidth(qualities[index])
                    self.total_budget += rate_client # min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                    qualities[index] -= 1
                    rate_client = cal_bandwidth(qualities[index])
                    self.total_budget -= rate_client # min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                else:
                    if index in temp_index:
                        temp_index.remove(index)
        else:
            temp_index = [i for i in range(config.CLIENT_NUM)]
            # iterate until the budget is approximate to 0 or all qualities are the highest
            while(temp_index):
                # lru increase
                index = lru_update(self.lru_index)
                if qualities[index] < self.BITRATE_LEVELS and cal_bandwidth(qualities[index]+1) <bandwidth_clients[index]:
                    rate_client = cal_bandwidth(qualities[index])
                    rate_client_next = cal_bandwidth(qualities[index]+1)                    
                    qualities[index] += 1
                    self.total_budget += rate_client # min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                    self.total_budget -= rate_client_next # min(bandwidth_clients[index],max(self.RESERVE,rate_client))
                    if self.total_budget < 0:
                        qualities[index] -= 1
                        self.total_budget += rate_client_next-rate_client
                        if index in temp_index:
                            temp_index.remove(index)
                else:
                    if index in temp_index:
                        temp_index.remove(index)
        return qualities
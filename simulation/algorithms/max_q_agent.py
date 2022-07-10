# -*- coding: utf-8 -*-
"""
Created on Mon Apr 26 20:26:16 2021

@author: chenj
"""

from utils import cal_bandwidth
import config

class max_q_agent():
    def __init__(self):
        self.RATE_LIMIT_SERVER = config.RATE_LIMIT_SERVER
        self.RATE_LIMIT_CLIENT = config.RATE_LIMIT_CLIENT
        self.CLIENT_NUM = config.CLIENT_NUM
        self.BITRATE_LEVELS = config.BITRATE_LEVELS
        self.GAMMA = config.GAMMA
        self.TILE_SIZE = config.TILE_SIZE
        self.safety_margin = 0.9
        self.RESERVE = 0 # 0.75*total_budget/config.CLIENT_NUM
        # gap to judge whether the total budget is approximate to 0
        self.gap = 1
        self.bandwidth_clients = [rate*self.safety_margin for rate in self.RATE_LIMIT_CLIENT]

        self.lru_index = [i for i in range(config.CLIENT_NUM)]
        self.label = 'highest'
    # algorithm that choose the maximal quality for each user
    def allocate(self,prev_qualities,tiles, users):
        qualities = prev_qualities.copy()
        bandwidth_clients = [rate for rate in self.RATE_LIMIT_CLIENT]
        for i in range(self.CLIENT_NUM):
            rate_client = cal_bandwidth(tiles,qualities[i],self.TILE_SIZE)
            while(rate_client>=bandwidth_clients[i] and qualities[i] > 1):
                qualities[i] -= 1
                rate_client = cal_bandwidth(tiles,qualities[i],self.TILE_SIZE)
            if(qualities[i] < self.BITRATE_LEVELS):
                rate_client_higher = cal_bandwidth(tiles,qualities[i]+1,self.TILE_SIZE)
                while(rate_client_higher<=bandwidth_clients[i] and qualities[i] < self.BITRATE_LEVELS):
                    qualities[i] += 1
                    if(qualities[i] < self.BITRATE_LEVELS):
                        rate_client_higher = cal_bandwidth(tiles,qualities[i]+1,self.TILE_SIZE)
        return qualities
    
# -*- coding: utf-8 -*-
"""
Created on Mon Apr 26 20:34:42 2021

@author: chenj
"""

from utils import cal_bandwidth, cal_delay_without_noise
import numpy as np
import config 

class pavq_agent_paper():
    def __init__(self):
        self.RATE_LIMIT_SERVER = config.RATE_LIMIT_SERVER
        self.RATE_LIMIT_CLIENT = config.RATE_LIMIT_CLIENT
        self.CLIENT_NUM = config.CLIENT_NUM
        self.BITRATE_LEVELS = config.BITRATE_LEVELS
        self.ALPHA = config.ALPHA
        self.GAMMA = config.GAMMA
        self.safety_margin = 0.9
        self.RESERVE = 0 # 0.75*total_budget/CLIENT_NUM
        # gap to judge whether the total budget is approximate to 0
        self.gap = 1
        self.bandwidth_clients = [rate*self.safety_margin for rate in self.RATE_LIMIT_CLIENT]
        self.TIME_INTERVAL = config.TIME_INTERVAL
        self.TILE_SIZE = config.TILE_SIZE
        self.lru_index = [i for i in range(config.CLIENT_NUM)]
        self.label = 'pavq paper'
        # self.mean_qualities = [0 for i in range(config.CLIENT_NUM)]
    
    # def update(self,t,qualities):
    #     for i in range(config.CLIENT_NUM):
    #         self.mean_qualities[i] = self.mean_qualities[i] + config.GAMMA*(qualities[i]-self.mean_qualities[i])/(t+config.GAMMA)

    
    #%% gradient descent algorithm
    def allocate(self,prev_qualities,tiles,users):
        qualities = [1 for i in range(self.CLIENT_NUM)]
        bandwidth_clients = [rate*self.safety_margin for rate in self.RATE_LIMIT_CLIENT]
        indexes = [i for i in range(self.CLIENT_NUM)]
        # for i in range(len(indexes)):
            # if len(users[i].train_x)>0:
            #     users[i].delay_model.fit(np.array(users[i].train_x),np.array(users[i].train_y))
            # else:
            #     users[i].delay_model.fit(np.array([[0]]),np.array([[0]]))
        while(len(indexes)!=0):
            mu_n = np.zeros(len(indexes))
            for i in range(len(indexes)):
                index = indexes[i]
                rate_high = cal_bandwidth(tiles, qualities[index]+1,self.TILE_SIZE)
                rate_low = cal_bandwidth(tiles, qualities[index],self.TILE_SIZE)
                # mu_n[i] = (1 - self.ALPHA*(np.round(users[index].delay_model.predict([[rate_high]]).item(0)/self.TIME_INTERVAL)-np.round(users[index].delay_model.predict([[rate_low]]).item(0)/self.TIME_INTERVAL)) \
                #            - 2*self.GAMMA*(qualities[index]-users[index].dynamic_mean)) \
                #             /((rate_high-rate_low)/bandwidth_clients[index]) #TODO: finish it
                delay_portion = cal_delay_without_noise(sum(config.TILE_SIZE[qualities[index]][tiles])/1e6,rate_high,config.RATE_LIMIT_CLIENT[index])/self.TIME_INTERVAL \
                                - cal_delay_without_noise(sum(config.TILE_SIZE[qualities[index]][tiles])/1e6,rate_low,config.RATE_LIMIT_CLIENT[index])/self.TIME_INTERVAL 
                # mu_n[i] = (1 - self.ALPHA*delay_portion \
                #            - 2*self.GAMMA*(qualities[index]-users[index].mean_quality_paper)) \
                #                /(rate_high-rate_low)
                mu_n[i] = (1 - self.ALPHA*delay_portion \
                           - 2*self.GAMMA*(qualities[index]-users[index].mean_quality_paper)) 
            if np.max(mu_n) < 0:
                indexes = []
            else:
                max_index = indexes[np.argmax(mu_n)]
                qualities[max_index] += 1
                if qualities[max_index] == self.BITRATE_LEVELS:
                    indexes.remove(max_index)
                cur_rates = [cal_bandwidth(tiles,quality,self.TILE_SIZE) for quality in qualities]
                if cur_rates[max_index] > bandwidth_clients[max_index] or sum(cur_rates)>self.safety_margin*self.RATE_LIMIT_SERVER:
                    qualities[max_index] -= 1
                    if max_index in indexes:
                        indexes.remove(max_index)

        return qualities
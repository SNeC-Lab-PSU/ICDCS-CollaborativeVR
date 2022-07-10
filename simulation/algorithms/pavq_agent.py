# -*- coding: utf-8 -*-
"""
Created on Mon Apr 26 20:34:42 2021

@author: chenj
"""

from utils import cal_bandwidth, cal_delay_without_noise
import numpy as np
import config 

class pavq_agent():
    def __init__(self):
        self.CLIENT_NUM = config.CLIENT_NUM
        self.BITRATE_LEVELS = config.BITRATE_LEVELS
        self.ALPHA = config.ALPHA
        self.BETA = config.BETA
        self.GAMMA = config.GAMMA
        self.RESERVE = 0 # 0.75*total_budget/CLIENT_NUM
        # gap to judge whether the total budget is approximate to 0
        self.gap = 1
        self.TIME_INTERVAL = config.TIME_INTERVAL
        self.lru_index = [i for i in range(config.CLIENT_NUM)]
        self.label = 'gradient descent'
        self.mu = 0
        self.step_size = 0.00
        self.hist_mu = []
        
    #%% gradient descent algorithm
    def allocate(self,prev_qualities,users):
        qualities = [1 for i in range(self.CLIENT_NUM)]
        bandwidth_clients = [rate for rate in config.RATE_LIMIT_CLIENT_EST]
        indexes = [i for i in range(self.CLIENT_NUM)]
        # for i in range(len(indexes)):
            # if len(users[i].train_x)>0:
            #     users[i].delay_model.fit(np.array(users[i].train_x),np.array(users[i].train_y))
            # else:
            #     users[i].delay_model.fit(np.array([[0]]),np.array([[0]]))
        delays = np.zeros(self.CLIENT_NUM)
        for index in range(self.CLIENT_NUM):
            #rate = cal_bandwidth(tiles, qualities[index],self.TILE_SIZE)
            #delays[index] = cal_delay_without_noise(sum(config.TILE_SIZE[qualities[index]][tiles])/1e6,rate,config.RATE_LIMIT_CLIENT[index])
            delays[index] = users[index].next_delay[qualities[index]]
        max_index = 0
        
        while(len(indexes)!=0):
            # calculate the minimum delay and index
            # min_delay = 1e26
            # min_index = -1
            # for index in range(self.CLIENT_NUM):
            #     rate = cal_bandwidth(tiles, qualities[index],self.TILE_SIZE)
            #     delay = cal_delay_without_noise(sum(config.TILE_SIZE[qualities[index]][tiles])/1e6,rate,config.RATE_LIMIT_CLIENT[index])
            #     if delay < min_delay:
            #         min_delay = delay
            #         min_index = index
            rate = cal_bandwidth(qualities[max_index])
            #delays[max_index] = cal_delay_without_noise(sum(config.TILE_SIZE[qualities[max_index]][tiles])/1e6,rate,config.RATE_LIMIT_CLIENT[max_index])
            delays[max_index] = users[max_index].next_delay[qualities[max_index]]
            
            min_index = np.argmin(delays)
            
            mu_n = np.zeros(len(indexes))
            for i in range(len(indexes)):
                index = indexes[i]
                rate_high = cal_bandwidth(qualities[index]+1)
                rate_low = cal_bandwidth(qualities[index])
                # mu_n[i] = (1 - self.ALPHA*(np.round(users[index].delay_model.predict([[rate_high]]).item(0)/self.TIME_INTERVAL)-np.round(users[index].delay_model.predict([[rate_low]]).item(0)/self.TIME_INTERVAL)) \
                #            - 2*self.GAMMA*(qualities[index]-users[index].dynamic_mean)) \
                #             /((rate_high-rate_low)/bandwidth_clients[index]) #TODO: finish it
                
                #delay_portion = (cal_delay_without_noise(sum(config.TILE_SIZE[qualities[index]+1][tiles])/1e6,rate_high,config.RATE_LIMIT_CLIENT[index])/self.TIME_INTERVAL) \
                #                - (cal_delay_without_noise(sum(config.TILE_SIZE[qualities[index]][tiles])/1e6,rate_low,config.RATE_LIMIT_CLIENT[index])/self.TIME_INTERVAL )
                delay_portion = users[index].next_delay[qualities[index]+1]\
                    - users[index].next_delay[qualities[index]]
                
                # mu_n[i] = (1 - self.ALPHA*delay_portion \
                #            - 2*self.GAMMA*(qualities[index]-users[index].dynamic_mean)) \
                #                /(rate_high-rate_low)
                # mu_n[i] = 1 - self.ALPHA*delay_portion \
                #            - 2*self.GAMMA*(qualities[index]-users[index].dynamic_mean)\
                #                - self.mu * (rate_high-rate_low)/config.RATE_LIMIT_SERVER
                if index == min_index:
                    factor = 1 - self.CLIENT_NUM*self.BETA
                else:
                    factor = 1
                mu_n[i] = users[index].est_pred - self.ALPHA*factor*delay_portion \
                           - 2*self.GAMMA*users[index].est_pred*(qualities[index]-users[index].dynamic_mean) 
            if np.max(mu_n) < 0:
                indexes = []
            else:
                max_index = indexes[np.argmax(mu_n)]
                qualities[max_index] += 1
                if qualities[max_index] == self.BITRATE_LEVELS:
                    indexes.remove(max_index)
                cur_rates = [cal_bandwidth(quality) for quality in qualities]
                if cur_rates[max_index] >= bandwidth_clients[max_index] or sum(cur_rates)>config.RATE_LIMIT_SERVER:
                    qualities[max_index] -= 1
                    if max_index in indexes:
                        indexes.remove(max_index)
        # cur_rates = [cal_bandwidth(tiles,quality,self.TILE_SIZE) for quality in qualities]
        # self.mu = self.mu + self.step_size*(sum(cur_rates)-config.RATE_LIMIT_SERVER)
        return qualities
    
    
    
class pavq_agent2():
    def __init__(self):
        self.CLIENT_NUM = config.CLIENT_NUM
        self.BITRATE_LEVELS = config.BITRATE_LEVELS
        self.ALPHA = config.ALPHA
        self.GAMMA = config.GAMMA
        self.safety_margin = 0.9
        self.RESERVE = 0 # 0.75*total_budget/CLIENT_NUM
        # gap to judge whether the total budget is approximate to 0
        self.gap = 1
        self.TIME_INTERVAL = config.TIME_INTERVAL
        self.lru_index = [i for i in range(config.CLIENT_NUM)]
        self.label = 'delay only'
        self.mu = 0
        self.step_size = 0.00
        self.hist_mu = []
        
    #%% gradient descent algorithm
    def allocate(self,prev_qualities,users):
        qualities = [1 for i in range(self.CLIENT_NUM)]
        bandwidth_clients = [rate*self.safety_margin for rate in config.RATE_LIMIT_CLIENT]
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
                rate_high = cal_bandwidth(qualities[index]+1)
                rate_low = cal_bandwidth(qualities[index])
                # mu_n[i] = (1 - self.ALPHA*(np.round(users[index].delay_model.predict([[rate_high]]).item(0)/self.TIME_INTERVAL)-np.round(users[index].delay_model.predict([[rate_low]]).item(0)/self.TIME_INTERVAL)) \
                #            - 2*self.GAMMA*(qualities[index]-users[index].dynamic_mean)) \
                #             /((rate_high-rate_low)/bandwidth_clients[index]) #TODO: finish it
                delay_portion = (cal_delay_without_noise(config.SLOT_SIZE[qualities[index]]/1e6,rate_high,config.RATE_LIMIT_CLIENT[index])/self.TIME_INTERVAL) \
                                - (cal_delay_without_noise(config.SLOT_SIZE[qualities[index]]/1e6,rate_low,config.RATE_LIMIT_CLIENT[index])/self.TIME_INTERVAL )
                # mu_n[i] = (1 - self.ALPHA*delay_portion \
                #            - 2*self.GAMMA*(qualities[index]-users[index].dynamic_mean)) \
                #                /(rate_high-rate_low)
                # mu_n[i] = 1 - self.ALPHA*delay_portion \
                #            - 2*self.GAMMA*(qualities[index]-users[index].dynamic_mean)\
                #                - self.mu * (rate_high-rate_low)/config.RATE_LIMIT_SERVER

                mu_n[i] = users[i].est_pred - self.ALPHA*delay_portion \
                           - 2*self.GAMMA*users[i].est_pred*(qualities[index]-users[index].dynamic_mean) 
            if np.max(mu_n) < 0:
                indexes = []
            else:
                max_index = indexes[np.argmax(mu_n)]
                qualities[max_index] += 1
                if qualities[max_index] == self.BITRATE_LEVELS:
                    indexes.remove(max_index)
                cur_rates = [cal_bandwidth(quality) for quality in qualities]
                if cur_rates[max_index] > bandwidth_clients[max_index] or sum(cur_rates)>self.safety_margin*config.RATE_LIMIT_SERVER:
                    qualities[max_index] -= 1
                    if max_index in indexes:
                        indexes.remove(max_index)
        # cur_rates = [cal_bandwidth(tiles,quality,self.TILE_SIZE) for quality in qualities]
        # self.mu = self.mu + self.step_size*(sum(cur_rates)-config.RATE_LIMIT_SERVER)
        return qualities
# -*- coding: utf-8 -*-
"""
Created on Wed Jun 16 15:24:41 2021

@author: chenj
"""

from utils import cal_bandwidth, cal_delay_without_noise
import numpy as np
import config 

#%% generate a meshgrid for all possible qualities
def gene_all_qualities(q_num,c_num):
    all_qualities = []
    for i in range(q_num**c_num):
        qualities = [1 for j in range(c_num)]
        # transform the index into q_num based number
        q_str = np.base_repr(i,base=q_num)
        # transform the string into integer and put in proper position
        for j in range(len(q_str)):
            # since quality starts from 1, while number starts from 0, add 1 to all numbers
            qualities[c_num-len(q_str)+j] = int(q_str[j])+1
        # make up 1 to remained digits
        for j in range(c_num-len(q_str)):
            qualities[j] = 1
        all_qualities.append(qualities)
    return all_qualities

#%%    
class brute_agent():
    def __init__(self):
        self.label = 'brute force'
        self.all_qualities = gene_all_qualities(config.BITRATE_LEVELS,config.CLIENT_NUM)
        self.time_slot = 0
        
    #%% brute force algorithm
    def allocate(self,prev_qualities, users):
        bandwidth_clients = [rate for rate in config.RATE_LIMIT_CLIENT]
        max_obj = -1e26
        self.time_slot += 1
        
        for qualities in self.all_qualities:
            cur_rates = [cal_bandwidth(quality) for quality in qualities]
            if any(np.array(cur_rates)>=np.array(bandwidth_clients)) or sum(cur_rates)>config.RATE_LIMIT_SERVER:
                continue
            else:
                cur_obj = 0
                for i in range(config.CLIENT_NUM):
                    #delay = int(cal_delay_without_noise(sum(config.TILE_SIZE[qualities[i]][tiles])/1e6,cur_rates[i],config.RATE_LIMIT_CLIENT[i])/config.TIME_INTERVAL) 
                    delay = users[i].next_delay[qualities[i]]
                    # the choice of current quality will not influence estimated mean of last time slot, ignore the (1-delta) part of objective function
                    var_portion = users[i].est_pred * (self.time_slot-1) * ((qualities[i] - users[i].dynamic_mean)**2 )/self.time_slot
                    cur_obj += users[i].est_pred *qualities[i] - config.ALPHA*delay - config.GAMMA*var_portion
        
                if cur_obj>max_obj:
                    opt_qualities = qualities
                    max_obj = cur_obj
        
        return opt_qualities
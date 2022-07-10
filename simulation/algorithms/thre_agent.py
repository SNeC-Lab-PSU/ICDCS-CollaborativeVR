# -*- coding: utf-8 -*-
"""
Created on Sun Apr 25 16:05:02 2021

@author: chenj
"""

from utils import cal_bandwidth
import config 

THRESHOLD_1 = 1
THRESHOLD_2 = 1
THRESHOLD_3 = 0.5

class thre_agent():
    def __init__(self):
        self.RATE_LIMIT_SERVER = config.RATE_LIMIT_SERVER
        self.RATE_LIMIT_CLIENT = config.RATE_LIMIT_CLIENT
        self.CLIENT_NUM = config.CLIENT_NUM
        self.BITRATE_LEVELS = config.BITRATE_LEVELS
        self.TILE_SIZE = config.TILE_SIZE
        self.GAMMA = config.GAMMA
        self.safety_margin = 0.9
        self.lru_index = [i for i in range(config.CLIENT_NUM)]
        self.label = 'Threshold'
    # TODO: threshold based algorithm
    def allocate(self,prev_qualities,tiles,users):
        total_budget = self.RATE_LIMIT_SERVER * self.safety_margin
        bandwidth_clients = [rate*self.safety_margin for rate in self.RATE_LIMIT_CLIENT]
        changed_users = []
        min_delay = 1e26
        min_index = -1
        for i in range(self.CLIENT_NUM):
            temp_delay = users[i].estimated_delay+4*users[i].dev_delay
            if(temp_delay < min_delay):
                min_delay = temp_delay
                min_index = i
        qualities = prev_qualities.copy()
        if(min_delay > THRESHOLD_1 and qualities[min_index] > 1):
            # TODO: decrease only single quality or all qualities
            qualities[min_index] = int(qualities[min_index]/2) # [qualities[i]-1 for i in range(CLIENT_NUM) if qualities[i]>1]
            changed_users.append(min_index)
            self.lru_index.remove(min_index)
            self.lru_index.append(min_index)
        for i in range(self.CLIENT_NUM):
            # TODO: change the unit of delay, cal mean dalay in unit: ms or time slots?
            est_delay = users[i].estimated_delay + 4*users[i].dev_delay
            if est_delay - min_delay > THRESHOLD_2 and qualities[i] > 1:
                qualities[i] = int(qualities[i]/2)
                changed_users.append(i)
                self.lru_index.remove(i)
                self.lru_index.append(i)
        for i in changed_users.copy():
            rate_client = cal_bandwidth(tiles,qualities[i],self.TILE_SIZE)
            total_budget -= rate_client
            # try to limit the rate of changed users
            while(rate_client>=bandwidth_clients[i] and qualities[i] > 1):
                total_budget += rate_client
                qualities[i] -= 1
                rate_client = cal_bandwidth(tiles,qualities[i],self.TILE_SIZE)
                total_budget -= rate_client
                changed_users.remove(i)
                changed_users.append(i)
                self.lru_index.remove(i)
                self.lru_index.append(i)
        unchanged_users = [i for i in self.lru_index if i not in changed_users]
        # limit the rate of unchanged users 
        for index in unchanged_users.copy():
            rate_client = cal_bandwidth(tiles,qualities[index],self.TILE_SIZE)
            total_budget -= rate_client
            while(rate_client>=bandwidth_clients[index] and qualities[index] > 1):
                total_budget += rate_client
                qualities[index] -= 1
                rate_client = cal_bandwidth(tiles,qualities[index],self.TILE_SIZE)
                total_budget -= rate_client
                self.lru_index.remove(index)
                self.lru_index.append(index)
                unchanged_users.remove(index)
                unchanged_users.append(index)
                
        if(total_budget < 0):
            while(total_budget<0 and all(item > 1 for item in qualities)):
                index = changed_users[0]
                rate_client = cal_bandwidth(tiles,qualities[index],self.TILE_SIZE)
                total_budget += rate_client
                qualities[index] -= 1
                rate_client = cal_bandwidth(tiles,qualities[index],self.TILE_SIZE)
                total_budget -= rate_client
                changed_users.remove(index)
                changed_users.append(index)
                self.lru_index.remove(index)
                self.lru_index.append(index)
        
        else: 
            gap = 0.5
            unchanged_users = [i for i in self.lru_index if i not in changed_users]
            temp_qualities = qualities.copy()
            ori_qualities = qualities.copy()
            while(len(unchanged_users)!=0 and total_budget>gap and any(item < self.BITRATE_LEVELS for item in temp_qualities) \
                  # until all qualities are the highest
                and any(cal_bandwidth(tiles, temp_qualities[i]+1, self.TILE_SIZE)<=bandwidth_clients[i] for i in range(self.CLIENT_NUM) if temp_qualities[i] < self.BITRATE_LEVELS)):
                # try to change the rate of unchanged users to the maximal quality
                index = unchanged_users[0]
                cnt = len(users[index].var_set) 
                if cnt == 0 or temp_qualities[index] > self.BITRATE_LEVELS - 1 or cal_bandwidth(tiles, temp_qualities[index]+1, self.TILE_SIZE)>bandwidth_clients[index]:
                    unchanged_users.remove(index)
                    continue
                
                temp_qualities[index] += 1
                new_var = (cnt-1)*users[index].dynamic_var/cnt+(temp_qualities[index]-users[index].dynamic_mean)**2/(cnt+1)
                delta_utility = temp_qualities[index] - ori_qualities[index]-self.GAMMA*len(users[index].var_set)*(new_var-users[index].dynamic_var)
                # iterate over all possible combinations, even ori+1 not satisfy, but ori+2 can satisfy
                if(delta_utility > THRESHOLD_3):
                    rate_client = cal_bandwidth(tiles,qualities[index],self.TILE_SIZE)
                    total_budget += rate_client           
                    
                    rate_client = cal_bandwidth(tiles,temp_qualities[index],self.TILE_SIZE)
                    total_budget -= rate_client
                    if(total_budget > 0 and rate_client < bandwidth_clients[index]):
                        qualities[index] = temp_qualities[index]
                    else:
                        total_budget += rate_client
                    unchanged_users.remove(index)
                    unchanged_users.append(index)
                    self.lru_index.remove(index)
                    self.lru_index.append(index)
        
        return qualities
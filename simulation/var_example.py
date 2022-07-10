# -*- coding: utf-8 -*-
"""
Created on Sun Apr 11 11:44:27 2021
Variance decoupling example
Considering quality and variance

@author: chenj
"""

import numpy as np


def cal_delay(size,rate,limit):  
    if(rate>=limit):
        print("The transmission rate exceeds the limit")
        print("rate: %.3f limit: %.3f"%(rate,limit))
        return 100
    # keep the unity of size and rate unified (e.g., MB and MB/s)
    return max(size*1000/(limit-rate)+np.random.normal(5),0) # unit: ms

T=10

qualities = [1,2,3]

for t in range(T):
    for quality in qualities:
        value = quality 
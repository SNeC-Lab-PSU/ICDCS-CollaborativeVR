# -*- coding: utf-8 -*-
"""
Created on Sun Jul 25 15:43:12 2021

@author: chenj
"""

import numpy as np
import os

TARGET_DURATION = 320*1e6 # unit: microseconds
MIN_THROUGHPUT = 2.5*1e6 # unit: bytes per second 
MAX_THROUGHPUT = 12.5*1e6 # unit: bytes per second 

filenames = os.listdir("./")
filenames = [filename for filename in filenames if 'report' in filename]

dire = './dataset/'
if not os.path.exists(dire):
    os.makedirs(dire)
for filename in filenames:
    with open(filename) as f:
        line = f.readline()
        total_duration = 0
        with open(dire+filename,'w') as w:
            while line:
                tokens = line.split(' ')
                duration = int(tokens[5]) # unit: ms
                throughput = int(tokens[4])*1000/int(tokens[5])
                if throughput > MIN_THROUGHPUT and throughput < MAX_THROUGHPUT:
                    w.write("%d %d\n"%(duration*1000,throughput))
                    total_duration += duration
                line = f.readline()
        if total_duration * 1000 < TARGET_DURATION:
            os.remove(dire+filename)
                    
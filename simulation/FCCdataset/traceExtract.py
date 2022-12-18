# -*- coding: utf-8 -*-
"""
Created on Mon May 17 21:46:24 2021

@author: chenj
"""

import numpy as np
import random
import datetime
import os
import time

np.random.seed(7)
random.seed(7)


FILE_PATH = './curr_webget.csv'
NUM_LINES = np.inf
TIME_ORIGIN = datetime.datetime.utcfromtimestamp(0)
TARGET_CLIENT_NUM = 1
TARGET_DURATION = 320*1e6 # unit: microseconds
TRACE_NUM = 1000
MIN_THROUGHPUT = 2.5*1e6 # unit: bytes per second 
MAX_THROUGHPUT = 12.5*1e6 # unit: bytes per second 

bw_measurements = {}
# bw_measurements_filter = {}
# lines = []
if __name__ == '__main__':
    start_time = time.time()
    line_counter = 0
    with open(FILE_PATH, 'r') as f:
        line = f.readline()
        while line:
            # fcc format: unit_id,dtime,target,address,fetch_time(microsecond),bytes_total,bytes_sec...
            parse = line.split(',')
            # lines.append(parse)
            
            if line_counter == 0:
                line_counter += 1
                line = f.readline()
                continue
            if line_counter%100000 == 0:
                temp_time = time.time() - start_time
                print('%d lines processed, time used: %.3f s'%(line_counter,temp_time))

            # collect same client, same server, regardless of time (not necessarily continous)
            uid = parse[0]
            dtime = (datetime.datetime.strptime(parse[1],'%Y-%m-%d %H:%M:%S') 
             	- TIME_ORIGIN).total_seconds()
            target = parse[2] # domain
            # target = parse[3] # IP address
            duration = parse[4] # fetch time, microsecond
            throughput = parse[6]  # bytes per second
            
            # filter useless data and avoid trivial bitrate selection 
            # bandwidth limit, upper and lower
            if duration == '0' or int(throughput) < MIN_THROUGHPUT or int(throughput) > MAX_THROUGHPUT:
                line_counter += 1
                line = f.readline()
                continue
            
            k = (duration, throughput)
            if target in bw_measurements:
                clients = bw_measurements[target]
                if uid in clients:
                    clients[uid].append(k)
                else:
                    clients[uid] = [k]
            else:
                clients = {}
                clients[uid] = [k]
                bw_measurements[target] = clients
            
            line_counter += 1
            line = f.readline()
            if line_counter >= NUM_LINES:
                break
    read_done_time = time.time()
    print('read data done, total number of lines: %d, time used: %.3f s'%(line_counter, read_done_time-start_time))
    
#%%
    # clear those with insufficient client number or duration
    candidate = []
    for server in bw_measurements:
        if len(bw_measurements[server]) < TARGET_CLIENT_NUM:
            # del bw_measurements[server]
            continue
        clients = bw_measurements[server]
        for uid in clients.copy():
            client = clients[uid]
            duration = 0
            for k in client:
                # if int(k[1])<2.5*1e6:
                #     continue
                duration += int(k[0])
            if duration < TARGET_DURATION:
                del clients[uid]
        if len(bw_measurements[server]) < TARGET_CLIENT_NUM:
            # del bw_measurements[server]
            continue
        candidate.append(server)
        # bw_measurements_filter[server] = clients
    if len(candidate) > TRACE_NUM:
        choices = np.random.choice(candidate, TRACE_NUM)
    else:
        choices = candidate
    cnt = 0
    trace_num = 0
    for server in choices:
        clients = bw_measurements[server]
        dire = 'dataset/'+str(cnt)
        if not os.path.exists(dire):
            os.makedirs(dire)
        for uid in clients:
            out_file = 'trace_' + str(uid) +'.txt'
            out_file = dire + "/" + out_file
            with open(out_file, 'w') as f:
                trace_num += 1
                for k in clients[uid]:
                    f.write(k[0] + ' ' + k[1] + '\n')
        cnt += 1
    gene_done_time = time.time()
    print('%d traces have been generated, time used: %.3f s'%(trace_num, gene_done_time - read_done_time))
    


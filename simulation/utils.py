# -*- coding: utf-8 -*-
"""
Created on Sun Apr 25 16:21:10 2021

@author: chenj
"""

import numpy as np
import config

tile_dict_display = {}
tile_dict_tran = {}
id2pose = {}
pose2id = {}
id2size = {}

# read table for tiles
def read_table(filename,tileDict):
    with open(filename) as f:
        lines = f.readlines()
        for i in range(len(lines)):
            if i%2 == 1:
                tokens = lines[i].split(',')
                tiles = [int(float(tile)) for tile in tokens]
                tileDict[lines[i-1].strip()] = tiles

# read tile ID table corresponding to the pose
def read_ID_table(filename):
    with open(filename) as f:
        lines = f.readlines()
        cnt = 0
        for line in lines:
            strs = line.split(" ")
            videoID = int(strs[0])
            pose = strs[1].strip()
            id2pose[videoID] = pose
            pose2id[pose] = videoID
            cnt+=1
    print(str(cnt)+" ids read.")

# read the tile size table for each tile        
def read_size_table(filename):
    with open(filename) as f:
        lines = f.readlines()
        cnt = 0
        for line in lines:
            strs = line.split(",")
            videoID = int(strs[0])
            size = int(strs[1])
            id2size[videoID] = size
            cnt += 1
    print(str(cnt)+" id sizes read.")
    
# get the float value of orientation from the string
def get_ori(recv_str):
    if ',' in recv_str:
        coor = recv_str.split(",")
    else:
        coor = recv_str.split(" ")
    orientations = np.zeros(2)
    for i in range(2):
        orientations[i] = float(coor[i+2])
    return orientations 

# get the position index from the float value
def get_pos_index(recv_str):
    granular = 5
    if ',' in recv_str:
        posStr = recv_str.split(",")
    else:
        posStr = recv_str.split(" ")
    positions = np.zeros(2)
    for i in range(2):
        positions[i] = cal_pos(float(posStr[i]),granular)
    pos = str(int(positions[0]))+","+str(int(positions[1]))
    return pos

# get the videoID given the pose
def get_videoID(indexPos, tileID, quality):
    pose = indexPos+","+str(tileID)+","+str(quality)
    videoID = pose2id[pose]
    return videoID

def get_total_size_of_pose(pose,quality):
    total_size = 0
    index_pos = get_pos_index(pose)
    ori = get_ori(pose)
    if ori[0] > 90:
        ori[0] = 90
    if ori[0] < -90:
        ori[0] = -90
    coor = "(" + str(int(cal_angle(ori[0]))) + "," + str(int(cal_angle(ori[1]))) + ",0)"
    tiles = tile_dict_tran[coor]
    for tile_id in tiles: 
        videoID = get_videoID(index_pos, tile_id, quality)
        total_size += id2size[videoID]
    return total_size
        
def get_pred_result(predPose, realPose) :
    result = 1

    # requested tiles use real pose
    request_tiles = []
    index_pos = get_pos_index(realPose)
    ori = get_ori(realPose)
    if ori[0] > 90:
        ori[0] = 90
    if ori[0] < -90:
        ori[0] = -90
    coor = "(" + str(int(cal_angle(ori[0]))) + "," + str(int(cal_angle(ori[1]))) + ",0)" 
    tiles = tile_dict_display[coor] 
    for tile_id in tiles:
        videoID = get_videoID(index_pos, tile_id, 19)
        request_tiles.append(videoID)
		
		
 	# get transmitted tiles use predicted pose
    trans_tiles = []
    index_pos = get_pos_index(predPose)
    ori = get_ori(predPose)
    if ori[0] > 90:
        ori[0] = 90
    if ori[0] < -90:
        ori[0] = -90
    coor = "(" + str(int(cal_angle(ori[0]))) + "," + str(int(cal_angle(ori[1]))) + ",0)"
    tiles = tile_dict_tran[coor]
    for tile_id in tiles: 
        videoID = get_videoID(index_pos, tile_id, 19)
        trans_tiles.append(videoID)
		
    # detect whether all requested tiles are transmitted 
    for tileID in request_tiles:
        if tileID not in trans_tiles:
            result = 0
            break
    return result


# filter non-empty data in the dataset
def find_data(data):
    candidate = []
    for i in range(data.shape[0]): 
        for j in range(data.shape[1]):
            if data[i,j].shape[1] != 0:
                candidate.append([i,j])
    return candidate

def cal_angle(degree):
    result =  (degree+180)%360 - 180
    return result
 
def cal_pos(pos,granular):
    return int(pos/granular)*granular

def cal_bandwidth(quality):
    tile_size = config.SLOT_SIZE[quality-1]
    bandwidth = tile_size/(config.TIME_INTERVAL*1e3) #unit: MB/s
    return bandwidth

def cal_delay(size,rate,limit):  
    if(rate>=limit):
        print("The transmission rate exceeds the limit")
        print("rate: %.3f limit: %.3f"%(rate,limit))
        return 100
    # keep the unit of size and rate unified (e.g., MB and MB/s)
    return max(size*1000/(limit-rate),0) # max(size*1000/(limit-rate)+np.random.normal(5),0) # unit: ms

def cal_delay_without_noise(size,rate,limit):  
    if(rate>=limit):
        return 100
    # keep the unity of size and rate unified (e.g., MB and MB/s)
    return max(size*1000/(limit-rate),0) # unit: ms

def modi_quality(total_budget,qualities,index,tiles,flag):
    rate_client = cal_bandwidth(tiles,qualities[index])
    total_budget += rate_client
    qualities[index] += flag
    rate_client = cal_bandwidth(tiles,qualities[index])
    total_budget -= rate_client
    return total_budget, rate_client

def lru_update(lru_index):
    index = lru_index[0]
    del lru_index[0]
    lru_index.append(index)
    return index

def cal_metric(users):
    # min_delay = {}
    # for t in range(int(config.T)):
    #     cur_time = int(t*config.TIME_INTERVAL)
    #     # find same frame based on start time
    #     delays = [np.round((user.frame_pool[cur_time].display_time-cur_time)/config.TIME_INTERVAL)-1 for user in users if user.frame_pool[cur_time].display_time>0]
    #     if delays:
    #         min_delay[cur_time] = min(delays)
    # metric_Qs = []
    # metric_Ds = []
    # metric_Vs = []
    all_metric_Qs = []
    all_metric_Ds = []
    all_metric_Vs = []
    metrics = []
    miss_rates = []
    delays = []
    # record variance of the delay, variance of the metric D
    # mean_delays = [] 
    # var_delays = []
    # mean_metric_Ds = []
    # var_metric_Ds = []
    
    for user in users:
        # metric_Q = 0
        # metric_D = 0
        # metric_V = 0
        miss_cnt = 0
        metric_Q_set = []
        metric_D_set = []
        metric_V_set = []
        metric_set = []
        delay_set = []
        # record variance of the delay, variance of the metric D
        # mean_delay = 0
        # var_delay = 0
        # mean_metric_D = 0
        # var_metric_D = 0
        # delay_cnt = 0
        for t in range(len(user.frame_show)):
            cur_frame = user.frame_show[t] 
            if(cur_frame == 0):
                # miss_cnt += 1
                metric_Q_set.append(-1)
                metric_D_set.append(-1)
                metric_V_set.append(-1)
                delay_set.append(-1)
            else:
                if cur_frame.pred_flag == 1:
                    cur_quality = cur_frame.quality_level
                else:
                    # print('test not enter') # this branch should not be entered based on the metric policy
                    cur_quality = 0
                    miss_cnt += 1
                
                # cur_delay = np.round((cur_frame.display_time - cur_frame.start_time)/config.TIME_INTERVAL)-1
                cur_delay = cur_frame.delay
                cur_metric_D = cur_delay # - config.BETA*min_delay[cur_frame.start_time]
                # cur_metric_D = cur_delay
                # metric_Q += cur_quality
                # metric_D += cur_delay
                metric_Q_set.append(cur_quality)
                metric_D_set.append(cur_metric_D)
                metric_V_set.append(user.var_set[t])
                #TODO: a bit different for variance compared with the formula of the objective function
                # metric = cur_quality - config.ALPHA*cur_metric_D - config.GAMMA*user.var_set[t]
                metric = cur_quality - config.ALPHA*cur_metric_D - config.GAMMA*(cur_quality-user.dynamic_mean)**2
                metric_set.append(metric)
                delay_set.append(cur_delay)
                # record variance of the delay, variance of the metric D
                #mean_delay += cur_frame.display_time - cur_frame.start_time
                # if delay_cnt >= 1:
                #     var_delay = (delay_cnt-1)*var_delay/delay_cnt+(cur_delay-mean_delay)**2/(delay_cnt+1)
                #     mean_delay = (cur_delay + delay_cnt * mean_delay)/(delay_cnt + 1)
                #     var_metric_D = (delay_cnt-1)*var_metric_D/delay_cnt+(cur_metric_D-mean_metric_D)**2/(delay_cnt+1)
                #     mean_metric_D = (cur_metric_D + delay_cnt * mean_metric_D)/(delay_cnt + 1)
                # else:
                #     mean_delay = cur_delay
                #     mean_metric_D = cur_metric_D
                # delay_cnt += 1
        # record variance of the delay, variance of the metric D
        # mean_delay = mean_delay/(end-start-miss_cnt)
        # mean_delays.append(mean_delay)
        # var_delays.append(var_delay)
        # mean_metric_Ds.append(mean_metric_D)
        # var_metric_Ds.append(var_metric_D)
        # metric_Q = metric_Q/(end-start)
        # when calculate delay and synchronization, do not count the missed frame
        # metric_D = metric_D/(end-start-miss_cnt)
        # metric_V = user.var_set[end-1]
        # metric_Qs.append(metric_Q)
        # metric_Ds.append(metric_D)
        # metric_Vs.append(metric_V)
        delays.append(delay_set)
        metrics.append(metric_set)
        # miss_rates.append(miss_cnt/(end-start))
        display = [frame for frame in user.frame_show if frame!=0]
        miss_rates.append((len(user.frame_pool)-len(display)+miss_cnt)/(len(user.frame_pool)))
        all_metric_Qs.append(metric_Q_set)
        all_metric_Ds.append(metric_D_set)
        all_metric_Vs.append(metric_V_set)
    return miss_rates,all_metric_Qs,all_metric_Ds,all_metric_Vs,metrics,delays
    
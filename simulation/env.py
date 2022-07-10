# -*- coding: utf-8 -*-
"""
Created on Wed Mar 17 10:19:44 2021
Add the handler of displaying multiple frames in a time slot
Change the unit of delay from millisecond to time slot
Update the threshold method by changing unit
Optimize the draw figure function 

See the README.txt file for more modifications
@author: chenj
"""

import numpy as np
import random
import matplotlib.pyplot as plt
import itertools
import scipy.io as sio
from algorithms.thre_agent import thre_agent
from algorithms.firefly import aqc_agent
from algorithms.max_q_agent import max_q_agent
from algorithms.pavq_agent import pavq_agent, pavq_agent2
from algorithms.pavq_agent_paper import pavq_agent_paper
from algorithms.cons_agent import cons_agent
from algorithms.approx2 import approx2_agent
from algorithms.brute_force import brute_agent
from def_classes import User, Frame
from utils import cal_bandwidth, cal_metric, cal_delay, cal_delay_without_noise
import config, utils
import copy
import time
import os

random_seed = 10

#%% preparation 
utils.read_table('tile_table_1row_4col.txt',utils.tile_dict_display)
utils.read_table('tile_table_1row_4col_120_150.txt',utils.tile_dict_tran)
utils.read_size_table('../RTP/id2size.txt')
utils.read_ID_table('../id2pose.txt')
random.seed(random_seed)
np.random.seed(random_seed)

# get a trace from the Firefly dataset
traces = []
with open('./traces/OfficeTrace_1.txt') as f:
    lines = f.readlines()
    for line in lines:
        traces.append(line.strip())

# to accelerate the simulation, we read the stored prediction results from the file
pred_traces = []
with open('./traces/pred_pos_office_1.txt') as f:
    lines = f.readlines()
    for line in lines:
        pred_traces.append(line.strip())

# store the prediction results and required tile size
pred_results = []
tile_sizes = np.zeros((config.BITRATE_LEVELS,len(pred_traces)-1))
qualities = [35,31,27,23,19,15]
for i in range(len(pred_traces)-1):
    result = utils.get_pred_result(pred_traces[i], traces[i+1])
    pred_results.append(result)
    for quality in qualities:
        size = utils.get_total_size_of_pose(pred_traces[i], quality)
        tile_sizes[qualities.index(quality)][i] = size
    
#%% prepare for the available network trace from the dataset
# half of the trace from FCC dataset
fcc_num = int(config.TOTAL_TRACE_NUM*config.CLIENT_NUM/2)
fcc_root_dir = 'FCCdataset/dataset/'
fcc_trace_candidate = []
if os.path.exists(fcc_root_dir):
    dir_names = os.listdir(fcc_root_dir)
    dir_names = [dirname for dirname in dir_names if os.path.isdir(fcc_root_dir+dirname) ]
    for dir_name in dir_names:
        trace_files = os.listdir(fcc_root_dir+dir_name)
        trace_files = [fcc_root_dir + dir_name +'/' + file_name for file_name in trace_files]
        fcc_trace_candidate.extend(trace_files)        
trace_files_indexes = np.random.choice(len(fcc_trace_candidate),fcc_num,replace=False)            
fcc_trace_files = np.array(fcc_trace_candidate)[trace_files_indexes]

# half of the trace from 4G dataset
lte_num = config.TOTAL_TRACE_NUM*config.CLIENT_NUM - fcc_num
lte_root_dir = '4Gdataset/dataset/'
lte_trace_candidate = []
if os.path.exists(lte_root_dir):
    trace_files = os.listdir(lte_root_dir)
    trace_files = [lte_root_dir + file_name for file_name in trace_files]
    lte_trace_candidate.extend(trace_files)   
trace_files_indexes = np.random.choice(len(lte_trace_candidate),lte_num)            
lte_trace_files = np.array(lte_trace_candidate)[trace_files_indexes]
trace_files = []
trace_files.extend(fcc_trace_files.tolist())
trace_files.extend(lte_trace_files.tolist())

split_traces_indexes = np.random.choice(np.arange(len(trace_files)),size=(config.TOTAL_TRACE_NUM,config.CLIENT_NUM),replace=False)
trace_files = np.array(trace_files)[split_traces_indexes]

#%%
policies = [2, 4, 9]
mean_qualities_policies = [[] for i in range(len(policies))]
mean_delays_policies = [[] for i in range(len(policies))]
var_delays_policies = [[] for i in range(len(policies))]
mean_metric_Ds_policies = [[] for i in range(len(policies))]
mean_metric_Vs_policies = [[] for i in range(len(policies))]
miss_rates_policies = [[] for i in range(len(policies))]
mean_metrics_policies = [[] for i in range(len(policies))]
display_policies = [0,1]
display_policy = 1

init_time = time.time()
for k in range(config.TOTAL_TRACE_NUM):
    labels = []
    # prepare the network trace for each user in this round
    net_traces = []
    for trace_file in trace_files[k]:
        user_trace = []
        with open(trace_file) as f:
            lines = f.readlines()
            duration = 0
            t = 0
            # random.shuffle(lines)
            for line in lines:
                tokens = line.split(' ')
                temp_duration = int(tokens[0])/1e3 # from microseconds to milliseconds
                throughput = tokens[1]
                throughput_MB = float(int(throughput)/1e6)
                while (t+1)*config.TIME_INTERVAL - duration < temp_duration:
                    # add some variance
                    # user_trace.append(max(throughput_MB + np.random.normal(0,throughput_MB/10),1.356))
                    # without variance
                    user_trace.append(throughput_MB)
                    t += 1
                duration = t*config.TIME_INTERVAL
                if t >= config.T:
                    break
        net_traces.append(user_trace[:int(config.T)])
    net_traces = np.array(net_traces)
    #%%
    policy_cnt = 0
    for policy in policies:
    # policy = 0
    # for display_policy in display_policies:
    #%% main environment
    
        random.seed(random_seed)
        np.random.seed(random_seed)
        start_time = time.time()
        qualities = [3 for i in range(config.CLIENT_NUM)] # initially, all qualities is 3
        lru_index = [i for i in range(config.CLIENT_NUM)]
        users = [User(i,display_policy) for i in range(config.CLIENT_NUM)]
        min_delays = np.zeros(int(config.T)) # record the minimal delay of all users at each time slot
        all_tiles = []
        
        if policy == 0:
            # constant qualities
            agent = cons_agent()
        elif policy == 1:        
            # maximal quality algorithm
            agent = max_q_agent()
        elif policy == 2:
            # firefly aqc algorithm
            agent = aqc_agent()
        elif policy == 3:
            # threshold based algorithm
            agent = thre_agent()
        elif policy == 4 or policy == 6 or policy == 8:
            # practical AVQ algorithm
            agent = pavq_agent()
            if policy == 6:
                agent.label = 'whole frame'
            if policy == 8:
                agent.BETA = 0
                agent.label = 'beta = 0'
        elif policy == 5:
            agent = pavq_agent_paper()
        elif policy == 7:
            agent = pavq_agent2()
        elif policy == 9 or policy == 11:
            agent = approx2_agent()
            if policy == 11:
                agent.delay_pred = 1
        elif policy == 10:
            agent = brute_agent()
        labels.append(agent.label)
        
        for t in range(int(config.T)):
            # prediction result
            pred_result = pred_results[t]
            # if policy != 6:
            #     tiles = utils.tile_dict_tran[ori_format]
            # else:
            #     tiles = [i for i in range(4)]
            # ori_format = "(%d,%d,0)"%(real_angle_X,real_angle_Y)
            # request_tiles = utils.tile_dict_display[ori_format]
            # pred_result = 1
            # for tile in request_tiles:
            #     if not tile in tiles:
            #         pred_result = 0
            #         break
            # force the prediction to be successful
            # if policy == 4 :
            #     pred_result = 1
            # pred_result = 1
            
            # tiles = random.choice(config.TILES_POOL)
            # all_tiles.append(tiles)
            config.SLOT_SIZE = tile_sizes[:,t]
            
            # modify the client rate limit based on the network trace
            config.RATE_LIMIT_CLIENT = list(net_traces[:,t])
            config.RATE_LIMIT_CLIENT_EST = [1*rate for rate in config.RATE_LIMIT_CLIENT]
            
            # predetermine the delay for all clients, all qualities, to be used by following functionalities
            for i in range(config.CLIENT_NUM):
                users[i].cal_delay()
            
            # determine the qualities for each client
            # if(t%config.DECISION_INTERVAL==0): # and t != 0):
                # qualities = agent.allocate(qualities,tiles,users)
                # if policy == 5:
                #     agent.update(t+1,qualities)
            qualities = agent.allocate(qualities,users)
            if sum([cal_bandwidth(quality) for quality in qualities]) > config.RATE_LIMIT_SERVER:
                print("total rate exceeds limit")
                
            # # calculate the delay for each client
            # rate_client = np.zeros(config.CLIENT_NUM)
            # delay_tran = np.zeros(config.CLIENT_NUM)
            # delay_queue_client = np.zeros(config.CLIENT_NUM)
            # # calculate the required rate to transmit the tile packet for each client
            # for i in range(config.CLIENT_NUM):
            #     quality = qualities[i]
            #     tile_size = sum(config.TILE_SIZE[quality][tiles])/1e6 #unit: MB
            #     rate_client[i] = tile_size*config.TARGET_FPS #unit: MB/s
            #     delay_queue_client[i] = cal_delay(tile_size,rate_client[i],config.RATE_LIMIT_CLIENT[i])
                
            # total_tile_size = sum(config.TILE_SIZE[quality][tiles])*config.CLIENT_NUM/1e6 #unit: MB
            # # delay_queue_server = cal_delay(total_tile_size,sum(rate_client),RATE_LIMIT_SERVER)
            # # delay_tran = 5 # tile_size/PKT_SIZE*0.1 #unit: ms
            # delay = config.DELAY_BASE + np.array(delay_queue_client)
            delay = [users[i].next_delay[qualities[i]] for i in range(config.CLIENT_NUM)]
            
            cur_time = int(t*config.TIME_INTERVAL)
            for i in range(config.CLIENT_NUM):
                # generate frame for each user
                frame = Frame(cur_time,qualities[i],delay[i],config.SLOT_SIZE[qualities[i]-1]/1e6,pred_result)
                users[i].update(cur_time,frame)
        end_time = time.time()
        # print("Time used: %.3f s"%(end_time-start_time))
        print("%d round of policy %d finished, time used: %.3f s"%(k, policy_cnt, end_time-init_time))
        
        #%% get metrics
        miss_rates,metric_Qs,metric_Ds,metric_Vs,metrics,delays = cal_metric(users)
        xs = []
        for i in range(config.CLIENT_NUM):
            # split those missed frames from the data
            x = [t for t in range(len(metric_Qs[i])) if metric_Qs[i][t]!= -1]
            xs.append(x)
        
        mean_qualities = [np.mean(np.array(metric_Qs[i])[xs[i]]) for i in range(config.CLIENT_NUM)]
        # mean_qualities = [np.mean(np.array(metric_Qs[i])) for i in range(config.CLIENT_NUM)]
        # print("mean qualities: ",mean_qualities)
        # plt.figure()
        # for i in range(CLIENT_NUM):
        #     label = "client%d"%i
        #     plt.plot(xs[i],np.array(metric_Qs[i])[xs[i]],label=label)
        # plt.legend()
        
        delays = [np.array(delays[i])[xs[i]] for i in range(config.CLIENT_NUM)]
        mean_delays = [np.mean(x) for x in delays]
        var_delays = [np.var(x) for x in delays]
        # print("mean delay: ",mean_delays)
        # print("variance of delays: ",var_delays)
        metric_Ds = [np.array(metric_Ds[i])[xs[i]] for i in range(config.CLIENT_NUM)]
        mean_metric_Ds = [np.mean(x) for x in metric_Ds]
        var_metric_Ds = [np.var(x) for x in metric_Ds]
        # print("mean metric Ds: ",mean_metric_Ds)
        # print("variance of metric Ds: ",var_metric_Ds)
        # plt.figure()
        # for i in range(CLIENT_NUM):
        #     label = "client%d"%i
        #     plt.plot(xs[i],np.array(metric_Ds[i])[xs[i]],label=label)
        # plt.legend()
        
        # high variance due to the missed frames
        # mean_metric_Vs = [np.mean(np.array(metric_Vs[i])[xs[i]]) for i in range(config.CLIENT_NUM)]
        mean_metric_Vs = [np.var(np.array(metric_Qs[i])[xs[i]]) for i in range(config.CLIENT_NUM)]
        # print("mean metric Vs: ",mean_metric_Vs)
        # plt.figure()
        # for i in range(CLIENT_NUM):
        #     label = "client%d"%i
        #     plt.plot(xs[i],np.array(metric_Vs[i])[xs[i]],label=label)
        # plt.legend()
        
        # recalculate the metrics over the whole time horizon
        metrics = [mean_qualities[i] - config.ALPHA*mean_metric_Ds[i] - config.GAMMA*mean_metric_Vs[i] for i in range(config.CLIENT_NUM)]
        mean_metrics = [np.mean(metrics[i]) for i in range(config.CLIENT_NUM)]
        # print("mean metrics: ",mean_metrics)
        
        # print("miss frame rate: ",miss_rates)
    
        mean_qualities_policies[policy_cnt].append(np.mean(mean_qualities))
        mean_delays_policies[policy_cnt].append(np.mean(mean_delays))
        var_delays_policies[policy_cnt].append(np.mean(var_delays))
        mean_metric_Ds_policies[policy_cnt].append(np.mean(mean_metric_Ds))
        mean_metric_Vs_policies[policy_cnt].append(np.mean(mean_metric_Vs))
        miss_rates_policies[policy_cnt].append(np.mean(miss_rates))
        
        mean_metrics_policies[policy_cnt].append(np.mean(mean_metrics))
        
        policy_cnt += 1


#%% draw figures

def cdf(x, label, plot=True, *args, **kwargs):
    x, y = sorted(x), np.arange(len(x)) / len(x)
    return plt.plot(x, y, label=label, *args, **kwargs) if plot else (x, y)

plt.figure(figsize=(16,9))
marker = itertools.cycle((',', '+', '.', 'o', '*')) 
colors = itertools.cycle(('blue', 'red', 'yellow', 'black', 'purple')) 
for i in range(len(policies)):
    cdf(mean_qualities_policies[i],labels[i],color = next(colors))
plt.legend(fontsize=20)
fig_title = 'mean qualities'
title_no_space = '_'.join(fig_title.split(' '))
plt.title(fig_title)
plt.savefig(fig_title+'.png')
sio.savemat(title_no_space+'.mat', {title_no_space: np.array(mean_qualities_policies)})

plt.figure(figsize=(16,9))
colors = itertools.cycle(('blue', 'red', 'yellow', 'black', 'purple')) 
for i in range(len(policies)):
    cdf(mean_metric_Ds_policies[i],labels[i],color = next(colors))
plt.legend(fontsize=20)
fig_title = 'mean delay' #'mean synchronization performance'
title_no_space = '_'.join(fig_title.split(' '))
plt.title(fig_title)
plt.savefig(fig_title+'.png')
sio.savemat(title_no_space+'.mat', {title_no_space: mean_metric_Ds_policies})

# plt.figure(figsize=(16,9))
# colors = itertools.cycle(('blue', 'red', 'yellow', 'black', 'purple')) 
# for i in range(len(policies)):
#     cdf(miss_rates_policies[i],labels[i],color = next(colors))
# plt.legend(fontsize=20)
# fig_title = 'missed frame rate'
# title_no_space = '_'.join(fig_title.split(' '))
# plt.title(fig_title)
# plt.savefig(fig_title+'.png')
# sio.savemat(title_no_space+'.mat', {title_no_space: miss_rates_policies})


plt.figure(figsize=(16,9))
colors = itertools.cycle(('blue', 'red', 'yellow', 'black', 'purple')) 
for i in range(len(policies)):
    cdf(mean_metric_Vs_policies[i],labels[i],color = next(colors))
plt.legend(fontsize=20)
fig_title = 'mean variance'
title_no_space = '_'.join(fig_title.split(' '))
plt.title(fig_title)
plt.savefig(fig_title+'.png')
sio.savemat(title_no_space+'.mat', {title_no_space: mean_metric_Vs_policies})

plt.figure(figsize=(16,9))
colors = itertools.cycle(('blue', 'red', 'yellow', 'black', 'purple')) 
mean_value = np.mean(mean_metrics_policies,axis=1)
median_value = np.median(mean_metrics_policies,axis=1)
percent_value = np.percentile(mean_metrics_policies,95,axis=1)
for i in range(len(policies)):
    cdf(mean_metrics_policies[i],labels[i]+"\n mean: %.3f, median: %.3f, 95%%: %.3f"%(mean_value[i],median_value[i],percent_value[i]),color = next(colors))
plt.legend(fontsize=20)
fig_title = 'mean QoE metrics'
title_no_space = '_'.join(fig_title.split(' '))
plt.title(fig_title)
plt.savefig(fig_title+'.png')
sio.savemat(title_no_space+'.mat', {title_no_space: mean_metrics_policies})

#%% draw bar
# def draw_bar(metric_1,metric_2,label_1,label_2,title,err_1=None,err_2=None):
#     labels = ['user1', 'user2', 'user3', 'user4', 'user5']
    
#     x = np.arange(len(labels))  # the label locations
#     width = 0.35  # the width of the bars
    
#     fig, ax = plt.subplots()
#     if err_1 is None and err_2 is None: 
#         rects1 = ax.bar(x - width/2, metric_1, width, label=label_1)
#         rects2 = ax.bar(x + width/2, metric_2, width, label=label_2)
#     else:
#         rects1 = ax.bar(x - width/2, metric_1, width, yerr=err_1, capsize=5, label=label_1)
#         rects2 = ax.bar(x + width/2, metric_2, width, yerr=err_2, capsize=5, label=label_2)
    
#     # Add some text for labels, title and custom x-axis tick labels, etc.
#     # ax.set_ylabel('delay(ms)')
#     ax.set_xticks(x)
#     ax.set_xticklabels(labels)
#     ax.set_title(title)
#     ax.legend()
    
    
#     def autolabel(rects):
#         """Attach a text label above each bar in *rects*, displaying its height."""
#         for rect in rects:
#             height = rect.get_height()
#             ax.annotate('{}'.format(height),
#                         xy=(rect.get_x() + rect.get_width() / 2, height),
#                         xytext=(0, 3),  # 3 points vertical offset
#                         textcoords="offset points",
#                         ha='center', va='bottom')
    
    
#     autolabel(rects1)
#     autolabel(rects2)
    
#     fig.tight_layout()
    
#     plt.show()
    
# label_1 = labels[0]
# label_2 = labels[1]


# metric_1 = np.round(np.array(mean_qualities_policies[0][0]),3)
# metric_2 = np.round(np.array(mean_qualities_policies[1][0]),3)
# # err_1 = 1.96*np.sqrt(np.array(mean_metric_Vs_policies[0]))
# # err_2 = 1.96*np.sqrt(np.array(mean_metric_Vs_policies[1]))
# title = 'mean qualities'
# draw_bar(metric_1,metric_2,label_1,label_2,title)


# metric_1 = np.round(np.array(mean_delays_policies[0][0]),3)
# metric_2 = np.round(np.array(mean_delays_policies[1][0]),3)
# # err_1 = 1.96*np.sqrt(np.array(var_delays_policies[0]))
# # err_2 = 1.96*np.sqrt(np.array(var_delays_policies[1]))
# title = 'mean delays'
# draw_bar(metric_1,metric_2,label_1,label_2,title)


# metric_1 = np.round(np.array(mean_metric_Vs_policies[0][0]),3)
# metric_2 = np.round(np.array(mean_metric_Vs_policies[1][0]),3)
# title = 'variance of qualities'
# draw_bar(metric_1,metric_2,label_1,label_2,title)


# metric_1 = np.round(np.array(mean_metric_Ds_policies[0][0]),3)
# metric_2 = np.round(np.array(mean_metric_Ds_policies[1][0]),3)
# title = 'mean metric D'
# draw_bar(metric_1,metric_2,label_1,label_2,title)


# metric_1 = np.round(np.array(miss_rates_policies[0][0]),3)
# metric_2 = np.round(np.array(miss_rates_policies[1][0]),3)
# title = 'miss frame rate'
# draw_bar(metric_1,metric_2,label_1,label_2,title)

# metric_1 = np.round(np.array(mean_metrics_policies[0][0]),3)
# metric_2 = np.round(np.array(mean_metrics_policies[1][0]),3)
# title = 'mean metric'
# draw_bar(metric_1,metric_2,label_1,label_2,title)


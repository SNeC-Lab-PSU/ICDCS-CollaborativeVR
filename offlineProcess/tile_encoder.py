# -*- coding: utf-8 -*-
"""
Created on Mon Jun  7 21:22:34 2021
input: png file of the whole frame
argument format: input_folder, output_folder, tile_num_row, tile_num_col
output: 264 file for each tile

@author: chenj
"""

import os

if __name__ == '__main__':
    import sys
    if len(sys.argv) == 6:
        input_folder = sys.argv[1].strip()
        output_location = sys.argv[2].strip()
        tile_num_row = int(sys.argv[3].strip())
        tile_num_col = int(sys.argv[4].strip())
        quality = int(sys.argv[5].strip())
        print('extract frame from \'%s\', put into the folder \'%s\', split the frame into %d rows and %d columns, CRF %d'
              %(input_folder, output_location, tile_num_row, tile_num_col, quality))
    else:
        print("format: input_folder, output_file_loc, tile_row_num, tile_col_num, quality")
        sys.exit()
    filenames = os.listdir(input_folder)
    for filename in filenames:
        for row in range(tile_num_row):
            for col in range(tile_num_col):
                new_filename = filename[:-4]+"tile"+str(row*tile_num_col+col)+"crf"+str(quality)+".264"
                if os.path.exists(output_location+"/"+new_filename):
                    continue
                command = "ffmpeg -i %s -filter:v crop=ceil(in_w/%d/2)*2:ceil(in_h/%d/2)*2:%d*in_w/%d:%d*in_h/%d -c:v libx264 -crf %d -pix_fmt yuv420p %s/%s"\
                    %(input_folder+"/"+filename,tile_num_col,tile_num_row,col,tile_num_col,row,tile_num_row,quality,output_location,new_filename)
                print(command)
                os.system(command)
        # fileNames = os.listdir(output_location)
        # for filename in fileNames:
        #     if 'png' in filename:
        #         command = "ffmpeg -i %s -c:v libx264 -pix_fmt yuv420p %s.264"%(output_location+filename,output_location+filename[:-4])
        #         os.system(command)
        #         if os.path.exists(output_location+filename): 
        #             os.remove(output_location+filename)
    
    # # test encode format
    # dirName = '../serverRate/frameTest/'
    # fileNames = os.listdir(dirName)
    # for filename in fileNames:
    #     if '264' in filename:
    #         command = "ffmpeg -i %s -c:v libx264 -pix_fmt yuv420p %snew%s"%(dirName+filename,dirName,filename)
    #         os.system(command)
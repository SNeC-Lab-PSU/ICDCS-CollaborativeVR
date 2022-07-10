# Offline Processing Code

Attach `EncAll.cs` to a Unity Project to generate all frames in the scene with specific granulity. The output format is `.png`. 

Run `python tile_encoder.py input_folder output_folder tile_num_row tile_num_col` to convert the `.png` file to `.264` file.  

Run `python tileTableGeneObject.py row_num col_num FOV_x FOV_y` to generate a table that judges whether the prediction is successful. 

Run `python videoIDTableGene.py input_folder` to generate a videoID table that links the videoID with all generates tiles. 

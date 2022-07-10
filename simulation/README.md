# Simulation code for the paper. 

Before running this code, you need to download the network trace from [FCC dataset](https://www.fcc.gov/oet/mba/raw-data-releases) and [4G dataset from Ghent University](https://users.ugent.be/~jvdrhoof/dataset-4g/) and store in corresponding folders.

We use several sampled user traces from [Firefly paper]() in the `traces` folder. The format is **POS_X, POS_Z, ORI_X, ORI_Y**. But finally we assume perfect prediction in the simulation. 

To run this code, open your command line and enter `python env.py`. 
Adjust the variable `policies` to compare the performance with different policies. 

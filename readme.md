# Network Analysis of Spotify Playlists

## Data
The Spotify playlists dataset can be downloaded via [here](https://www.kaggle.com/datasets/himanshuwagh/spotify-million)

## How to Run
### Step 1
In network-parser folder, edit **line 30** with the input data directory in `GraphMLGenerator.java`, then run this file.
The network will be stored as `track_graph.graphml`. By default, it will parse first 2 json files from input directory to construct the network.

### Step 2
After graphml file is generated, we need to run the `network_analysis_hyperparameter.ipynb` python file in `final` directory. This file contains code used for hyperparameter tuning.
In the first cell, edit the `track_network_path` variable to point to the graphml file. Then run the cells from top to bottom.
Markdown cells are used to explain the purpose of each section.

### Step 3
After hyperparameter tuning is done, the `network_analysis.ipynb` python file in `final` directory can be run to obtain the accuracy and penalized recommendation scores.
This notebook is structured similarly to `network_analysis_hyperparameter.ipynb`. In the first cell, edit the `track_network_path` variable to point to the graphml file. Then run the cells from top to bottom.
Markdown cells are used to explain the purpose of each section.

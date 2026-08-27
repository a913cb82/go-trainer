# go-trainer

> 9×9 Go vs KataGo HumanSL — pick from *n* moves, get points-based feedback.

![go-trainer screenshot](media/screenshot.png)

## How it plays
Play 9x9 Go vs KataGo HumanSL at chosen rank. Pick from `n` moves (some good, some bad) likely to be chosen by player at chosen rank. Optional instant feedback on selected move.

## Setup

```bash
wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2204/x86_64/cuda-keyring_1.1-1_all.deb
sudo dpkg -i cuda-keyring_1.1-1_all.deb && sudo apt update
sudo apt install cuda-toolkit-12-1 libcudnn8

cd app && npm i
cd ../server && npm i
./server/scripts/download-models.sh
```

Run:

```bash
cd server && KATAGO_MODE=real npm run dev  # :3001
cd app && npm run dev                       # :5173 proxies /api → :3001
curl http://localhost:3001/health
```

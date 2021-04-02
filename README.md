# BinanceProfitTracker
> Track your profits with your Binance export
> 
This is a project for tracking your accumulated profits on trading pairs in Binance.
This program currently only supports USDT pairings.
## How to use
1. Set your api keys
2. Set your data
3. Run the program with the arguments [PAIR, BNBFEE] Example: [BTCUSDT, 0.00075]
> BNBFEE needs to be supplied so the value of BNB does not have to be queried for every transaction
## Set api keys
Run this program to create the config file. This file will be created in your Documents/BinanceProfitTracker
## Set data
You can put your exported trade data in Documents/BinanceProfitTracker/data.xlsx

The format is as follows:

**Date (UTC)**|**Market**|**Type**|**Price**|**Amount**|**Total**|**Fee**|**Fee Coin**
:-----:|:-----:|:-----:|:-----:|:-----:|:-----:|:-----:|:-----:
2021-02-07 22:53:26|ADABTC|BUY|0.00001628|2752|0.04480256|2.752|ADA
2021-02-07 22:53:26|ADABTC|BUY|0.00001628|319|0.00519332|0.319|ADA
# TODO
- Add support for mixing trading pairs. For example: ETHUSDT in conjunction with ETHBTC
- Add more exchange export support
- Add profit calculation for all trading pairs
- UI

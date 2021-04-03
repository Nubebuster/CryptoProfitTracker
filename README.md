# BinanceProfitTracker
> Track your profits with your Binance export file

This is a project for tracking your accumulated profits on trading pairs in Binance.
This program has currently only been tested with USDT pairings. xBTC pairings may be inaccurate. (please tell me if it works, I don't trade with anything other than xUSDT)

## Download
https://github.com/Nubebuster/CryptoProfitTracker/releases
## How to use
1. ~~Set your api keys in the settings tab~~
> This is not needed until more features are added
2. Export your transaction data from Binance
3. Set your data file location in the 'Settings' tab
4. Go to the 'Main' tab and set your pairing. For example: ADAUSDT

## How to export data from Binance

#### For a max range of 3 months:
This will generate a .xlsx file which you can use for this program
1. Go to your trade history
2. Click on the tab 'Trade History'
3. Click on 'Export Recent Trade History'


#### Limitless (max 3 exports per month, binance cap)
This will generate a .csv file which you can use for this program
1. Go to your trade history
2. Click on the tab 'Trade History'
3. Click on 'Generate All Trade Statements'


## TODO
- Add Coinbase export support
- Test accurate valuation for non xUSDT pairs
- Add support for mixing trading pairs. For example: ETHUSDT in conjunction with ETHBTC
- Add more exchange export support
- Add profit calculation for all trading pairs
- UI with graphs to plot your profits over time
- Add data file caching with a button to load and reload data
# Troubleshooting
If the program does not work properly, please run it with a command line to see error stacktraces. 
These stacktraces can be reported in the github issues section of this repository. 
Be sure to explain how to reproduce the problem. Also include stacktraces.
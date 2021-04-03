package com.nubebuster.cryptoprofittracker.data.caching;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nubebuster.cryptoprofittracker.BinanceProfitTracker;
import com.nubebuster.cryptoprofittracker.CryptoProfitTrackerUtils;
import com.nubebuster.cryptoprofittracker.data.FileHandler;

import java.io.File;
import java.util.*;

public class CandleStickCollector {

    private final BinanceProfitTracker client;
    private File cacheFolder;

    public CandleStickCollector(BinanceProfitTracker client) throws Exception {
        cacheFolder = new File(FileHandler.getDocumentsPath(), "cache");
        if (!cacheFolder.exists())
            cacheFolder.mkdir();

        this.client = client;
    }

    private File getFile(String symbol, CandlestickInterval interval) throws Exception {
        return new File(cacheFolder, symbol + "@" + interval.name() + ".json");
    }

    public List<Candlestick> getCandlesCollectWhenMissing(String symbol, long start, long end, CandlestickInterval interval) throws Exception {
        File data = getFile(symbol, interval);
        if (!data.exists()) {
            log("No data for " + symbol + ", querying data from API...");
            List<Candlestick> candles = getCandlesFromInternetAndSave(symbol, start, end, interval);
            saveSticks(symbol, interval, candles);
            log("Successfully queried and saved " + symbol + " data to new file.");
            return candles;
        }

        long startRounded = start - (start % CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(interval));
        long endRounded = end - (end % CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(interval));
        SortedMap<Long, Candlestick> load = loadSticks(symbol, interval);
        if (load.containsKey(startRounded) && load.containsKey(endRounded)) {
            //i have the data start and end but need to see if I am missing data.../ TODO only query the data that is missing, instead of all
            if (checkDataComplete(load, startRounded, endRounded, interval))
                return getSticksInRange(load, start, end);
            else
                log("Missing parts of the data for " + symbol + " in cache. Querying all data from requested period..." );
        }

        List<Candlestick> c = getCandlesFromInternetAndSave(symbol, startRounded, endRounded, interval);
        c.forEach(candle -> load.put(candle.getOpenTime(), candle));
        return getSticksInRange(load, start, end);
    }

    /**
     * @param data
     * @param startRounded
     * @param endRounded
     * @param interval
     * @returns whether data is complete or missing a data point in map
     */
    private boolean checkDataComplete(SortedMap<Long, Candlestick> data, long startRounded, long endRounded, CandlestickInterval interval) {
        long intervalMs = CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(interval);
        int index = 0;
        long end = (endRounded - startRounded) / intervalMs;
        for (; index < end; index++) {
            long i = startRounded + index * intervalMs;
            if (!data.containsKey(i)) return false;
        }
        return true;
    }

    private List<Candlestick> getSticksInRange(SortedMap<Long, Candlestick> map, long start, long end) {
        Long[] keyset = map.keySet().toArray(new Long[map.size()]);
        int startIndex = 1;//starts at 1 to prevent indexoutofbounds
        for (; startIndex < keyset.length; startIndex++) {
            if (keyset[startIndex] > start && keyset[startIndex - 1] < start)
                break;
        }
        int endIndex = 0;
        for (; endIndex < keyset.length - 1; endIndex++) {//-1 to prevent indexoutofbounds
            if (keyset[endIndex] < end && keyset[endIndex + 1] > end)
                break;
        }
        List<Candlestick> candles = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            candles.add(map.get(keyset[i]));
        }
        return candles;
    }

    private void saveSticks(String symbol, CandlestickInterval interval, List<Candlestick> candles) throws Exception {
        File data = getFile(symbol, interval);
        if (!data.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            TreeMap<Long, Candlestick> candlesMap = new TreeMap<>();
            candles.forEach(candle -> candlesMap.put(candle.getOpenTime(), candle));
            mapper.writeValue(data, candlesMap);
        } else {
            //TODO load data from file to save with this combined
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<SortedMap<Long, Candlestick>> typeRef
                    = new TypeReference<SortedMap<Long, Candlestick>>() {
            };
            TreeMap<Long, Candlestick> candlesMap = (TreeMap<Long, Candlestick>) mapper.readValue(data, typeRef);
            candles.forEach(candle -> candlesMap.put(candle.getOpenTime(), candle));
            mapper.writeValue(data, candlesMap);
        }
    }

    private SortedMap<Long, Candlestick> loadSticks(String symbol, CandlestickInterval interval) throws Exception {
        File data = getFile(symbol, interval);
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<SortedMap<Long, Candlestick>> typeRef
                = new TypeReference<SortedMap<Long, Candlestick>>() {
        };
        SortedMap<Long, Candlestick> candlesMap = mapper.readValue(data, typeRef);
        return candlesMap;
    }

    private void log(String s) {
        System.out.println("CandleStickCollector: " + s);
    }


    /**
     * This function should only be used when collecting data.
     * The data is stored somewhere to prevent rate limiting from Binance.
     * This function queries multiple times if the requested data is over the maxQuery size of 1000 candles.
     *
     * @param symbol   currencycurrency
     * @param start    time in ms of start of period
     * @param end      time in ms of end of period
     * @param interval resolution of the candle sticks
     * @returns a list of the candles from this period in the [interval] resolution from Binance API
     */
    private List<Candlestick> getCandlesFromInternetAndSave(String symbol, long start, long end, CandlestickInterval interval) throws Exception {
        log("Getting sticks from internet for " + symbol + " from " + start + " to " + end);
        long intervalMs = CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(interval);
        int maxQuery = 1000;

        long time = end - start;

        List<Candlestick> candles = new ArrayList<>();

        long stickAmount = time / intervalMs;
        if (stickAmount > maxQuery) {
            long index = 0;
            while (index <= stickAmount / maxQuery) {
                long pStart = start + ((index) * intervalMs * maxQuery);
                long pEnd = start + ((index++ + 1) * intervalMs * maxQuery);
                candles.addAll(client.getBinanceClient().getCandlestickBars(symbol, interval, maxQuery,
                        pStart, Math.min(pEnd, end)));
            }
        } else {
            candles.addAll(client.getBinanceClient().getCandlestickBars(symbol, interval, maxQuery,
                    start, end));
        }
        saveSticks(symbol, interval, candles);
        return candles;
    }

}

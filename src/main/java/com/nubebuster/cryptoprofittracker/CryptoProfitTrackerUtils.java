package com.nubebuster.cryptoprofittracker;

import com.binance.api.client.domain.market.CandlestickInterval;

public class CryptoProfitTrackerUtils {

    public static long convertCandleStickIntervalToMs(CandlestickInterval interval) {
        switch (interval) {
            case ONE_MINUTE:
                return 60000;
            case FIVE_MINUTES:
                return 300000;
            case FIFTEEN_MINUTES:
                return 900000;
            case TWELVE_HOURLY:
                return 43200000;
            case HALF_HOURLY:
                return 1800000;
            case DAILY:
                return 86400000;
            case HOURLY:
                return 3600000;
            default:
                throw new IllegalArgumentException("Cant convert this interval yet. Please report this on the github page.");
        }
    }
}

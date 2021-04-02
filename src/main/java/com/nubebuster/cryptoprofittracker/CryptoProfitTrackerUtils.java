package com.nubebuster.cryptoprofittracker;

import com.binance.api.client.domain.market.CandlestickInterval;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;

public class CryptoProfitTrackerUtils {

    public static String getDocumentsPath() throws Exception {
        Process p = Runtime.getRuntime().exec("reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\" /v personal");
        p.waitFor();
        InputStream in = p.getInputStream();
        byte[] b = new byte[in.available()];
        in.read(b);
        in.close();
        return new String(b).split("\\s\\s+")[4] + File.separator + "CryptoProfitTracker";
    }

    public static long convertToTimeStamp(String lineDatum) {
        return Timestamp.valueOf(lineDatum).getTime();
    }

    public static String[] readData(File f) throws IOException {
        FileReader fr = new FileReader(f);
        char[] data = new char[(int) f.length()];
        fr.read(data);
        fr.close();
        return new String(data).split("\n");
    }


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

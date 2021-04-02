package com.nubebuster.cryptoprofittracker;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.nubebuster.cryptoprofittracker.caching.CandleStickCollector;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BinanceProfitTracker {

    public static void main(String[] args) {
        try {
            String pair = args[0];
            double bnbFee = Double.parseDouble(args[1]);
            String apiKey, apiSecret;
            File documents = new File(CryptoProfitTrackerUtils.getDocumentsPath());
            if (!documents.exists()) {
                documents.mkdir();
            }

            File configFile = new File(documents, "config.txt");
            if (configFile.exists()) {
                String[] configData = CryptoProfitTrackerUtils.readData(configFile);
                apiKey = configData[0];
                apiSecret = configData[1];
            } else {
                configFile.createNewFile();
                FileWriter fr = new FileWriter(configFile);
                fr.write("apiKey=\napiSecret=");
                fr.flush();
                fr.close();
                System.out.println("Set your API keys in " + configFile.getPath());
                System.exit(0);
                return;
            }

            File dataFile = new File(documents, "history.xlsx");
            if (!dataFile.exists()) {
                System.out.println("Put your data in " + dataFile.getPath());
                System.exit(0);
                return;
            }

            OPCPackage pkg = OPCPackage.open(dataFile);
            Workbook wb = new XSSFWorkbook(pkg);
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rows = sheet.rowIterator();

            BinanceProfitTracker binanceProfitTracker = new BinanceProfitTracker(apiKey,
                    apiSecret);
            binanceProfitTracker.printCalculations(pair, bnbFee, rows);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private BinanceApiRestClient client;
    private CandleStickCollector candleStickCollector;

    public BinanceProfitTracker(String apiKey, String apiSecret) throws Exception {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(apiKey,
                apiSecret);
        client = factory.newRestClient();
        candleStickCollector = new CandleStickCollector(this);
    }

    public BinanceApiRestClient getBinanceClient() {
        return client;
    }

    public void printCalculations(String pair, double bnbFeeValue, Iterator<Row> rows) throws Exception {

        List<TradeOrder> orders = new ArrayList<TradeOrder>();

        long oldest = Long.MAX_VALUE;
        long newest = Long.MIN_VALUE;

        while (rows.hasNext()) {
            Row row = rows.next();
            if (row.getRowNum() == 0) //headers
                continue;
            String linePair = row.getCell(1).getStringCellValue();
            if (!linePair.equals(pair))
                continue;

            long time = CryptoProfitTrackerUtils.convertToTimeStamp(row.getCell(0).getStringCellValue());
            if (time < oldest) oldest = time;
            if (time > newest) newest = time;

            boolean buy = row.getCell(2).getStringCellValue().equals("BUY");
            double price = Double.parseDouble(row.getCell(3).getStringCellValue());
            double amount = Double.parseDouble(row.getCell(4).getStringCellValue());
            double total = Double.parseDouble(row.getCell(5).getStringCellValue());
            double fee = Double.parseDouble(row.getCell(6).getStringCellValue());
            String feeCoin = row.getCell(7).getStringCellValue();

            orders.add(new TradeOrder(pair, buy, price, amount, total, fee, feeCoin, time));
        }

        //collect all candles from the transaction period to be able to convert at timestamps without querying.
        candleStickCollector.getCandlesCollectWhenMissing(pair, oldest, newest,
                CandlestickInterval.FIFTEEN_MINUTES);

        double cumProfit = 0;
        double amountInWallet = 0;
        double volume = 0;
        double accruedFees = 0;

        for (TradeOrder order : orders) {
            if (order.isBuy()) {
                cumProfit -= order.getTotal();
                amountInWallet += order.getAmount();
            } else {
                cumProfit += order.getTotal();
                amountInWallet -= order.getAmount();
            }
            volume += order.getAmount();

            if (order.getFeeCoin().equals("USDT")) {
                accruedFees += order.getFee();
            } else if (order.getFeeCoin().equals("BNB")) {
                accruedFees += order.getTotal() * bnbFeeValue;
            } else {
                amountInWallet -= order.getFee();
            }
        }

        System.out.println("Data for " + pair);
        System.out.println("#Orders (BUY+SELL): " + orders.size());
        System.out.println("Volume: " + roundedToSignificance(volume));
        System.out.println("Sub Total Profit: " + roundedToSignificance(cumProfit));

        double walletValue = getPrice(pair) * amountInWallet;
        System.out.println("Amount in wallet: " + roundedToSignificance(amountInWallet) + " (current value: " +
                Math.floor(walletValue) + ")");

        System.out.println("\nFees total: " + roundedToSignificance(accruedFees));

        System.out.println("\nTotal profit: " + Math.floor(cumProfit + walletValue - accruedFees));

    }

    private double getHistoricalPrice(String ticker, long timestamp) throws Exception {
        return Double.parseDouble(candleStickCollector.getCandlesCollectWhenMissing(ticker, timestamp, timestamp
                        + CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(CandlestickInterval.FIFTEEN_MINUTES),
                CandlestickInterval.FIFTEEN_MINUTES).get(0).getClose());
    }

    private double roundedToSignificance(double input) {
        BigDecimal bd = new BigDecimal(input);
        return bd.round(new MathContext(8)).doubleValue();
    }


    //Very slow HMMMMMM
    // maybe just go with %
    @Deprecated
    private Double getPriceAtTime(String ticker, long timestamp) {
        return Double.parseDouble(client.getCandlestickBars(ticker, CandlestickInterval.ONE_MINUTE, 1, timestamp, timestamp + 60000).get(0).getHigh());
    }

    private Double getPrice(String ticker) {
        return Double.parseDouble(client.getPrice(ticker).getPrice());
    }

}

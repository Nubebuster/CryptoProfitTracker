package com.nubebuster.cryptoprofittracker;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.nubebuster.cryptoprofittracker.caching.CandleStickCollector;
import com.nubebuster.cryptoprofittracker.ui.ProfitTrackerUI;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.rmi.UnexpectedException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BinanceProfitTracker {

    private static final CandlestickInterval CACHE_PRECISION = CandlestickInterval.FIVE_MINUTES;

    public static void main(String[] args) {

        JFrame frame = new JFrame();
        frame.setTitle("Binance profit tracker");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        ProfitTrackerUI ui = new ProfitTrackerUI();
        frame.add(ui.panel1);
        frame.pack();
        frame.setBounds(0, 0, 512, 256);
        frame.setVisible(true);


        try {
//            double bnbFee = Double.parseDouble(args[0]);//TODO add this to UI settings
            double bnbFee = 0.00075;
            File documents = new File(CryptoProfitTrackerUtils.getDocumentsPath());
            if (!documents.exists()) {
                documents.mkdir();
            }

            File configFile = new File(documents, "config.txt");
            if (configFile.exists()) {
                String[] configData = CryptoProfitTrackerUtils.readData(configFile);
                ui.apiKeyTextField.setText(configData[0].replace("apiKey=", ""));
                ui.secretKeyTextField.setText(configData[1].replace("apiSecret=", ""));
                ui.dataTextField.setText(configData[2].replace("dataFile=", ""));
            }

            if (ui.dataTextField.getText() == null || ui.dataTextField.getText().isEmpty()) {
                ui.dataTextField.setText(CryptoProfitTrackerUtils.getDocumentsPath() + File.separator + "data.xlsx");
            }


            ui.printCalculationsButton.addActionListener(e -> {
                ui.printCalculationsButton.setEnabled(false);
                ui.output.setText("");
                try {
                    File dataFile = new File(ui.dataTextField.getText());
                    if (!dataFile.exists()) {
                        System.err.println("Data file not found: " + dataFile.getPath());
                        ui.printCalculationsButton.setEnabled(true);
                        return;
                    }
                    OPCPackage pkg = OPCPackage.open(dataFile);
                    Workbook wb = new XSSFWorkbook(pkg);
                    Sheet sheet = wb.getSheetAt(0);
                    Iterator<Row> rows = sheet.rowIterator();
                    BinanceProfitTracker binanceProfitTracker = new BinanceProfitTracker(ui, ui.apiKeyTextField.getText(),
                            ui.secretKeyTextField.getText());
                    binanceProfitTracker.printCalculations(ui.ticker.getText(), bnbFee, rows);
                    pkg.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                ui.printCalculationsButton.setEnabled(true);
            });
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                File configFile = new File(CryptoProfitTrackerUtils.getDocumentsPath(), "config.txt");
                configFile.createNewFile();
                FileWriter fr = new FileWriter(configFile);
                fr.write("apiKey=" + ui.apiKeyTextField.getText() + "\napiSecret=" +
                        ui.secretKeyTextField.getText() + "\ndataFile=" + ui.dataTextField.getText() + "\nNote: this file should not be edited manually.");
                fr.flush();
                fr.close();
                System.out.println("Saved data to " + configFile.getPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    public void log(String s) {
        System.out.println(s);
        ui.output.append("\n" + s);
    }

    private BinanceApiRestClient client;
    private CandleStickCollector candleStickCollector;
    private ProfitTrackerUI ui;

    public BinanceProfitTracker(ProfitTrackerUI ui, String apiKey, String apiSecret) throws Exception {
        this.ui = ui;
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

//        //collect all candles from the first to last transaction to be able to convert at timestamps without querying.
//        List<Candlestick> candles = candleStickCollector.getCandlesCollectWhenMissing(pair, oldest - CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(CACHE_PRECISION), newest + CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(CACHE_PRECISION),
//                CACHE_PRECISION);

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
                if (!pair.toLowerCase().contains(order.getFeeCoin().toLowerCase()))
                    throw new UnexpectedException("Fee coin for " + pair + " was in " + order.getFeeCoin()
                            + ". This is not supported yet. Report this to the github repository.");
                amountInWallet -= order.getFee();
            }
        }

        log("Data for " + pair);
        log("#Orders (BUY+SELL): " + orders.size());
        log("Volume: " + roundedToSignificance(volume));
        log("Sub Total Profit: " + roundedToSignificance(cumProfit));

        double walletValue = getPrice(pair) * amountInWallet;
        log("Amount in wallet: " + roundedToSignificance(amountInWallet) + " (current value: " +
                Math.floor(walletValue) + ")");

        log("Fees total: " + roundedToSignificance(accruedFees));

        log("\nTotal profit: " + Math.floor(cumProfit + walletValue - accruedFees) + "\n-----------------------");
    }

    /**
     * @param candles to get the price from
     * @param timestamp of the price you want to know
     * @returns the closing price of the candle. -1 if no candle was found.
     * You can use {@link #loadHistoricalPrice(String, long)} if this function returns -1.
     */
    private double getHistoricalPrice(List<Candlestick> candles, long timestamp) {
        long startRounded = timestamp - (timestamp % CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(CACHE_PRECISION));
        for (Candlestick candle : candles) {
            if (candle.getOpenTime() == startRounded) return Double.parseDouble(candle.getClose());
        }
        System.err.println("Is there a candle missing in the candle stick data?");
        return -1;
    }

    /**
     * Use this function if {@link #getHistoricalPrice(List, long)} returns -1.
     *
     * @param ticker
     * @param timestamp
     * @return
     * @throws Exception
     */
    @Deprecated
    private double loadHistoricalPrice(String ticker, long timestamp) throws Exception {
        return Double.parseDouble(candleStickCollector.getCandlesCollectWhenMissing(ticker,
                timestamp - CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(CACHE_PRECISION),
                timestamp,
                CACHE_PRECISION).get(0).getClose());
    }

    private double roundedToSignificance(double input) {
        BigDecimal bd = new BigDecimal(input);
        return bd.round(new MathContext(8)).doubleValue();
    }

    /**
     * @deprecated this is slow and {@link #getHistoricalPrice(List, long)} should be used for a less precise, cached price.
     * Alternatively, you can use {@link #loadHistoricalPrice(String, long)} to load the price from the cache.
     */
    @Deprecated
    private Double queryPriceAtTime(String ticker, long timestamp) {
        return Double.parseDouble(client.getCandlestickBars(ticker, CandlestickInterval.ONE_MINUTE, 1, timestamp, timestamp + 60000).get(0).getHigh());
    }

    private Double getPrice(String ticker) {
        return Double.parseDouble(client.getPrice(ticker).getPrice());
    }
}

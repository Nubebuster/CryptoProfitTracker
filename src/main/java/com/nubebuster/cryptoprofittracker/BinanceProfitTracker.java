package com.nubebuster.cryptoprofittracker;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.nubebuster.cryptoprofittracker.data.FileHandler;
import com.nubebuster.cryptoprofittracker.data.SettingsHandler;
import com.nubebuster.cryptoprofittracker.data.TradeOrder;
import com.nubebuster.cryptoprofittracker.data.UnsupportedFileFormatException;
import com.nubebuster.cryptoprofittracker.data.caching.CandleStickCollector;
import com.nubebuster.cryptoprofittracker.ui.ProfitTrackerUI;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.rmi.UnexpectedException;
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

            SettingsHandler settings = SettingsHandler.getInstance();
            settings.loadData();
            ui.apiKeyTextField.setText(settings.getApiKey());
            ui.secretKeyTextField.setText(settings.getApiSecret().replace("apiSecret=", ""));
            ui.dataTextField.setText(settings.getDataFile().replace("dataFile=", ""));


            ui.printCalculationsButton.addActionListener(e -> {
                ui.printCalculationsButton.setEnabled(false);
                ui.output.setText("");
                List<TradeOrder> orders = null;
                try {
                    File dataFile = new File(ui.dataTextField.getText());
                    orders = FileHandler.loadOrders(dataFile, ui.ticker.getText().toUpperCase());
                } catch (InvalidFormatException | UnsupportedFileFormatException ex) {
                    ex.printStackTrace();
                    ui.output.setText("Unsupported data format. Current support: " + FileHandler.SUPPORTED_FORMATS);
                    return;
                } catch (IOException ex) {
                    ui.output.setText(ex.getMessage());
                    ex.printStackTrace();
                    return;
                }

                try {
                    BinanceProfitTracker binanceProfitTracker = new BinanceProfitTracker(ui, ui.apiKeyTextField.getText(),
                            ui.secretKeyTextField.getText());
                    binanceProfitTracker.printCalculations(ui.ticker.getText().toUpperCase(), bnbFee, orders);
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
                SettingsHandler.getInstance().setApiKey(ui.apiKeyTextField.getText());
                SettingsHandler.getInstance().setApiSecret(ui.secretKeyTextField.getText());
                SettingsHandler.getInstance().setDataFile(ui.dataTextField.getText());
                SettingsHandler.getInstance().saveSettings();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
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

    /**
     * @param pair        needs to be in upper case. Example: ADAUSDT
     * @param bnbFeeValue percentage formatted to double
     * @param orders
     * @throws Exception
     */
    public void printCalculations(String pair, double bnbFeeValue, List<TradeOrder> orders) throws Exception {

//         //collect all candles from the first to last transaction to be able to convert at timestamps without querying.
//        long oldest = Long.MAX_VALUE;
//        long newest = Long.MIN_VALUE;
//        for(TradeOrder order : getOrders(pair, rows)) {
//            long time = order.getTime();
//            if (time < oldest) oldest = time;
//            if (time > newest) newest = time;
//        }
//
//        List<Candlestick> candles = candleStickCollector.getCandlesCollectWhenMissing(pair, oldest - CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(CACHE_PRECISION), newest + CryptoProfitTrackerUtils.convertCandleStickIntervalToMs(CACHE_PRECISION),
//                CACHE_PRECISION);

        double cumProfit = 0;
        double amountInWallet = 0;
        double volume = 0;
        double accruedFees = 0;
        double addDisplayFees = 0;

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
                //fees are deducted from total profit. This is in order to not calculate native token fees twice
                addDisplayFees += order.getFee() * order.getPrice();
            }
        }

        log("Data for " + pair);
        log("#Trades (BUY+SELL): " + orders.size());
        log("Volume: " + roundedToSignificance(volume));
        log("Sub Total Profit: " + roundedToSignificance(cumProfit));

        double walletValue = getPrice(pair) * amountInWallet;
        log("Amount in wallet: " + roundedToSignificance(amountInWallet) + " (current value: " +
                Math.floor(walletValue) + ")");

        log("Fees total: " + roundedToSignificance(accruedFees + addDisplayFees));

        log("\nTotal profit: " + Math.floor(cumProfit + walletValue - accruedFees) + "\n-----------------------");
    }

    /**
     * @param candles   to get the price from
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

    public void log(String s) {
        System.out.println(s);
        ui.output.append("\n" + s);
    }
}

package com.nubebuster.cryptoprofittracker.data;

public class TradeOrder {

    private String pair;
    private boolean buy;
    private double price, amount, total, fee;
    private String feeCoin;
    private long time;

    public TradeOrder(String pair, boolean buy, double price, double amount, double total, double fee, String feeCoin, long time) {
        this.pair = pair;
        this.buy = buy;
        this.price = price;
        this.amount = amount;
        this.total = total;
        this.fee = fee;
        this.feeCoin = feeCoin;
        this.time = time;
    }

    public String getPair() {
        return pair;
    }

    public boolean isBuy() {
        return buy;
    }

    public double getPrice() {
        return price;
    }

    public double getAmount() {
        return amount;
    }

    public double getTotal() {
        return total;
    }

    public double getFee() {
        return fee;
    }

    public String getFeeCoin() {
        return feeCoin;
    }

    public long getTime() {
        return time;
    }

}

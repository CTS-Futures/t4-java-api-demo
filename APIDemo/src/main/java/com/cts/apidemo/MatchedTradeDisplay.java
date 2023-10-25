package com.cts.apidemo;

import com.t4login.api.Market;
import com.t4login.api.PriceFormatter;
import com.t4login.api.accounts.Account;
import com.t4login.api.accounts.MatchedTrade;
import com.t4login.datetime.NDateTime;
import com.t4login.definitions.priceconversion.Price;
import com.t4login.definitions.priceconversion.PriceFormat;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

public class MatchedTradeDisplay {

    private final String accountName;
    private final Market market;
    private final MatchedTrade matchedTrade;
    private final DecimalFormat cashFormatter = new DecimalFormat("#,##0");
    public static final SimpleDateFormat timeFormatter = NDateTime.createDateFormat("h:mm:ss.SSS a");

    PriceFormatter priceFormatter = new PriceFormatter() {
        @Override
        public String formatPrice(Market market, Price price) {
            return PriceFormat.convertPriceToDisplayFormat(price, market);
        }
    };

    public MatchedTradeDisplay(Market market, Account account, MatchedTrade matchedTrade) {
        this.accountName = account.getAccountNumber();
        this.market = market;
        this.matchedTrade = matchedTrade;
    }

    public NDateTime getCloseTime() {
        return matchedTrade.CloseTime;
    }

    public NDateTime getEntryTime() {
        return matchedTrade.CloseTime;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getMarketDescription() {
        return market.getDescription();
    }

    public String getRPLDisplay() {
        return cashFormatter.format(matchedTrade.RPL);
    }

    public String getVolumeDisplay() {
        return String.format("%d", matchedTrade.EntryQuantity);
    }

    public String getEntryPriceDisplay() {
        return priceFormatter.formatPrice(market, matchedTrade.AvgEntryPrice);
    }

    public String getClosePriceDisplay() {
        return priceFormatter.formatPrice(market, matchedTrade.AvgClosePrice);
    }
}

package com.cts.apidemo;

import com.t4login.api.Market;
import com.t4login.api.accounts.PositionProfit;

import java.text.DecimalFormat;

/**
 * Helper class to simplify populating the positions table.
 */
public class PositionDisplay {

    private final Market market;
    private final PositionProfit positionProfit;
    private final DecimalFormat cashFormatter = new DecimalFormat("#,##0");

    public PositionDisplay(Market market, PositionProfit pp) {
        this.market = market;
        this.positionProfit = pp;
    }

    public String getMarketDescription() {
        return market.getDescription();
    }

    public String getNetDisplay() {
        return positionProfit.getNetDisplay();
    }

    public String getPLDisplay() {
        return cashFormatter.format(positionProfit.getPL());
    }

    public String getPositionDisplay() {
        return String.format("%d-%d", positionProfit.getBuys(), positionProfit.getSells());
    }

    public String getWorkingDisplay() {
        return String.format("%d-%d", positionProfit.getWorkingBuys(), positionProfit.getWorkingSells());
    }
}

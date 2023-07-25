package com.cts.apidemo;

import com.t4login.api.Market;
import com.t4login.api.MarketPriceFormatter;
import com.t4login.api.PriceFormatter;
import com.t4login.api.accounts.Order;
import com.t4login.datetime.NDateTime;
import com.t4login.definitions.priceconversion.Price;
import com.t4login.definitions.priceconversion.PriceFormat;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

/**
 * Helper class to simplify populating the order book table.
 */
public class OrderDisplay {

    private final Market market;
    private final Order order;
    private final DecimalFormat cashFormatter = new DecimalFormat("#,##0");
    public static final SimpleDateFormat timeFormatter = NDateTime.createDateFormat("h:mm:ss.SSS a");

    PriceFormatter priceFormatter = new PriceFormatter() {
        @Override
        public String formatPrice(Market market, Price price) {
            return PriceFormat.convertPriceToDisplayFormat(price, market);
        }
    };

    public OrderDisplay(Market market, Order order) {
        this.market = market;
        this.order = order;
    }

    public NDateTime getSubmitTime() {
        return order.getLastUpdate().SubmitTime;
    }

    public String getMarketDescription() {
        return market.getDescription();
    }

    public String getSideDisplay() {
        return order.getSide().toString();
    }

    public String getVolumeDisplay() {
        return String.format("%d/%d", order.getTotalFillVolume(), order.getCurrentVolume());
    }

    public String getOrderTypeDisplay() {
        return Order.getOrderTypeDisplay(order.getLastUpdate(), new MarketPriceFormatter() {

            @Override
            public String formatPrice(Price price) {
                return priceFormatter.formatPrice(market, price);
            }

            @Override
            public Market getMarket() {
                return market;
            }
        });
    }

    public String getStatusDisplay() {
        return Order.getOrderStatusSummary(order);
    }

}

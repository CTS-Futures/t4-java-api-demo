package com.cts.apidemo;

import com.cts.apidemo.util.LogUtil;
import com.t4login.JavaHost;
import com.t4login.Log;
import com.t4login.api.*;
import com.t4login.api.accounts.*;
import com.t4login.api.chartdata.ChartData;
import com.t4login.api.chartdata.ChartDataSubscriptionHandler;
import com.t4login.api.chartdata.DataSeriesSubscription;
import com.t4login.application.chart.BarInterval;
import com.t4login.application.chart.ChartInterval;
import com.t4login.application.chart.SessionTimeRange;
import com.t4login.application.chart.chartdata.BarDataPoint;
import com.t4login.application.chart.chartdata.BarDataSeries;
import com.t4login.application.chart.chartdata.DataLoadArgs;
import com.t4login.application.chart.chartdata.IBarDataPoint;
import com.t4login.application.chart.markets.ExpiryMarket;
import com.t4login.connection.ServerType;
import com.t4login.datetime.NDateTime;
import com.t4login.definitions.*;
import com.t4login.definitions.priceconversion.Price;
import com.t4login.definitions.priceconversion.PriceFormat;
import com.t4login.util.ReturnFlag;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

public class MainController {

    // region FXML controls

    public Button logoutButton;
    public Button loginButton;
    public TextField firmInput;
    public TextField usernameInput;
    public TextField passwordInput;
    public Label loggedInIndicator;
    public Label loggedInValue;

    public ComboBox<Account> accountPickerComboBox;
    public Label accountsLoadingLabel;
    public Label profitLossLabel;
    public Label cashLabel;

    public Button loadExchangeContractButton;
    public Button subscribeMarketDataButton;
    public Button unsubscribeMarketDataButton;

    public Spinner<Integer> orderVolumeSpinner;
    public RadioButton orderBidRadioButton;
    public RadioButton orderOfferRadioButton;
    public TextField orderPriceTextField;
    public Button orderSubmitButton;

    public ToggleButton liveToggleButton;
    public TreeView<ContractPickerNode> contractPickerTreeView;
    public TextField contractSearchTextField;
    public ComboBox<MarketPickerMarket> expiryPickerComboBox;

    public Label subscribedMarketDecsriptionLabel;
    public Label highPriceLabel;
    public Label lowPriceLabel;
    public Label marketModeLabel;
    public Label totalTradedVolumeLabel;
    public Label lastTradeLabel;
    public TableView<MarketDataSnapshot.DepthItem> marketDepthBidsTableView;
    public TableView<MarketDataSnapshot.DepthItem> marketDepthOffersTableView;

    public Label currentPositionLabel;
    public Label currentWorkingLabel;
    public Label currentPLLabel;

    public TableColumn<MarketDataSnapshot.DepthItem, Price> bidPriceTableColumn;
    public TableColumn<MarketDataSnapshot.DepthItem, Integer> bidVolumeTableColumn;
    public TableColumn<MarketDataSnapshot.DepthItem, Price> offerPriceTableColumn;
    public TableColumn<MarketDataSnapshot.DepthItem, Integer> offerVolumeTableColumn;

    public ComboBox<BarInterval> barIntervalComboBox;
    public TableView<BarDataPoint> chartDataTableView;
    public Button subscribeChartDataButton;
    public Button unsubscribeChartDataButton;

    public TableColumn<BarDataPoint, NDateTime> chartDataDateTableColumn;
    public TableColumn<BarDataPoint, NDateTime> chartDataTimeTableColumn;
    public TableColumn<BarDataPoint, Integer> chartDataOpenTicksTableColumn;
    public TableColumn<BarDataPoint, Integer> chartDataHighTicksTableColumn;
    public TableColumn<BarDataPoint, Integer> chartDataLowTicksTableColumn;
    public TableColumn<BarDataPoint, Integer> chartDataCloseTicksTableColumn;
    public TableColumn<BarDataPoint, Integer> chartDataVolumeTableColumn;

    public TableView<PositionDisplay> positionsTableView;
    public TableColumn<PositionDisplay, String> positionMarketTableColumn;
    public TableColumn<PositionDisplay, String> positionNetTableColumn;
    public TableColumn<PositionDisplay, String> positionPLTableColumn;
    public TableColumn<PositionDisplay, String> positionSideTableColumn;
    public TableColumn<PositionDisplay, String> postionWorkingTableColumn;

    public TableView<OrderDisplay> orderbookTableView;
    public TableColumn<OrderDisplay, NDateTime> orderbookTimeTableColumn;
    public TableColumn<OrderDisplay, String> orderbookMarketTableColumn;
    public TableColumn<OrderDisplay, String> orderbookSideTableColumn;
    public TableColumn<OrderDisplay, String> orderbookVolumeTableColumn;
    public TableColumn<OrderDisplay, String> orderbookOrderTypeTableColumn;
    public TableColumn<OrderDisplay, String> orderbookOrderStatusTableColumn;

    // endregion

    private static final String TAG = "MainController";
    private static final LogUtil logger = new LogUtil(TAG, true);

    private static T4HostService t4HostService;

    private ServerType overrideServerType = ServerType.Unknown;

    //region Service Handlers

    /**
     * Handles host service events. Always assume all callbacks will occur on an API thread
     * and not the main/UI thread.
     */
    private final T4HostServiceHandler hostServiceHandler = new T4HostServiceHandler() {
        @Override
        public boolean isActiveUI() {
            return false;
        }

        @Override
        public void onLoginResponse(LoginResponse loginResponse) {
            Platform.runLater(() -> processLoginResponse(loginResponse));
        }

        @Override
        public void on2FATokenRequest() {
            Platform.runLater(MainController.this::on2FATokenRequest);
        }

        @Override
        public void onServiceStateChanged(T4HostService.ServiceState newState) {

            if (newState.equals(T4HostService.ServiceState.Started)) {
                t4HostService.beginConnect();
            }

            Platform.runLater(() -> MainController.this.updateServiceState());
        }
    };

    /**
     * Handles incoming market data events. Always assume all callbacks will occur on an API thread
     * and not the main/UI thread.
     */
    MarketDataHandler marketDataHandler = new MarketDataHandler() {
        /**
         * Where, what, why, for this market data handler. Used in logging to give clues about which MD handler is holding
         * a subscription ro causing issues.
         */
        @Override
        public String getDescription() {
            return TAG;
        }

        /**
         * This is a mechanism to cull unused subscriptions and save bandwidth and resources. If a market isn't
         * updated here, the market will get unsubscribed or subscribed at the lowest rate any other MarketDataHandler
         * would need.
         * @param subscriptions The active subscriptions.
         */
        @Override
        public void onCheckSubscriptions(Map<String, SubscriptionLevel> subscriptions) {

            Market market = mSubscribedMarket;

            if (market != null) {
                SubscriptionLevel subscrLevel = subscriptions.get(market.getMarketID());
                if (subscrLevel != null) {
                    subscrLevel.update(DepthBuffer.Smart, DepthLevels.Normal);
                }
            }
        }

        /**
         * Called when the API receives a real-time market depth update. This will be called for every subscribed market,
         * so always check that the update is for the market you are interested in for this market data handler.
         *
         * @param snapshot The updated snapshot.
         */
        @Override
        public void onMarketUpdate(MarketDataSnapshot snapshot) {
            Market market = mSubscribedMarket;

            if (market != null && snapshot.Market.getMarketID().equals(market.getMarketID())) {
                Platform.runLater(() -> MainController.this.onMarketUpdate(snapshot));
            }
        }

        @Override
        public void onContractsLoaded(Collection<String> exchangeids) {
            Platform.runLater(MainController.this::initializeContractPickerTreeView);
        }

        /**
         * Called when the markets are loaded from the server.
         * @param exchangeID The exchange id of the markets.
         * @param contractID The contract id of the markets.
         * @param markets    The markets.
         */
        @Override
        public void onMarketListComplete(String exchangeID, String contractID, List<Market> markets) {

            if (mSelectedContract != null && mSelectedContract.getExchangeID().equals(exchangeID) && mSelectedContract.getContractID().equals(contractID)) {
                // Subscribe the market we have been waiting for.
                subscribeMarket();
            }
        }
    };

    IAccountDataHandler accountDataHandler = new AccountDataHandler() {
        /**
         * Who, what, where is handling this call back. Used for logging and troubleshooting.
         */
        @Override
        public String getDescription() {
            return TAG + "(Account Picker)";
        }

        @Override
        public void onActiveAccountChanged(Account acct) {
            // If your application has multiple windows, this could be used as a signal as to the actively selected
            // account has changed.
            Platform.runLater(MainController.this::onActiveAccountChanged);
            Platform.runLater(MainController.this::displayOrders);
        }

        @Override
        public void onAccountComplete(Account acct) {
            // Accounts are loaded dynamically on the server and can take some time.
            Platform.runLater(MainController.this::onAccountComplete);
            Platform.runLater(MainController.this::displayOrders);
        }

        @Override
        public void onOrderAdded(Account acct, Position position, List<Order> updates) {
            Platform.runLater(MainController.this::displayOrders);
        }

        @Override
        public void onOrderUpdate(Account acct, Position position, List<Order> updates) {
            Platform.runLater(MainController.this::displayOrders);
        }

        @Override
        public void onOrderRemoved(Account acct, Position position, List<Order> updates) {
            Platform.runLater(MainController.this::displayOrders);
        }
    };

    private final ChartDataSubscriptionHandler chartDataSubscriptionHandler = new ChartDataSubscriptionHandler() {

        /**
         * Called usually when historical data is received from the server. Update the chart data/display.
         *
         * @param updatedArgs The chart data that was updated.
         */
        @Override
        public void onDataSeriesReset(List<DataLoadArgs> updatedArgs) {
            for (DataLoadArgs updatedArg : updatedArgs) {
                if (updatedArg.equals(mSubscribedChartDataArgs)) {
                    updateChartDataDisplay();
                }
            }
        }

        /**
         * Called usually when historical data is received from the server. Update the chart data/display.
         *
         * @param updatedArgs The chart data that was updated.
         */
        @Override
        public void onDataSeriesUpdated(List<DataLoadArgs> updatedArgs) {
            for (DataLoadArgs updatedArg : updatedArgs) {
                if (updatedArg.equals(mSubscribedChartDataArgs)) {
                    updateChartDataDisplay();
                }
            }
        }

        /**
         * This method gets called when it is time to flush live data to the chart collections.         *
         * NOTE: Just copy the implementation. It should probably get simplified for API use.
         *
         * @param handled
         */
        @Override
        public void onDataSeriesFlushTimer(ReturnFlag handled) {
            if (!handled.isSet()) {
                handled.set();
                // Call flushLiveTrades() on the UI thread.
                Platform.runLater(() -> t4HostService.getChartData().flushLiveTrades());
            }
        }

        /**
         * This method gets called when the data series has been updated. Update the chart data/display.
         *
         * @param updatedArgs The chart data that was updated.
         */
        @Override
        public void onDataSeriesFlushed(List<DataLoadArgs> updatedArgs) {
            for (DataLoadArgs args : updatedArgs) {
                if (args.equals(mSubscribedChartDataArgs)) {
                    updateChartDataDisplay();
                }
            }
        }

        /**
         * This method gets called periodically to make sure the app is still interested in the chart data subscription.
         *
         * @param subscriptions The subscriptions to check.
         */
        @Override
        public void onCheckSubscriptions(List<DataSeriesSubscription> subscriptions) {
            for (DataSeriesSubscription subscription : subscriptions) {
                if (subscription.Args.equals(mSubscribedChartDataArgs)) {
                    // Mark the subscription to keep it going.
                    subscription.mark();
                }
            }
        }
    };

    //endregion

    //region Startup and Shutdown

    @FXML
    public void initialize() {

        // Initialize the display.
        clearMarketData();
        clearContractPicker();
        clearAccountStatus();
        clearChartData();

        accountPickerComboBox.setVisible(false);
        accountPickerComboBox.setManaged(false);
        accountsLoadingLabel.setText("-");
        accountsLoadingLabel.setVisible(true);
        accountsLoadingLabel.setManaged(true);

        // Set up the bid and offer market depth tables.
        bidPriceTableColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        bidVolumeTableColumn.setCellValueFactory(new PropertyValueFactory<>("volume"));
        offerPriceTableColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        offerVolumeTableColumn.setCellValueFactory(new PropertyValueFactory<>("volume"));

        // Set up the bar interval combo box.
        barIntervalComboBox.getItems().add(BarInterval.Bar30Second);
        barIntervalComboBox.getItems().add(BarInterval.Bar1Minute);
        barIntervalComboBox.getItems().add(BarInterval.Bar5Minute);
        barIntervalComboBox.getItems().add(BarInterval.Bar15Minute);
        // NOTE: Use the constructor to create any desired bar size.
        barIntervalComboBox.getItems().add(new BarInterval(45, ChartInterval.Minute));
        barIntervalComboBox.getItems().add(BarInterval.Bar1Hour);
        barIntervalComboBox.getItems().add(BarInterval.Bar1Day);
        barIntervalComboBox.getSelectionModel().select(2);

        // Set up the chart data table.
        chartDataDateTableColumn.setCellValueFactory(new PropertyValueFactory<>("tradeDate"));
        chartDataDateTableColumn.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BarDataPoint, NDateTime> call(TableColumn<BarDataPoint, NDateTime> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(NDateTime item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item == null || empty) {
                            setText(null);
                        } else {
                            setText(item.toDateString());
                        }
                    }
                };
            }
        });
        chartDataTimeTableColumn.setCellValueFactory(new PropertyValueFactory<>("time"));
        chartDataTimeTableColumn.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BarDataPoint, NDateTime> call(TableColumn<BarDataPoint, NDateTime> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(NDateTime item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item == null || empty) {
                            setText(null);
                        } else {
                            setText(item.toShortTimeString());
                        }
                    }
                };
            }
        });
        chartDataOpenTicksTableColumn.setCellValueFactory(new PropertyValueFactory<>("open"));
        chartDataHighTicksTableColumn.setCellValueFactory(new PropertyValueFactory<>("high"));
        chartDataLowTicksTableColumn.setCellValueFactory(new PropertyValueFactory<>("low"));
        chartDataCloseTicksTableColumn.setCellValueFactory(new PropertyValueFactory<>("close"));
        chartDataVolumeTableColumn.setCellValueFactory(new PropertyValueFactory<>("volume"));

        // Set up the position table.
        positionMarketTableColumn.setCellValueFactory(new PropertyValueFactory<>("marketDescription"));
        positionNetTableColumn.setCellValueFactory(new PropertyValueFactory<>("netDisplay"));
        positionPLTableColumn.setCellValueFactory(new PropertyValueFactory<>("pLDisplay"));
        positionSideTableColumn.setCellValueFactory(new PropertyValueFactory<>("positionDisplay"));
        postionWorkingTableColumn.setCellValueFactory(new PropertyValueFactory<>("workingDisplay"));

        // Set up the orderbook table.
        orderbookTimeTableColumn.setCellValueFactory(new PropertyValueFactory<>("submitTime"));
        orderbookMarketTableColumn.setCellValueFactory(new PropertyValueFactory<>("marketDescription"));
        orderbookSideTableColumn.setCellValueFactory(new PropertyValueFactory<>("sideDisplay"));
        orderbookVolumeTableColumn.setCellValueFactory(new PropertyValueFactory<>("volumeDisplay"));
        orderbookOrderTypeTableColumn.setCellValueFactory(new PropertyValueFactory<>("orderTypeDisplay"));
        orderbookOrderStatusTableColumn.setCellValueFactory(new PropertyValueFactory<>("statusDisplay"));

        // Debugging help.
        String serverName = System.getenv("T4DEMO_SERVER");
        overrideServerType = ServerType.fromName(serverName) != null ? ServerType.fromName(serverName) : ServerType.Unknown;
        String ff = System.getenv("T4DEMO_FIRM");
        firmInput.setText(System.getenv("T4DEMO_FIRM"));
        usernameInput.setText(System.getenv("T4DEMO_USERNAME"));
        passwordInput.setText(System.getenv("T4DEMO_PASSWORD"));
        contractSearchTextField.setText(System.getenv("T4DEMO_CONTRACT_SEARCH") != null ? System.getenv("T4DEMO_CONTRACT_SEARCH") : "");
    }

    @FXML
    public void shutdown() {
        // Cleanup account picker.
        if (t4HostService != null) {
            t4HostService.getAccountData().unregisterForAccountData(accountDataHandler);
        }

        accountPickerComboBox.setVisible(false);
        accountPickerComboBox.setManaged(false);
        accountsLoadingLabel.setText("-");
        accountsLoadingLabel.setVisible(true);
        accountsLoadingLabel.setManaged(true);

        // Unregister account status.
        if (accountProfitUpdater != null) {
            accountProfitUpdater.unregisterForUpdates(accountProfitUpdateHandler);
            accountProfitUpdater.disconnect();
            accountProfitUpdater = null;
        }

        // Unregister market data, etc.
        if (t4HostService != null) {
            // Unregister market data.
            t4HostService.getMarketData().unregisterForMarketData(marketDataHandler);

            // Unregister chart data.
            t4HostService.getChartData().unregisterForSubscriptionUpdates(chartDataSubscriptionHandler);

            // Unregister host service.
            t4HostService.unregisterHostServiceHandler(hostServiceHandler);

            // Disconnect.
            t4HostService.destroy();
        }
    }

    //endregion

    //region Login

    public void onLoginButtonClicked() {
        ServerType serverType = liveToggleButton.isSelected() ? ServerType.Live : ServerType.Simulator;
        login(serverType, firmInput.getText(), usernameInput.getText(), passwordInput.getText(), "");
    }

    public void onLogoutButtonClicked() {
        if (t4HostService != null) {
            t4HostService.destroy();
        }

        // Clear all displays.
        shutdown();

        clearMarketData();
        clearContractPicker();
        clearAccountStatus();
        clearPositionDisplay();
        clearOrderBook();
        clearChartData();
    }

    private void login(ServerType serverType, String firm, String username, String password, String newPassword) {

        resetT4Service();

        // Initialize the T4 host service.
        t4HostService = new T4HostService();
        t4HostService.initialize(new JavaHost(
                "T4Example",
                "112A04B0-5AAF-42F4-994E-FA7CB959C60B",
                "com.cts.apidemo",
                "1.0",
                // TODO: Read from the current OS.
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "",
                "",
                Paths.get(System.getProperty("user.home"), ".t4").toString(),
                true
        ));

        // Register host service handler.
        t4HostService.registerHostServiceHandler(hostServiceHandler);
        t4HostService.getMarketData().registerForMarketData(marketDataHandler);

        // Register chart data handler.
        t4HostService.getChartData().registerForSubscriptionUpdates(chartDataSubscriptionHandler);

        // Login.
        try {
            t4HostService.start(new UserLogin(
                    overrideServerType.equals(ServerType.Unknown) ? serverType : overrideServerType,
                    firm,
                    username,
                    password));
        } catch (Exception e) {
            logger.log("Error starting T4HostService: " + e.getMessage());
            resetT4Service();
        }
    }

    private void processLoginResponse(LoginResponse loginResponse) {

        updateServiceState();

        if (loginResponse.getLoginResult().equals(LoginResult.Success)) {
            onLoggedIn();
        } else if (loginResponse.getLoginResult().equals(LoginResult.PasswordExpired)) {
            // Prompt the user for a new password and send it.
            promptForNewPassword();
        } else {
            // Prompt the user that the login failed.
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Login Failed");
            alert.setHeaderText(null);
            alert.setContentText("Login failed: " + loginResponse.getLoginResult());
            alert.showAndWait();
        }
    }

    private void on2FATokenRequest() {
        // The server requested a 2FA token. Prompt the user for it.
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Two-Factor Authentication Requested");
        dialog.setHeaderText("Enter the 2FA Token we sent:");
        dialog.setContentText("2FA Token:");

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            String token = result.get();
            t4HostService.submit2FAToken(token);
        } else {
            onLogoutButtonClicked();
        }
    }

    private void promptForNewPassword() {
        // The server requested a 2FA token. Prompt the user for it.
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Password Expired");
        dialog.setHeaderText("Your password has expired:");
        dialog.setContentText("New Password:");

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            String newPassword = result.get();
            ServerType serverType = liveToggleButton.isSelected() ? ServerType.Live : ServerType.Simulator;
            login(serverType, firmInput.getText(), usernameInput.getText(), passwordInput.getText(), newPassword);
        } else {
            onLogoutButtonClicked();
        }
    }

    private void onLoggedIn() {
        // Initialize the account picker.
        initializeAccountPicker();

        // Initialize the account status.
        initializeAccountStatus();

        // Initialize the contract picker.
        initializeContractPickerTreeView();
    }

    private void updateServiceState() {

        // Updates service state display.
        T4HostService.ServiceState serviceState = t4HostService.getServiceState();
        LoginResponse loginResponse = t4HostService.getLastLoginResponse();

        String lastLoginResult = "-";
        if (loginResponse != null) {
            lastLoginResult = loginResponse.getLoginResult().toString();
        }

        loggedInValue.setText(String.format("%s - %s", serviceState, lastLoginResult));
    }

    //endregion

    //region Account Picker

    private void initializeAccountPicker() {

        // Clean up previous handler. (Could happen on a window close.)
        t4HostService.getAccountData().unregisterForAccountData(accountDataHandler);

        // Register for account updates.
        t4HostService.getAccountData().registerForAccountData(accountDataHandler);

        accountsLoadingLabel.setText("-");
    }

    private void onAccountComplete() {

        accountsLoadingLabel.setVisible(false);
        accountsLoadingLabel.setManaged(false);
        accountPickerComboBox.setVisible(true);
        accountPickerComboBox.setManaged(true);

        // Get a handle to the AccountData class that we will use throughout the method.
        AccountData accountData = t4HostService.getAccountData();

        // All the accounts are loaded.
        List<Account> accounts = accountData.getAccounts();
        accountPickerComboBox.getItems().addAll(accounts);

        // Select the active account.
        int activeAccountIndex = accounts.indexOf(accountData.getActiveAccount());
        if (activeAccountIndex >= 0) {
            accountPickerComboBox.getSelectionModel().select(activeAccountIndex);
        }

        initializeAccountStatus();
    }

    private void onActiveAccountChanged() {

        // Clear the position display.
        clearPositionDisplay();
        clearOrderBook();

        // Get a handle to the AccountData class that we will use throughout the method.
        AccountData accountData = t4HostService.getAccountData();

        // Select the active account.
        List<Account> accounts = accountData.getAccounts();
        int activeAccountIndex = accounts.indexOf(accountData.getActiveAccount());
        if (activeAccountIndex >= 0) {
            accountPickerComboBox.getSelectionModel().select(activeAccountIndex);
        }

        initializeAccountStatus();
    }

    public void onAccountSelectionChanged() {

        // Switch active account.
        Account selctedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();

        if (selctedAccount != null && !selctedAccount.getAccountID().equals(t4HostService.getAccountData().getActiveAccountID())) {
            t4HostService.getAccountData().setActiveAccount(selctedAccount.getAccountID(), true);
        }
    }

    //endregion

    //region Account Status (P%L)

    AccountProfitUpdater accountProfitUpdater;

    AccountProfitUpdater.UpdateHandler accountProfitUpdateHandler = new AccountProfitUpdater.UpdateHandler() {
        @Override
        public void onActiveAccountChanged(Account acct) {

        }

        @Override
        public void onAccountProfitUpdated(AccountProfit profit) {
            // Update the account P&L and Cash.
            Platform.runLater(() -> MainController.this.onAccountProfitUpdated(profit));
        }

        @Override
        public void onPositionProfitUpdated(PositionProfit profit) {
            Platform.runLater(() -> MainController.this.onPositionProfitUpdated(profit));
        }
    };

    private void initializeAccountStatus() {

        if (accountProfitUpdater != null) {
            // Clean up previous instance. (Could happen on a window close.)
            accountProfitUpdater.unregisterForUpdates(accountProfitUpdateHandler);
            accountProfitUpdater.disconnect();
            accountProfitUpdater = null;
        }

        accountProfitUpdater = new AccountProfitUpdater();
        accountProfitUpdater.registerForUpdates(accountProfitUpdateHandler);
        accountProfitUpdater.connect(t4HostService);
    }

    private final DecimalFormat cashFormatter = new DecimalFormat("#,##0");

    private void onAccountProfitUpdated(AccountProfit profit) {

        // We can compute P&L based on last trade or bid/offer.
        // We also provide realized, unrealized and settlement P&L. Also cash and net equity.

        profitLossLabel.setText(cashFormatter.format(profit.getPL()));
        cashLabel.setText(cashFormatter.format(profit.getAvailableCash()));
    }

    private void clearAccountStatus() {
        profitLossLabel.setText("-");
        cashLabel.setText("-");
    }

    //endregion

    // region Contract Picker

    static class ContractPickerNode {
        public final String description;

        public ContractPickerNode(String descr) {
            description = descr;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    static class ContractPickerExchangeNode extends ContractPickerNode {
        public final Exchange exchange;

        public ContractPickerExchangeNode(Exchange e) {
            super(e.getDescription());
            this.exchange = e;
        }
    }

    static class ContractPickerContractNode extends ContractPickerNode {
        public final Contract contract;

        public ContractPickerContractNode(Contract c) {
            super(c.getDescription());
            contract = c;
        }
    }

    public void onContractSearchTextFieldKeyPressed(KeyEvent event) {
        initializeContractPickerTreeView();
    }

    private void initializeContractPickerTreeView() {

        if (t4HostService == null) {
            return;
        }

        String searchText = contractSearchTextField.getText();
        TreeItem<ContractPickerNode> rootNode = new TreeItem<>();

        List<Exchange> allExchanges = t4HostService.getMarketData().searchExchanges(searchText);

        Log.d(TAG, "initializeContractPickerTreeView(), Exchange Count: %d", allExchanges.size());

        allExchanges.forEach(exchange -> {

            TreeItem<ContractPickerNode> exchangeNode = new TreeItem<>(new ContractPickerExchangeNode(exchange));
            rootNode.getChildren().add(exchangeNode);
            exchangeNode.setExpanded(searchText.length() > 0);

            List<Contract> contracts = t4HostService.getMarketData().searchExchangeContracts(exchange, searchText);
            contracts.forEach(contract -> exchangeNode.getChildren().add(new TreeItem<>(new ContractPickerContractNode(contract))));
        });

        contractPickerTreeView.setShowRoot(false);
        contractPickerTreeView.setRoot(rootNode);
    }

    public void onSelectContractButtonClicked() {

        TreeItem<ContractPickerNode> selectedNode = contractPickerTreeView.getSelectionModel().getSelectedItem();

        if (selectedNode != null && selectedNode.getValue() instanceof ContractPickerContractNode contractNode) {
            Contract contract = contractNode.contract;

            t4HostService.getMarketData().requestMarketPickerList(
                    contract.getExchangeID(),
                    contract.getContractID(),
                    // We are limiting to just outrights to simplify the demo. (Also why we are ignoring the groupings below.)
                    new MarketPickerGroup(StrategyType.None, 0, 0),
                    new MarketPickerListHandler() {
                        @Override
                        public void onMarketGroupsComplete(String exchangeid, String contractid, List<MarketPickerGroup> groups) {
                            // Not used for this demo.
                            // Could be used to create a tree-view such as:
                            //   StrategyType -> Expiry List
                            //   StrategyType/Expiry -> Call/Put/Strike Price (for options)
                        }

                        @Override
                        public void onMarketPickerListComplete(String exchangeid, String contractid, MarketPickerGroup group, List<MarketPickerMarket> markets) {
                            // Callback will be called on an API thread.
                            Platform.runLater(() -> initializeExpiryPickerComboBox(contract, markets));
                        }
                    });
        }
    }

    private void clearContractPicker() {
        contractPickerTreeView.setRoot(new TreeItem<>());
    }

    //endregion

    //region Market Data Subscription

    private Contract mSelectedContract = null;
    private String mSelectedMarketID = "";
    private Market mSubscribedMarket = null;

    private void initializeExpiryPickerComboBox(Contract contract, List<MarketPickerMarket> markets) {
        mSelectedContract = contract;
        expiryPickerComboBox.getItems().clear();
        expiryPickerComboBox.getItems().addAll(markets);
        expiryPickerComboBox.getSelectionModel().select(0);
    }

    public void onSubscribeMarketDataButtonClicked() {

        // Clean up any previous subscription.
        mSelectedMarketID = "";
        mSubscribedMarket = null;

        // Clear the display.
        clearMarketData();
        clearChartData();

        MarketPickerMarket selectedPickerMarket = expiryPickerComboBox.getSelectionModel().getSelectedItem();

        if (mSelectedContract != null && selectedPickerMarket != null) {

            // Remember the MarketID of the requested market.
            mSelectedMarketID = selectedPickerMarket.MarketID;

            // Subscribe the market.
            subscribeMarket();
        }
    }

    public void onUnsubscribeMarketDataButtonClicked() {

        if (mSubscribedMarket != null) {
            // Unsubscribe the market.
            t4HostService.getMarketData().subscribeForMarketDepth(mSubscribedMarket, DepthBuffer.NoSubscription, DepthLevels.Undefined);
        }

        mSelectedMarketID = "";
        mSubscribedMarket = null;

        // Clear the display.
        Platform.runLater(this::clearMarketData);
    }

    private void subscribeMarket() {

        if (mSubscribedMarket == null && mSelectedContract != null && mSelectedMarketID.length() > 0) {

            // Obtain a handle to the market from the API.
            mSubscribedMarket = t4HostService.getMarketData().getMarket(mSelectedContract.getExchangeID(), mSelectedContract.getContractID(), mSelectedMarketID);

            // Note: Markets are loaded on demand from the server. It's possible that MarketData.getMarket() will return null. In this case, wait
            //       for the markets to be loaded and the MarketDataHandler to be raised.

            if (mSubscribedMarket != null) {
                // Subscribe for market data.
                t4HostService.getMarketData().subscribeForMarketDepth(mSubscribedMarket, DepthBuffer.Smart, DepthLevels.Normal);

                // Update the market description.
                subscribedMarketDecsriptionLabel.setText(mSubscribedMarket.getDescription());

                // Reregister in order to get the position snapshot.
                accountProfitUpdater.registerForUpdates(accountProfitUpdateHandler);

                // Reset the order submission fields.
                resetOrderEntryFields();

            } else {
                Log.d(TAG, "subscribeMarket(), Market %s is not loaded. Waiting for markets to load for contract: %s/%s", mSelectedMarketID, mSelectedContract.getExchangeID(), mSelectedContract.getContractID());
            }
        }
    }

    private void onMarketUpdate(MarketDataSnapshot snapshot) {
        //Log.d(TAG, "onMarketUpdate(), Snapshot: %s", snapshot.toString());

        if (!snapshot.isEmpty()) {

            // Update the high, low and ttv.
            highPriceLabel.setText(PriceFormat.convertPriceToDisplayFormat(snapshot.getHighPrice(), snapshot.Market));
            lowPriceLabel.setText(PriceFormat.convertPriceToDisplayFormat(snapshot.getHighPrice(), snapshot.Market));
            totalTradedVolumeLabel.setText(snapshot.TotalTradedVolume().toString());

            // Update the market mode.
            marketModeLabel.setText(snapshot.getMarketMode().getName());

            if (snapshot.getMarketMode().equals(MarketMode.Open)) {
                marketModeLabel.setTextFill(Color.web("#04b904"));
            } else if (snapshot.getMarketMode().equals(MarketMode.PreOpen)) {
                marketModeLabel.setTextFill(Color.web("#ff8804"));
            } else if (snapshot.getMarketMode().equals(MarketMode.Closed)) {
                marketModeLabel.setTextFill(Color.web("#ff0000"));
            } else {
                marketModeLabel.setTextFill(Color.web("#969696"));
            }

            // Update Bids.
            final ObservableList<MarketDataSnapshot.DepthItem> bidData = FXCollections.observableArrayList();
            bidData.addAll(snapshot.getBids());
            marketDepthBidsTableView.getItems().setAll(bidData);

            // Update Offers.
            final ObservableList<MarketDataSnapshot.DepthItem> offerData = FXCollections.observableArrayList();
            offerData.addAll(snapshot.getOffers());
            marketDepthOffersTableView.getItems().setAll(offerData);

            // Update the last trade.
            lastTradeLabel.setText(String.format("%s@%s", Integer.toString(snapshot.LastTradeVolume()), PriceFormat.convertPriceToDisplayFormat(snapshot.LastTradePrice(), snapshot.Market)));
        }
    }

    private void clearMarketData() {

        subscribedMarketDecsriptionLabel.setText("-");
        highPriceLabel.setText("-");
        lowPriceLabel.setText("-");
        totalTradedVolumeLabel.setText("-");
        marketModeLabel.setText("-");
        marketModeLabel.setTextFill(Color.web("#969696"));

        marketDepthBidsTableView.getItems().clear();
        marketDepthOffersTableView.getItems().clear();

        currentPositionLabel.setText("-");
        currentWorkingLabel.setText("-");
        currentPLLabel.setText("-");

        lastTradeLabel.setText("-");

        currentPositionLabel.setText("-");
        currentWorkingLabel.setText("-");
        currentPLLabel.setText("-");
    }

    //endregion

    //region Order Submission

    public void bidRadioButtonClicked() {
        // Set the order price to 5 ticks below the market.
        if (mSubscribedMarket == null) {
            return;
        }

        MarketDataSnapshot snapshot = t4HostService.getMarketData().getMarketDataSnapshot(mSubscribedMarket.getMarketID());
        if (snapshot == null) {
            return;
        }

        PriceVolume bestBid = snapshot.bestBidWithImplied();
        if (bestBid.isEmpty()) {
            return;
        }

        Price adjustedSubmitPrice = bestBid.Price.addIncrements(mSubscribedMarket, -5);
        orderPriceTextField.setText(PriceFormat.convertPriceToDisplayFormat(adjustedSubmitPrice, mSubscribedMarket));
    }

    public void offerRadioButtonClicked() {
        if (mSubscribedMarket == null) {
            return;
        }

        MarketDataSnapshot snapshot = t4HostService.getMarketData().getMarketDataSnapshot(mSubscribedMarket.getMarketID());
        if (snapshot == null) {
            return;
        }

        PriceVolume bestOffer = snapshot.bestOfferWithImplied();
        if (bestOffer.isEmpty()) {
            return;
        }

        Price adjustedSubmitPrice = bestOffer.Price.addIncrements(mSubscribedMarket, 5);
        orderPriceTextField.setText(PriceFormat.convertPriceToDisplayFormat(adjustedSubmitPrice, mSubscribedMarket));
    }

    public void orderSubmitButtonClicked() {
        if (mSubscribedMarket == null) {
            // NOTE: A market subscription isn't required to submit an order for the API. However it doesn't make any sense
            //       in the context of this demo (and any real-world trading app.)
            alert(Alert.AlertType.ERROR, "No Market Selected", "Subscribe a market before submitting an order.");
            return;
        }

        if (!orderBidRadioButton.isSelected() && !orderOfferRadioButton.isSelected()) {
            alert(Alert.AlertType.ERROR, "Bid or Offer?", "Choose bid or offer.");
            return;
        }

        String accountID = t4HostService.getAccountData().getActiveAccountID();
        Market market = mSubscribedMarket;
        BuySell side = orderBidRadioButton.isSelected() ? BuySell.Buy : BuySell.Sell;
        PriceType priceType = PriceType.Limit;
        TimeType timeType = TimeType.Normal;
        int volume = orderVolumeSpinner.getValue();
        int maxShow = 0;

        Price limitPrice = PriceFormat.displayToPrice(orderPriceTextField.getText(), mSubscribedMarket);
        if (limitPrice == null) {
            alert(Alert.AlertType.ERROR, "Invalid Order Price", "The order price is invalid.");
        }

        Price stopPrice = null;
        Price trailPrice = null;

        OrderSubmit orderSubmit = new OrderSubmit();
        orderSubmit.add(accountID, market, side, priceType, timeType, volume, maxShow, limitPrice, stopPrice, trailPrice);

        // Validate the order (optional.)
        boolean validated = orderSubmit.validate(t4HostService.getMarketData().getMarketDataSnapshot(mSubscribedMarket.getMarketID()));

        if (!validated) {
            alert(Alert.AlertType.ERROR, "Order Validation Failed", orderSubmit.getValidationMessage());
            return;
        }

        // Submit the order.
        t4HostService.getAccountData().submitNewOrder(orderSubmit);

        // Check the submit result.
        if (orderSubmit.getSubmitResult() == SubmitResult.SubmitSuccess) {
            alert(Alert.AlertType.CONFIRMATION, "Order Submitted", "Order was submitted successfully.");
        } else {
            alert(Alert.AlertType.ERROR, "Order Submission Failed", orderSubmit.getSubmitMessage());
        }
    }

    private void resetOrderEntryFields() {

        orderBidRadioButton.setSelected(false);
        orderOfferRadioButton.setSelected(false);
        orderPriceTextField.setText("");
    }

    private void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    //endregion

    //region Position Display

    private void onPositionProfitUpdated(PositionProfit profit) {

        // Update the position details, only for the market we are subscribed to.
        if (mSubscribedMarket != null && profit.MarketID.equals(mSubscribedMarket.getMarketID())) {
            currentPositionLabel.setText(String.format("%d-%d", profit.getBuys(), profit.getSells()));
            currentWorkingLabel.setText(String.format("%d-%d", profit.getWorkingBuys(), profit.getWorkingSells()));
            currentPLLabel.setText(cashFormatter.format(profit.getPL()));
        }

        // Update the position display.
        final ObservableList<PositionDisplay> positionData = FXCollections.observableArrayList();
        Iterable<Position> thePositions = t4HostService.getAccountData().getActiveAccount().getPositions();

        for (Position pos : thePositions) {
            Market market = t4HostService.getMarketData().getMarket(pos.MarketID);
            PositionProfit pp = accountProfitUpdater.getPositionProfit(pos.MarketID);

            if (market != null && pp != null) {
                positionData.add(new PositionDisplay(market, pp));
            }
        }
        positionsTableView.getItems().setAll(positionData);

    }

    private void clearPositionDisplay() {
        positionsTableView.getItems().clear();
    }

    //endregion

    //region Order Book

    private void displayOrders() {
        Account account = t4HostService.getAccountData().getActiveAccount();

        final ObservableList<OrderDisplay> orderData = FXCollections.observableArrayList();

        if (account != null) {
            for (Position position : account.getPositions()) {
                Market market = t4HostService.getMarketData().getMarket(position.MarketID);
                for (Order order : position.getOrders()) {
                    orderData.add(new OrderDisplay(market, order));
                }
            }
        }

        orderbookTableView.getItems().setAll(orderData);
    }

    private void clearOrderBook() {
        orderbookTableView.getItems().clear();
    }

    //endregion

    //region Chart Data

    private DataLoadArgs mSubscribedChartDataArgs = null;

    public void onSubscribeChartDataButtonClicked() {

        if (mSubscribedChartDataArgs != null) {
            // Unsubscribing the previous chart data isn't necessary.
            mSubscribedChartDataArgs = null;
        }

        ExpiryMarket expiryMarket = new ExpiryMarket(mSubscribedMarket);

        NDateTime selectedContractTradeDate = mSelectedContract.getTradeDate(NDateTime.now());

        mSubscribedChartDataArgs = new DataLoadArgs.Builder()
                .setMarket(expiryMarket)
                .setSession(SessionTimeRange.Empty)
                .setChartType(ChartType.Bar)
                .setCloseWithSettlement(false)
                .setBarInterval(barIntervalComboBox.getSelectionModel().getSelectedItem())
                .setAnchorToSession(false)
                .createDataLoadArgs();

        t4HostService.getChartData().loadBarData(mSubscribedChartDataArgs, selectedContractTradeDate);
    }

    public void onUnsubscribeChartDataButtonClicked() {
        mSubscribedChartDataArgs = null;
        chartDataTableView.getItems().clear();
    }

    private void updateChartDataDisplay() {

        final ObservableList<BarDataPoint> chartDataPoints = FXCollections.observableArrayList();

        // Get the updated chart data.
        BarDataSeries barDataSeries = t4HostService.getChartData().getBarDataSeries(mSubscribedChartDataArgs);

        for (IBarDataPoint iBarDataPoint : barDataSeries) {
            if (iBarDataPoint instanceof BarDataPoint barDataPoint) {
                chartDataPoints.add(barDataPoint);
            }
        }

        chartDataTableView.getItems().setAll(chartDataPoints.sorted((o1, o2) -> -1 * o1.getTime().compareTo(o2.getTime())));
    }

    private void clearChartData() {
        chartDataTableView.getItems().clear();
    }

    //endregion

    //region Misc.

    public synchronized void resetT4Service() {
        if (t4HostService != null) {
            t4HostService.unregisterHostServiceHandler(hostServiceHandler);
            t4HostService.getMarketData().unregisterForMarketData(marketDataHandler);
            t4HostService.destroy();
            t4HostService = null;
        }
    }

    //endregion
}

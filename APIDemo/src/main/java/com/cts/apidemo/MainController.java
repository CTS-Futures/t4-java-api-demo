package com.cts.apidemo;

import com.cts.apidemo.util.LogUtil;
import com.t4login.JavaHost;
import com.t4login.Log;
import com.t4login.api.*;
import com.t4login.api.accounts.*;
import com.t4login.api.chartdata.ChartDataReader;
import com.t4login.application.chart.ChartInterval;
import com.t4login.application.settings.PriceDisplayMode;
import com.t4login.connection.IMessageHandler;
import com.t4login.connection.ServerType;
import com.t4login.datetime.NDateTime;
import com.t4login.definitions.*;
import com.t4login.definitions.chartdata.ChartDataState;
import com.t4login.definitions.chartdata.ChartDataStreamReaderAggr;
import com.t4login.definitions.chartdata.ChartFormatAggr;
import com.t4login.definitions.priceconversion.IMarketConversion;
import com.t4login.definitions.priceconversion.Price;
import com.t4login.definitions.priceconversion.PriceFormat;
import com.t4login.messages.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    public TableView<Trade> chartDataTableView;
    public Button loadTradeDataButton;
    public Button loadBarsButton;

    public TableColumn<Trade, NDateTime> chartDataDateTableColumn;
    public TableColumn<Trade, NDateTime> chartDataTimeTableColumn;
    public TableColumn<Trade, Integer> chartDataTradePriceTableColumn;
    public TableColumn<Trade, Integer> chartDataVolumeTableColumn;
    public TableColumn<Trade, Integer> chartDataAggressorTableColumn;

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

    public TableView<MatchedTradeDisplay> matchedTradeTableView;
    public TableColumn<MatchedTradeDisplay, NDateTime> matchedTradeTimeTableColumn;
    public TableColumn<MatchedTradeDisplay, String> matchedTradeMarketTableColumn;
    public TableColumn<MatchedTradeDisplay, String> matchedTradeAccountTableColumn;
    public TableColumn<MatchedTradeDisplay, String> matchedTradeRPLTableColumn;
    public TableColumn<MatchedTradeDisplay, String> matchedTradeEntryQtyTableColumn;
    public TableColumn<MatchedTradeDisplay, String> matchedTradeEntryPriceTableColumn;
    public TableColumn<MatchedTradeDisplay, String> matchedTradeClosePriceTableColumn;

    // endregion

    private static final String TAG = "MainController";
    private static final LogUtil logger = new LogUtil(TAG, true);

    private static T4HostService t4HostService;

    private ServerType overrideServerType = ServerType.Unknown;

    private static final String apiBaseURL_Live = "https://api.t4login.com";
    private static final String apiBaseURL_SIM = "https://api-sim.t4login.com";
    //private static final String apiBaseURL_SIM = "http://localhost:63577";
    private static final String apiBaseURL_Test = "https://api-test.t4login.com";

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
                    subscrLevel.update(DepthBuffer.Smart, DepthLevels.Normal, subscribeMBO());
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
        public void onMarketDepthTrade(MarketDepthTrade trade) {
            Market market = mSubscribedMarket;

            if (market != null && trade.getMarketID().equals(market.getMarketID())) {
                //Platform.runLater(() -> MainController.this.onMarketUpdate(snapshot));
                //Log.d(TAG, "onMarketDepthTrade(), Time: %s, Price: %s, Volume: %d", trade.getTime().toString(), trade.getLastTradePrice().toString(), trade.getLastTradeVolume());
                Log.d(TAG, "onMarketDepthTrade(), Time: %s, Price: %s, Volume: %d", trade.getTime().toString(), PriceFormat.convertPriceToDisplayFormat(trade.getLastTradePrice(), market, PriceDisplayMode.RealDecimal), trade.getLastTradeVolume());
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

        @Override
        public void onMarketByOrderReject(String marketID) {
            Log.d(TAG, "onMarketByOrderReject(), SUBSCRIPTION REJECTED: %s", marketID);
        }

        @Override
        public void onMarketByOrderSnapshot(MBOSnapshot snapshot) {
            Log.d(TAG, "onMarketByOrderSnapshot(), SNAPSHOT: %s", snapshot.getMarketID());
        }

        @Override
        public void onMarketByOrderUpdate(MBOUpdate update) {
            Log.d(TAG, "onMarketByOrderUpdate(), UPDATE: %s", update.getMarketID());
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


    //endregion

    //region Startup and Shutdown

    @FXML
    public void initialize() {

        // Set the log level.
        Log.setLogLevel(Log.LogLevel.Debug);

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

        // Set up the chart data table.
        chartDataDateTableColumn.setCellValueFactory(new PropertyValueFactory<>("tradeDate"));
        chartDataDateTableColumn.setCellFactory(new Callback<>() {
            @Override
            public TableCell<Trade, NDateTime> call(TableColumn<Trade, NDateTime> param) {
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
            public TableCell<Trade, NDateTime> call(TableColumn<Trade, NDateTime> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(NDateTime item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item == null || empty) {
                            setText(null);
                        } else {
                            setText(item.toDateTimeString());
                        }
                    }
                };
            }
        });
        chartDataTradePriceTableColumn.setCellValueFactory(new PropertyValueFactory<>("tradePrice"));
        chartDataVolumeTableColumn.setCellValueFactory(new PropertyValueFactory<>("volume"));
        chartDataAggressorTableColumn.setCellValueFactory(new PropertyValueFactory<>("aggressor"));

        // Set up the position table.
        positionMarketTableColumn.setCellValueFactory(new PropertyValueFactory<>("marketDescription"));
        positionNetTableColumn.setCellValueFactory(new PropertyValueFactory<>("netDisplay"));
        positionPLTableColumn.setCellValueFactory(new PropertyValueFactory<>("pLDisplay"));
        positionSideTableColumn.setCellValueFactory(new PropertyValueFactory<>("positionDisplay"));
        postionWorkingTableColumn.setCellValueFactory(new PropertyValueFactory<>("workingDisplay"));

        // Set up the order book table.
        orderbookTimeTableColumn.setCellValueFactory(new PropertyValueFactory<>("submitTime"));
        orderbookMarketTableColumn.setCellValueFactory(new PropertyValueFactory<>("marketDescription"));
        orderbookSideTableColumn.setCellValueFactory(new PropertyValueFactory<>("sideDisplay"));
        orderbookVolumeTableColumn.setCellValueFactory(new PropertyValueFactory<>("volumeDisplay"));
        orderbookOrderTypeTableColumn.setCellValueFactory(new PropertyValueFactory<>("orderTypeDisplay"));
        orderbookOrderStatusTableColumn.setCellValueFactory(new PropertyValueFactory<>("statusDisplay"));

        // Set up the matched trade table.
        matchedTradeTimeTableColumn.setCellValueFactory(new PropertyValueFactory<>("closeTime"));
        matchedTradeMarketTableColumn.setCellValueFactory(new PropertyValueFactory<>("marketDescription"));
        matchedTradeAccountTableColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        matchedTradeRPLTableColumn.setCellValueFactory(new PropertyValueFactory<>("rPLDisplay"));
        matchedTradeEntryQtyTableColumn.setCellValueFactory(new PropertyValueFactory<>("volumeDisplay"));
        matchedTradeEntryPriceTableColumn.setCellValueFactory(new PropertyValueFactory<>("entryPriceDisplay"));
        matchedTradeClosePriceTableColumn.setCellValueFactory(new PropertyValueFactory<>("closePriceDisplay"));

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

    private String getAppName() {
        String appName = System.getenv("T4DEMO_APPLICATION");
        appName = appName == null ? "T4Example" : appName;
        return appName;
    }

    private String getAppLicense() {
        String appLicense = System.getenv("T4DEMO_APPLICENSE");
        appLicense = appLicense == null ? "112A04B0-5AAF-42F4-994E-FA7CB959C60B" : appLicense;
        return appLicense;
    }

    private void login(ServerType serverType, String firm, String username, String password, String newPassword) {

        resetT4Service();

        // Initialize the T4 host service.
        t4HostService = new T4HostService();
        t4HostService.initialize(new JavaHost(
                getAppName(),
                getAppLicense(),
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
        accountPickerComboBox.getItems().clear();
        List<Account> accounts = accountData.getAccounts();
        accountPickerComboBox.getItems().addAll(accounts);

        // Select the active account.
        accountPickerComboBox.getSelectionModel().select(0);

        initializeAccountStatus();
    }

    public void onAccountSelectionChanged() {

        // Update the position and order displays.
        Account selctedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();

        displayOrders();
        displayPositions();
        displayAccountProfit();
    }

    //endregion

    //region Account Status (P%L)

    AccountProfitUpdater accountProfitUpdater;

    AccountProfitUpdater.UpdateHandler accountProfitUpdateHandler = new AccountProfitUpdater.UpdateHandler() {

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

        if (accountProfitUpdater == null) {
            accountProfitUpdater = new AccountProfitUpdater();
            accountProfitUpdater.registerForUpdates(accountProfitUpdateHandler);
            accountProfitUpdater.connect(t4HostService);
        }
    }

    private final DecimalFormat cashFormatter = new DecimalFormat("#,##0");

    private void onAccountProfitUpdated(AccountProfit profit) {

        // We can compute P&L based on last trade or bid/offer.
        // We also provide realized, unrealized and settlement P&L. Also cash and net equity.

        Account selectedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();

        if (selectedAccount != null && profit.Account.getAccountID().equals(selectedAccount.getAccountID())) {
            profitLossLabel.setText(cashFormatter.format(profit.getPL()));
            cashLabel.setText(cashFormatter.format(profit.getAvailableCash()));
        }
    }

    private void displayAccountProfit() {

        // Update the position display.
        Account selectedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();

        if (selectedAccount != null) {
            AccountProfit profit = accountProfitUpdater.getAccountProfit(selectedAccount.getAccountID());
            if (profit != null) {
                profitLossLabel.setText(cashFormatter.format(profit.getPL()));
                cashLabel.setText(cashFormatter.format(profit.getAvailableCash()));
            } else {
                profitLossLabel.setText("-");
                cashLabel.setText("-");
            }
        }
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
            t4HostService.getMarketData().subscribeForMarketDepth(mSubscribedMarket, DepthBuffer.NoSubscription, DepthLevels.Undefined, false);
        }

        mSelectedMarketID = "";
        mSubscribedMarket = null;

        // Clear the display.
        Platform.runLater(this::clearMarketData);
    }

    private boolean subscribeMBO() {
        return true;
    }

    private void subscribeMarket() {

        if (mSubscribedMarket == null && mSelectedContract != null && mSelectedMarketID.length() > 0) {

            // Obtain a handle to the market from the API.
            mSubscribedMarket = t4HostService.getMarketData().getMarket(mSelectedContract.getExchangeID(), mSelectedContract.getContractID(), mSelectedMarketID);

            // Note: Markets are loaded on demand from the server. It's possible that MarketData.getMarket() will return null. In this case, wait
            //       for the markets to be loaded and the MarketDataHandler to be raised.

            if (mSubscribedMarket != null) {
                // Subscribe for market data.
                t4HostService.getMarketData().subscribeForMarketDepth(mSubscribedMarket, DepthBuffer.Smart, DepthLevels.Normal, subscribeMBO());

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

            //Log.d(TAG, "onMarketUpdate(), Market: %s, Price: %s, Real Decimal: %s", snapshot.Market.getMarketID(), snapshot.LastTradePrice().toString(), Double.toString(PriceFormat.convertPriceToRealDecimal(snapshot.LastTradePrice(), snapshot.Market)));
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

        Account selctedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();
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

        if (side.equals(BuySell.Buy)) {
            MarketDataSnapshot snapshot = t4HostService.getMarketData().getMarketDataSnapshot(mSubscribedMarket.getMarketID());
            stopPrice = snapshot.bestOffer().Price.addIncrements(mSubscribedMarket, 10);
        } else {
            MarketDataSnapshot snapshot = t4HostService.getMarketData().getMarketDataSnapshot(mSubscribedMarket.getMarketID());
            stopPrice = snapshot.bestBid().Price.addIncrements(mSubscribedMarket, -10);
        }

        //submitOCOOrder(selctedAccount, mSubscribedMarket, side, timeType, volume, limitPrice, stopPrice);

        OrderSubmit orderSubmit = new OrderSubmit();
        orderSubmit.add(selctedAccount.getAccountID(), market, side, priceType, timeType, volume, maxShow, limitPrice, stopPrice, trailPrice);

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

            // Get the order ID's.
            List<String> orderIDs = new ArrayList<>();

            for (int i = 0; i < orderSubmit.count(); i++) {
                String orderID = orderSubmit.get(i).mOrder.getUniqueID();
                orderIDs.add(orderID);
            }


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

    private void submitOCOOrder(Account account, Market market, BuySell side, TimeType timeType, int volume, Price limitPrice, Price stopPrice) {

        boolean acctsLoaded =  t4HostService.getAccountData().areAccountsLoaded();

        

        int maxShow = 0;
        Price trailPrice = null;

        OrderSubmit orderSubmit = new OrderSubmit();
        orderSubmit.add(account.getAccountID(), market, side, PriceType.Limit, timeType, volume, maxShow, limitPrice, null, trailPrice, "OCO01");
        orderSubmit.add(account.getAccountID(), market, side, PriceType.StopMarket, timeType, volume, maxShow, null, stopPrice, trailPrice, "OCO02");
        orderSubmit.Link = OrderLink.OCO;

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

            // Get the order ID's.
            List<String> orderIDs = new ArrayList<>();

            for (int i = 0; i < orderSubmit.count(); i++) {
                String orderID = orderSubmit.get(i).mOrder.getUniqueID();
                orderIDs.add(orderID);
            }
            alert(Alert.AlertType.CONFIRMATION, "Order Submitted", "Order was submitted successfully.");
        } else {
            alert(Alert.AlertType.ERROR, "Order Submission Failed", orderSubmit.getSubmitMessage());
        }
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

        displayPositions();
        displayMatchedTrades();
    }

    private void displayPositions() {

        // Update the position display.
        Account selectedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();

        if (selectedAccount != null) {
            final ObservableList<PositionDisplay> positionData = FXCollections.observableArrayList();
            Iterable<Position> thePositions = selectedAccount.getPositions();

            for (Position pos : thePositions) {
                Market market = t4HostService.getMarketData().getMarket(pos.MarketID);
                PositionProfit pp = accountProfitUpdater.getPositionProfit(selectedAccount.getAccountID(), pos.MarketID);

                if (market != null && pp != null) {
                    positionData.add(new PositionDisplay(market, pp));
                }
            }

            positionsTableView.getItems().setAll(positionData);
        }
    }

    private void clearPositionDisplay() {
        positionsTableView.getItems().clear();
    }

    private void displayMatchedTrades() {

        Account selectedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();

        if (selectedAccount != null) {
            final ObservableList<MatchedTradeDisplay> positionData = FXCollections.observableArrayList();
            Iterable<Position> thePositions = selectedAccount.getPositions();

            for (Position pos : thePositions) {
                Market market = t4HostService.getMarketData().getMarket(pos.MarketID);
                PositionProfit pp = accountProfitUpdater.getPositionProfit(selectedAccount.getAccountID(), pos.MarketID);

                if (market != null && pp != null) {
                    for (MatchedTrade match : pp.getMatchedTrades()) {
                        positionData.add(new MatchedTradeDisplay(market, selectedAccount, match));
                    }
                }
            }

            matchedTradeTableView.getItems().setAll(positionData);
        }
    }

    private void clearMatchedTrades() {

    }

    //endregion

    //region Order Book

    private void displayOrders() {
        Account selctedAccount = accountPickerComboBox.getSelectionModel().getSelectedItem();

        final ObservableList<OrderDisplay> orderData = FXCollections.observableArrayList();

        if (selctedAccount != null) {
            for (Position position : selctedAccount.getPositions()) {
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

    private final HttpClient client = HttpClient.newHttpClient();

    public static class Trade {
        private final NDateTime _tradeDate;
        private final NDateTime _time;
        private final Price _tradePrice;
        private final int _volume;
        private final BidOffer _aggressor;

        public Trade(NDateTime tradeDate, NDateTime tm, Price tradePrice, int vol, BidOffer aggr) {
            _tradeDate = tradeDate;
            _time = tm;
            _tradePrice = tradePrice;
            _volume = vol;
            _aggressor = aggr;
        }

        public NDateTime getTradeDate() {
            return _tradeDate;
        }

        public NDateTime getTime() {
            return _time;
        }

        public Price getTradePrice() {
            return _tradePrice;
        }

        public int getVolume() {
            return _volume;
        }

        public BidOffer getAggressor() {
            return _aggressor;
        }
    }

    public void onSubscribeChartDataButtonClicked() throws ExecutionException, InterruptedException {
        downloadTradeDataAsync();
    }

    public void onLoadBarsButtonClicked() {
        downloadBarDataAsync();
    }

    private String apiToken = "";
    private NDateTime apiTokenExpiryUTC = NDateTime.MinValue;

    private void downloadTradeDataAsync() {

        // Initialize the chart API.
        ServerType serverType = t4HostService.getUserData().getServerType();

        final String chartBaseURL;
        switch (serverType) {
            case Test -> chartBaseURL = apiBaseURL_Test;
            case Simulator -> chartBaseURL = apiBaseURL_SIM;
            default -> chartBaseURL = apiBaseURL_Live;
        }

        CompletableFuture.runAsync(() -> {

            // Ensure the API token is usable.
            refreshAPITokenViaHost();
            //refreshAPITokenViaLoginAPI();

            if (apiToken.isEmpty() || NDateTime.utcNow().AddSeconds(30.0).isAfter(apiTokenExpiryUTC)) {
                Log.e(TAG, "downloadTradeDataAsync(), Failed to refresh API token.");
                return;
            }

            // Note: t4HostService.getRemoteTime() is the best way to get the system time (CST.)
            // Note: The Contract object will tell you the trade date for a given time in CST.
            NDateTime startDate = mSubscribedMarket.Contract.getTradeDate(t4HostService.getRemoteTime());
            NDateTime endDate = mSubscribedMarket.Contract.getTradeDate(t4HostService.getRemoteTime());

//            String url = chartBaseURL +
//                    "/chart/tradehistory" +
//                    "?exchangeID=" + URLEncoder.encode(mSubscribedMarket.getExchangeID()) +
//                    "&contractID=" + URLEncoder.encode(mSubscribedMarket.getContractID()) +
//                    "&marketID=" + URLEncoder.encode(mSubscribedMarket.getMarketID()) +
//                    // Note: t4HostService.getRemoteTime() is the best way to get the system time (CST.)
//                    // Note: The Contract object will tell you the trade date for a given time in CST.
//                    "&tradeDateStart=" + URLEncoder.encode(startDate.toDateString()) +
//                    "&tradeDateEnd=" + URLEncoder.encode(endDate.toDateString());

            String url = "https://api-sim.t4login.com/chart/tradehistory?exchangeID=CME_Eq&contractID=ES&marketID=XCME_Eq+ES+%28Z23%29&tradeDateStart=2023-10-19&tradeDateEnd=2023-10-19&since=2023-10-19T10%3A02%3A08.0000000";

            Log.d(TAG, "downloadTradeDataAsync(), Sending trade data request: %s", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();

            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                Log.d(TAG, "downloadTradeDataAsync(), Response: %d", response.statusCode());

                if (response.statusCode() == 200) {

                    InputStream inputStream = response.body();

                    // The chart API returns a binary-encoded T4 platform message called MsgChartDataBatch
                    //  when the Accept header is set to 'application/octet-stream' or 'application/t4'
                    MsgChartDataBatch chartDataMessage = (MsgChartDataBatch) Message.getMessage(inputStream);

                    Log.d(TAG, "downloadTradeDataAsync(), Received: %s", chartDataMessage);

                    // The chart data is most efficiently (CPU and memory) read using a ChartDataReader.
                    ChartDataReader chartReader = new ChartDataReader(chartDataMessage, true);

                    ObservableList<Trade> trades = FXCollections.observableArrayList();

                    while (chartReader.read()) {
                        ChartDataState state = chartReader.getState();

                        switch (state.Change) {
                            case Trade -> {
                                if (!state.DueToSpread && state.TradeVolume > 0) {
                                    NDateTime tradeTime = new NDateTime(state.LastTimeTicks);
                                    long tradeTimeEpochMillis = tradeTime.toEpochMS();
                                    NDateTime tradeDate = state.TradeDate;
                                    Price tradePrice = state.LastTradePrice;
                                    int tradeVolume = state.TradeVolume;
                                    BidOffer aggressor = state.AtBidOrOffer;

                                    // Normal prices (needed if displaying a continuation style chart with data from multiple exipies.
                                    if (!state.getMinPriceIncrement().equals(mSubscribedMarket.getMinPriceIncrement())) {
                                        // Normalize price to current market.
                                        BigDecimal tradeValue = tradePrice.toCash(state);
                                        tradePrice = Price.fromCash(mSubscribedMarket, tradeValue);
                                    }

                                    trades.add(new Trade(tradeDate, tradeTime, tradePrice, tradeVolume, aggressor));
                                }
                            }
                        }
                    }

                    Log.d(TAG, "downloadTradeDataAsync(), Received %d trades.", trades.size());

                    // Marshaling back to the GUI thread
                    Platform.runLater(() -> updateChartDataDisplay(trades));

                } else {

                    InputStream inputStream = response.body();
                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                        String responseBody = bufferedReader.lines().collect(Collectors.joining("\n"));
                        Log.e(TAG, "downloadTradeDataAsync(), Request failed with status code: %d, body: %s", response.statusCode(), responseBody);
                        return;
                    } catch (Exception ex) {
                    }

                    Log.e(TAG, "downloadTradeDataAsync(), Request failed with status code: %d", response.statusCode());
                }

            } catch (IOException | InterruptedException ex) {
                Log.e(TAG, "downloadTradeDataAsync(), Request failed with exception", ex);
                throw new RuntimeException(ex);
            }
        });
    }


    private void downloadBarDataAsync() {

        // Initialize the chart API.
        ServerType serverType = t4HostService.getUserData().getServerType();

        final String chartBaseURL;
        switch (serverType) {
            case Test -> chartBaseURL = apiBaseURL_Test;
            case Simulator -> chartBaseURL = apiBaseURL_SIM;
            default -> chartBaseURL = apiBaseURL_Live;
        }

        CompletableFuture.runAsync(() -> {

            // Ensure the API token is usable.
            //refreshAPITokenViaHost();
            refreshAPITokenViaLoginAPI();

            if (apiToken.isEmpty() || NDateTime.utcNow().AddSeconds(30.0).isAfter(apiTokenExpiryUTC)) {
                Log.e(TAG, "downloadBarDataAsync(), Failed to refresh API token.");
                return;
            }

            // Note: t4HostService.getRemoteTime() is the best way to get the system time (CST.)
            // Note: The Contract object will tell you the trade date for a given time in CST.
            NDateTime endDate = mSubscribedMarket.Contract.getTradeDate(t4HostService.getRemoteTime());
            NDateTime startDate = endDate.AddDays(-100);

            String url = chartBaseURL +
                    "/chart/barchart" +
                    "?exchangeID=" + URLEncoder.encode(mSubscribedMarket.getExchangeID()) +
                    "&contractID=" + URLEncoder.encode(mSubscribedMarket.getContractID()) +
                    "&marketID=" + URLEncoder.encode(mSubscribedMarket.getMarketID()) +
                    // Note: t4HostService.getRemoteTime() is the best way to get the system time (CST.)
                    // Note: The Contract object will tell you the trade date for a given time in CST.
                    "&tradeDateStart=" + URLEncoder.encode(startDate.toDateString()) +
                    "&tradeDateEnd=" + URLEncoder.encode(endDate.toDateString()) +
                    "&charttype=" + ChartType.Bar.getValue() +
                    "&barinterval=" + ChartInterval.Day.getValue() +
                    "&barperiod=" + 1;


            Log.d(TAG, "downloadBarDataAsync(), Sending trade data request: %s", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Accept", "application/octet-stream")
                    .build();

            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                Log.d(TAG, "downloadBarDataAsync(), Response: %d", response.statusCode());

                if (response.statusCode() == 200) {

                    InputStream inputStream = response.body();

                    // The chart API returns a binary-encoded T4 platform message called MsgChartDataBatch
                    //  when the Accept header is set to 'application/octet-stream' or 'application/t4'
                    MsgChartAggregatedData chartDataMessage = (MsgChartAggregatedData) Message.getMessage(inputStream);

                    Log.d(TAG, "downloadBarDataAsync(), Received: %s", chartDataMessage);

                    // The chart data is most efficiently (CPU and memory) read using a ChartDataReader.
                    ChartDataStreamReaderAggr.read(chartDataMessage.Data, new ChartDataStreamReaderAggr.ChartDataHandler() {
                        @Override
                        public void onMarketDefinition(ChartFormatAggr.MarketDefinition marketDefinition) {
                            // Handle as needed.
                        }

                        @Override
                        public void onBar(ChartFormatAggr.Bar bar) {
                            Log.d(TAG, "Bar: Trade Date: %s, Time: %s, O: %s, H: %s, L: %s, C: %s, Volume: %d, Market: %s",
                                    bar.TradeDate.toDateString(),
                                    bar.Time.toDateTimeString(),
                                    bar.OpenPrice,
                                    bar.HighPrice,
                                    bar.LowPrice,
                                    bar.ClosePrice,
                                    bar.Volume,
                                    bar.MarketID);
                        }

                        @Override
                        public void onModeChange(String s, NDateTime nDateTime, NDateTime nDateTime1, MarketMode marketMode) {
                            // Handle as needed.
                        }

                        @Override
                        public void onSettlement(String s, NDateTime nDateTime, NDateTime nDateTime1, Price price, boolean b) {
                            // Handle as needed.
                        }

                        @Override
                        public void onOpenInterest(String s, NDateTime nDateTime, NDateTime nDateTime1, int i) {
                            // Handle as needed.
                        }
                    });

                } else {
                    Log.e(TAG, "downloadBarDataAsync(), Request failed with status code: %d", response.statusCode());
                }

            } catch (IOException | InterruptedException ex) {
                Log.e(TAG, "downloadBarDataAsync(), Request failed with exception", ex);
                throw new RuntimeException(ex);
            }
        });
    }

    /**
     * This method demonstrates how to get an API token via the T4 login API.
     */
    private void refreshAPITokenViaLoginAPI() {

        if (apiToken.isEmpty() || NDateTime.utcNow().AddSeconds(30.0).isAfter(apiTokenExpiryUTC)) {

            // Initialize the chart API.
            ServerType serverType = t4HostService.getUserData().getServerType();

            final String loginBaseURL;
            switch (serverType) {
                case Test -> loginBaseURL = apiBaseURL_Test;
                case Simulator -> loginBaseURL = apiBaseURL_SIM;
                default -> loginBaseURL = apiBaseURL_Live;
            }

            String url = loginBaseURL + "/login";

            Log.d(TAG, "refreshAPITokenViaLoginAPI(), Sending login request: %s", url);

            JSONObject loginBody = new JSONObject();
            loginBody.put("userName", usernameInput.getText());
            loginBody.put("password", passwordInput.getText());
            loginBody.put("firm", firmInput.getText());
            loginBody.put("appName", getAppName());
            loginBody.put("appLicense", getAppLicense());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginBody.toString()))
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                Log.d(TAG, "downloadTradeDataAsync(), Response: %d", response.statusCode());

                if (response.statusCode() == 200) {

                    // Save the token.
                    String apiTokenResponseBody = response.body();
                    JSONObject apiTokenResponse = new JSONObject(apiTokenResponseBody);

                    if (apiTokenResponse.has("token")) {
                        apiToken = apiTokenResponse.getString("token");
                        apiTokenExpiryUTC = NDateTime.fromUnixTimeStamp(apiTokenResponse.getLong("expires"));
                        Log.d(TAG, "refreshAPITokenViaLoginAPI(), Refreshed API token. Expires: %s (UTC)", apiTokenExpiryUTC.toDateTimeString());
                    } else {
                        if (apiTokenResponse.has("failReason")) {
                            Log.e(TAG, "refreshAPITokenViaLoginAPI(), Token refresh failed with reason: %s", apiTokenResponse.get("failReason"));
                        } else {
                            Log.e(TAG, "refreshAPITokenViaLoginAPI(), Token refresh failed.");
                        }
                    }
                } else {
                    Log.e(TAG, "refreshAPITokenViaLoginAPI(), Request failed with status code: %d", response.statusCode());
                }
            } catch (IOException | InterruptedException ex) {
                Log.e(TAG, "refreshAPITokenViaLoginAPI(), Request failed with exception", ex);
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * This method demonstrates how to get an API token via the T4 backend directly.
     */
    private void refreshAPITokenViaHost() {

        if (apiToken.isEmpty() || NDateTime.utcNow().AddSeconds(30.0).isAfter(apiTokenExpiryUTC)) {

            Log.d(TAG, "refreshAPITokenViaHost(), Refreshing API token via host.");

            // Refresh the API token.
            BlockingQueue<MsgAuthenticationTokenResponse> channel = new LinkedBlockingQueue<>();

            IMessageHandler messageHandler = new IMessageHandler() {
                @Override
                public void onMessage(Message message) {
                    if (message.getMessageType().equals(MessageType.AuthenticationTokenResponse)) {
                        try {
                            channel.put((MsgAuthenticationTokenResponse) message);
                        } catch (InterruptedException ex) {
                            Log.e(TAG, "refreshAPITokenViaHost(), Failed to publish authentication response to the channel", ex);
                        }
                    }
                }
            };

            try {
                t4HostService.registerMessageHandler(messageHandler);

                MsgAuthenticationTokenRequest tokenRequest = new MsgAuthenticationTokenRequest();
                tokenRequest.UserID = t4HostService.getUserData().getUser().getUserID();
                t4HostService.sendMessage(tokenRequest);

                // Wait for the response from the T4 backend.
                MsgAuthenticationTokenResponse tokenResponse = channel.take();

                if (tokenResponse.FailureReason.isEmpty()) {
                    apiToken = tokenResponse.Token;
                    apiTokenExpiryUTC = tokenResponse.ExpiresUTC;
                    Log.d(TAG, "refreshAPITokenViaHost(), Refreshed API token. Expires: %s (UTC)", apiTokenExpiryUTC.toDateTimeString());
                } else {
                    Log.e(TAG, "refreshAPITokenViaHost(), Token request failed: %s", tokenResponse.FailureReason);
                }
            } catch (Exception ex) {
                Log.e(TAG, "refreshAPITokenViaHost(), Error refreshing authentication token", ex);
            } finally {
                t4HostService.unregisterMessageHandler(messageHandler);
            }
        }
    }

    private void updateChartDataDisplay(ObservableList<Trade> trades) {


        chartDataTableView.getItems().setAll(trades.sorted((o1, o2) -> -1 * o1.getTime().compareTo(o2.getTime())));
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

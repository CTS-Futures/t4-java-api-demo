package com.cts.apidemo;

import com.cts.apidemo.util.LogUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {

    private final static String TAG = "MainApplication";
    private final static LogUtil logger = new LogUtil(TAG, true);

    public static void main(String[] args) {
        logger.log("executing main...");
        launch();
    }

    @Override
    public void start(Stage stage) throws IOException {
        logger.log("starting application...");
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1220, 920);
        stage.setTitle("API Demo");
        stage.setScene(scene);

        MainController controller = fxmlLoader.getController();
        stage.setOnHidden(e -> controller.shutdown());

        stage.show();
    }
}

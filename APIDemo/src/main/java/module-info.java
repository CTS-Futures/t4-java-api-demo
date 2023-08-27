module com.cts.apidemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.t4login;
    requires java.sql;
    requires java.net.http;
    requires org.json;
    requires java.json;

    opens com.cts.apidemo to javafx.fxml;
    exports com.cts.apidemo;
}

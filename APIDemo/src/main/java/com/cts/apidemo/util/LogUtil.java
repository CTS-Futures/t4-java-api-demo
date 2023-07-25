package com.cts.apidemo.util;

public class LogUtil {

    private final static String GLOBAL_TAG = "APIDemo";

    private final static System.Logger logger = System.getLogger("LogUtil");

    private final String tag;

    public LogUtil(String tag) {
        this.tag = tag;
    }

    public LogUtil(String tag, boolean addToGlobalLog) {
        this.tag = addToGlobalLog ? GLOBAL_TAG + ":" + tag : tag;
    }

    public static void log(String logTag, String message) {
        logger.log(System.Logger.Level.DEBUG, "[" + logTag + "] " + message);
        System.out.println("d [" + logTag + "] " + message);
    }

    public void log(String message) {
        logger.log(System.Logger.Level.DEBUG, "[" + tag + "] " + message);
        System.out.println("[" + tag + "] " + message);
    }

    public void log(String message, Throwable t) {
        logger.log(System.Logger.Level.DEBUG, "[" + tag + "] " + message, t);
        System.out.println("[" + tag + "] " + message);
        t.printStackTrace();
    }

    public void err(String message) {
        logger.log(System.Logger.Level.ERROR, "[" + tag + "] " + message);
        System.err.println("[" + tag + "] " + message);
    }

    public void err(String message, Throwable t) {
        logger.log(System.Logger.Level.ERROR, "[" + tag + "] " + message, t);
        System.err.println("[" + tag + "] " + message);
        t.printStackTrace();
    }

    public void warn(String message) {
        logger.log(System.Logger.Level.WARNING, "[" + tag + "] " + message);
        System.err.println("[" + tag + "] " + message);
    }

    public void warn(String message, Throwable t) {
        logger.log(System.Logger.Level.WARNING, "[" + tag + "] " + message, t);
        System.err.println("[" + tag + "] " + message);
        t.printStackTrace();
    }

}


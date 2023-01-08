package ru.nsu.fit.shuvalov.socks.proxy;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Wrong amount of arguments");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            System.err.println("Port number expected");
            return;
        }
        ProxyServer proxyServer = new ProxyServer(port);
        try {
            proxyServer.start();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}

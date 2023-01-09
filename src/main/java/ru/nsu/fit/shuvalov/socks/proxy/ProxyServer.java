package ru.nsu.fit.shuvalov.socks.proxy;

import sun.misc.SignalHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public class ProxyServer {
    private int port = 1080;
    private String ip = "0.0.0.0";
    private Selector selector;

    public ProxyServer(int port, String ip) {
        this.port = port;
        this.ip = ip;
    }

    public ProxyServer(int port) {
        this.port = port;
    }

    public ProxyServer(String ip) {
        this.ip = ip;
    }

    public ProxyServer() {
    }

    private void acceptClient(Selector selector, ServerSocketChannel serverSocket)
            throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
    }

    public void stop() throws IOException {
        selector.close();
    }

    private void registerSignalHandler() {
        SignalHandler signalHandler = sig -> {
            try {
                stop();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        DiagnosticSignalHandler.install("INT", signalHandler);
    }

    private void receiveFromClient(ByteBuffer buffer, SelectionKey key) throws IOException {
        SocketChannel clientSender = (SocketChannel) key.channel();
        clientSender.read(buffer);
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        System.out.println(Arrays.toString(bytes));
        buffer.clear();
        clientSender.close();
    }

    public void start() throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(ip, port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        registerSignalHandler();
        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                try {
                    if (key.isAcceptable()) {
                        acceptClient(selector, serverSocket);
                        iterator.remove();
                        continue;
                    }
                    if (key.isReadable()) {
                        receiveFromClient(buffer, key);
                        iterator.remove();
                        continue;
                    } // TODO parse packet
                } catch (IOException e) {
                    e.printStackTrace();
                    key.cancel();
                }
            }
        }
    }
}

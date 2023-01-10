package ru.nsu.fit.shuvalov.socks.proxy;

import sun.misc.SignalHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ProxyServer {
    private int port = 1080;
    private String ip = "0.0.0.0";
    private Selector selector;
    private final HashMap<SocketChannel, Client> clients = new HashMap<>();

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

    private void acceptClient(Selector selector, ServerSocketChannel serverSocket)
            throws IOException {
        SocketChannel clientChannel = (SocketChannel) serverSocket.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        Client client = new Client();
        client.socketChannel = clientChannel;
        clients.put(clientChannel, client);
    }

    private void receiveConnectionRequest(ByteBuffer buffer, SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Client client = clients.get(clientChannel);
        client.receiveConnectionRequest(buffer, selector);
    }

    private void sendConnectionResponse(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Client client = clients.get(clientChannel);
        client.sendConnectionResponse(selector);
    }

    private void processReadable(ByteBuffer buffer, SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Client client = clients.get(clientChannel);
        ClientData clientData = client.clientData;
        if (clientData.state == ClientData.ClientState.HANDSHAKE) {
            receiveConnectionRequest(buffer, key);
            return;
        }
        if (clientData.state == ClientData.ClientState.CONNECTION_REQUEST_RECEIVED) {
            byte[] bytes = client.receive(buffer);
            if (bytes.length == 0) {
                key.cancel();
            }
            System.out.println("Received: " + Arrays.toString(bytes));
            return;
        }
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
                        processReadable(buffer, key);
                        iterator.remove();
                        continue;
                    }
                    if (key.isWritable()) {
                        sendConnectionResponse(key);
                        iterator.remove();
                        continue;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    key.cancel();
                }
            }
        }
    }

    public void stop() throws IOException {
        selector.close();
    }
}

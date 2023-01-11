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
import java.util.stream.Stream;

public class ProxyServer {
    private int port = 1080;
    private String ip = "0.0.0.0";
    private Selector selector;
    private final HashMap<SocketChannel, Client> clients = new HashMap<>();
    private final HashMap<SocketChannel, Server> servers = new HashMap<>();
    private final HashMap<Client, Server> clientsServers = new HashMap<>();

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
        System.out.println("Client accepted: " + clientChannel);
    }

    private void receiveConnectionRequest(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Client client = clients.get(clientChannel);
        client.receiveConnectionRequest(selector);
        System.out.println("receiveConnectionRequest");
    }

    private void sendConnectionResponse(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Client client = clients.get(clientChannel);
        client.sendConnectionResponse(selector);
    }

    private void processReadable(ByteBuffer buffer, SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Client client = clients.get(socketChannel);
        Server server = null;
        if (client == null) {
            server = servers.get(socketChannel);
            if (server == null) {
                throw new NullPointerException();
            }
        }
        if (client != null) {
            ClientData clientData = client.clientData;
            if (clientData.state == ClientData.ClientState.HANDSHAKE) {
                receiveConnectionRequest(key);
                if (clientData.state == ClientData.ClientState.CONNECTION_REQUEST_RECEIVED) {
                    SocketChannel serverChannel = SocketChannel.open(
                            new InetSocketAddress(
                                    clientData.getDestinationIp(),
                                    clientData.getDestinationPort()
                            )
                    );
                    Server clientsServer = new Server();
                    clientsServer.socketChannel = serverChannel;
                    servers.put(serverChannel, clientsServer);
                    clientsServers.put(client, clientsServer);
                    serverChannel.configureBlocking(false);
                    key.cancel();
                    serverChannel.register(selector, SelectionKey.OP_WRITE);
                    System.out.println("Connect to server: " + serverChannel);
                }
                return;
            }
            if (clientData.state == ClientData.ClientState.CONNECTION_REQUEST_RECEIVED) {
//                byte[] bytes = client.receive();
                if (clientData.getBuffer().remaining() == 0) {
                    key.cancel();
                    socketChannel.register(selector, SelectionKey.OP_WRITE);
                }
//                System.out.println("Received from client: " + Arrays.toString(bytes));
                return;
            }
        }
        if (server != null) {
            byte[] bytes = server.receive();
            if (bytes.length == 0) {
                if (key.isReadable()) {
                    throw new RuntimeException();
                }
                key.cancel();
                socketChannel.register(selector, SelectionKey.OP_WRITE);
            }
            System.out.println("Received from server: " + Arrays.toString(bytes));
            return;
        }
    }

    public <K, V> Stream<K> keys(Map<K, V> map, V value) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> value.equals(entry.getValue()))
                .map(Map.Entry::getKey);
    }

    private void processWritable(ByteBuffer buffer, SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Client client = clients.get(socketChannel);
        Server server = null;
        if (client == null) {
            server = servers.get(socketChannel);
            if (server == null) {
                throw new NullPointerException();
            }
        }
        if (client != null) {
            ClientData clientData = client.clientData;
            if (clientData.state == ClientData.ClientState.HANDSHAKE) {
                sendConnectionResponse(key);
                return;
            }
            if (clientData.state == ClientData.ClientState.CONNECTION_REQUEST_RECEIVED) {
                client.send(buffer);
                key.cancel();
                socketChannel.register(selector, SelectionKey.OP_READ);
                return;
            }
        }
        if (server != null) {
            Stream<Client> keyStream = keys(clientsServers, server);
            Client serversClient = keyStream.findFirst().get();
            server.send(serversClient.clientData.getBuffer());
            key.cancel();
            socketChannel.register(selector, SelectionKey.OP_READ);
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
                        processWritable(buffer, key);
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

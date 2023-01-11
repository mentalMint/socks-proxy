package ru.nsu.fit.shuvalov.socks.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client {
    public final ClientData clientData = new ClientData();
    public SocketChannel socketChannel = null;
    public ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

    public void receiveConnectionRequest(Selector selector) throws IOException {
        socketChannel.read(receiveBuffer);
        receiveBuffer.flip();
        byte[] bytes = new byte[receiveBuffer.remaining()];
        receiveBuffer.get(bytes);
        clientData.addToConnectionRequest(bytes);
        receiveBuffer.clear();
        if (clientData.isParsed()) {
            System.out.println("Request received: " + clientData.getVersion() + " " + clientData.getCommand() + " " +
                    clientData.getDestinationPort() + " " + clientData.getDestinationIp());
            clientData.state = ClientData.ClientState.CONNECTION_REQUEST_RECEIVED;
            socketChannel.register(selector, SelectionKey.OP_WRITE);
        }
    }

    public void sendConnectionResponse(Selector selector) throws IOException {
        if (socketChannel.write(clientData.getToSend()) == 0) {
            System.out.println("Response sent: " + clientData.getToSend());
            socketChannel.register(selector, SelectionKey.OP_READ);
        }
    }

    public void receive() throws IOException {
        socketChannel.read(receiveBuffer);
        receiveBuffer.flip();
    }

    public void send(ByteBuffer buffer) throws IOException {
        while(socketChannel.write(buffer) > 0);
    }
}

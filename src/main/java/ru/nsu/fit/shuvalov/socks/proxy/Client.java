package ru.nsu.fit.shuvalov.socks.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client {
    public final ClientData clientData = new ClientData();
    public SocketChannel socketChannel = null;
    public void receiveConnectionRequest(ByteBuffer buffer, Selector selector) throws IOException {
        socketChannel.read(buffer);
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        clientData.addToConnectionRequest(bytes);
        buffer.clear();
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

    public byte[] receive(ByteBuffer buffer) throws IOException {
        socketChannel.read(buffer);
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.clear();
        return bytes;
    }
}

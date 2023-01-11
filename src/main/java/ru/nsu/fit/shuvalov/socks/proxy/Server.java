package ru.nsu.fit.shuvalov.socks.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Server {
    public SocketChannel socketChannel = null;
    public ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

    public byte[] receive() throws IOException {
        socketChannel.read(receiveBuffer);
        receiveBuffer.flip();
        byte[] bytes = new byte[receiveBuffer.remaining()];
        receiveBuffer.get(bytes);
        receiveBuffer.clear();
        return bytes;
    }

    public void send(ByteBuffer buffer) throws IOException {
        while(socketChannel.write(buffer) > 0);

    }
}

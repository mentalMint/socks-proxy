package ru.nsu.fit.shuvalov.socks.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Iterator;

public class Socks5ProxyServer implements Runnable {
    int bufferSize = 8192;
    int port;
    String host;

    static class Attachment {
        ByteBuffer in = null;
        ByteBuffer out = null;
        SelectionKey peer = null;
        Byte authentication = null;
        byte[] address = null;
        byte[] port = null;
        boolean close = false;
    }

    static byte[] OK = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    @Override
    public void run() {
        try {
            Selector selector = SelectorProvider.provider().openSelector();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(host, port));
            serverChannel.register(selector, serverChannel.validOps());
            int iteration = 0;
            while (selector.select() > -1) {
                System.out.println(iteration++ + ".");
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isValid()) {
                        try {
                            if (key.isAcceptable()) {
                                accept(key);
                            } else if (key.isConnectable()) {
                                connect(key);
                            } else if (key.isReadable()) {
                                read(key);
                            } else if (key.isWritable()) {
                                write(key);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            close(key);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    private void accept(SelectionKey key) throws IOException {
        SocketChannel newChannel = ((ServerSocketChannel) key.channel()).accept();
        newChannel.configureBlocking(false);
        newChannel.register(key.selector(), SelectionKey.OP_READ);
        System.out.println("===Accept===");
        System.out.println(newChannel.socket().getInetAddress() + ":" + newChannel.socket().getPort());
        System.out.println("============");
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        System.out.println("===Read===");
        System.out.println(channel.socket().getInetAddress() + ":" + channel.socket().getPort());
        if (attachment == null) {
            key.attach(attachment = new Attachment());
            attachment.in = ByteBuffer.allocate(bufferSize);
        }
//        System.out.println(key + ": " + attachment.peer);
        if (channel.read(attachment.in) < 1) {
            close(key);
        } else if (attachment.authentication == null) {
            readGreeting(key, attachment);
        } else if (attachment.peer == null) {
            readConnectionRequest(key, attachment);
        } else {
            attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_WRITE);
            key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
            attachment.in.flip();
        }
        System.out.println("==========");
    }

    private void readGreeting(SelectionKey key, Attachment attachment) throws IllegalStateException {
        byte[] greeting = attachment.in.array();
        System.out.println("===Greeting===");
        System.out.println(greeting[0] + " " + greeting[1]);
        System.out.println("==============");
        if (attachment.in.position() >= 2 && attachment.in.position() == 2 + greeting[1]) {
            if (greeting[0] != 5 || greeting[1] < 1 || attachment.in.position() < 1 + greeting[1]) {
                throw new IllegalStateException("Bad Request");
            } else {
                if (ByteUtilities.contains(Arrays.copyOfRange(greeting, 2, greeting[1] + 2), (byte) 0)) {
                    attachment.authentication = 0;
                } else {
                    attachment.authentication = (byte) 0xFF;
                    attachment.close = true;
                }
                attachment.out = ByteBuffer.wrap(new byte[]{5, attachment.authentication});
                key.interestOps(SelectionKey.OP_WRITE);
                attachment.in.clear();
            }
        }
    }

    private int getLengthByAddressType(byte type, byte nextByte) {
        return switch (type) {
            case 1 -> 4;
            case 3 -> nextByte + 1;
            default -> 16;
        };
    }

    private byte[] getAddress(byte[] request) {
        return Arrays.copyOfRange(request, 3, getRequestLength(request) - 2);
    }

    private byte[] getPort(byte[] request) {
        return Arrays.copyOfRange(request, getRequestLength(request) - 2, getRequestLength(request));
    }

    private int getRequestLength(byte[] request) {
        return 6 + getLengthByAddressType(request[3], request[4]);
    }

    private void printRequest(byte[] request) {
        for (int i = 0; i < getRequestLength(request); i++) {
            System.out.print(request[i] + " ");
        }
        System.out.println();
    }

    private void readConnectionRequest(SelectionKey key, Attachment attachment) throws IllegalStateException, IOException {
        byte[] request = attachment.in.array();
        System.out.println("===Connection request===");
        printRequest(request);
        System.out.println("========================");
        if (attachment.in.position() >= 10 && attachment.in.position() == getRequestLength(request)) {
            if (request[0] != 5 || request[1] != 1 || request[2] != 0 || (request[3] != 1 && request[3] != 3)) {
                throw new IllegalStateException("Bad Request");
            } else {
                SocketChannel peer = SocketChannel.open();
                peer.configureBlocking(false);
                if (request[3] == 1) {
                    byte[] addr = new byte[]{request[4], request[5], request[6], request[7]};
                    int port = (((0xFF & request[getRequestLength(request) - 2]) << 8) + (0xFF & request[getRequestLength(request) - 1]));
                    peer.connect(new InetSocketAddress(InetAddress.getByAddress(addr), port));
                    SelectionKey peerKey = peer.register(key.selector(), SelectionKey.OP_CONNECT);
                    key.interestOps(0);
                    attachment.peer = peerKey;
                    Attachment peerAttachment = new Attachment();
                    peerAttachment.peer = key;
                    peerAttachment.address = getAddress(request);
                    peerAttachment.port = getPort(request);
                    peerAttachment.authentication = attachment.authentication;
                    peerKey.attach(peerAttachment);
                    attachment.in.clear();
//                    System.out.println("Addr: " + Arrays.toString(peerAttachment.address));
                } else {
                    throw new IllegalStateException("Bad Request");
                }
            }
        }
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        System.out.println("===Write====");
        System.out.println(channel.socket().getInetAddress() + ":" + channel.socket().getPort());
        if (channel.write(attachment.out) == -1) {
            close(key);
        } else if (attachment.out.remaining() == 0) {
            if (attachment.close) {
                close(key);
            } else {
                attachment.out.clear();
                if (attachment.peer != null) {
                    attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_READ);
                    key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
                } else {
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
        }
        System.out.println("=============");
    }

    private void connect(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        channel.finishConnect();
        attachment.in = ByteBuffer.allocate(bufferSize);
        byte[] connectionResponse = ByteUtilities.concatenate(
                ByteUtilities.concatenate(
                        new byte[]{5, 0, 0},
                        attachment.address
                ),
                attachment.port
        );
        for (byte b : connectionResponse) {
            System.out.print(b + " ");
        }
        System.out.println();
        attachment.in.put(connectionResponse).flip();
        attachment.out = ((Attachment) attachment.peer.attachment()).in;
        ((Attachment) attachment.peer.attachment()).out = attachment.in;
        attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        key.interestOps(0);
        System.out.println("===Connect===");
        System.out.println(channel.socket().getInetAddress() + ":" + channel.socket().getPort());
        System.out.println("=============");
    }

    private void close(SelectionKey key) throws IOException {
        System.out.println("Close " + ((SocketChannel) key.channel()).socket().getInputStream() + ":" + ((SocketChannel) key.channel()).socket().getPort());
        key.cancel();
        key.channel().close();
        SelectionKey peerKey = ((Attachment) key.attachment()).peer;
        if (peerKey != null) {
            ((Attachment) peerKey.attachment()).peer = null;
            if ((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                ((Attachment) peerKey.attachment()).out.flip();
            }
            peerKey.interestOps(SelectionKey.OP_WRITE);
            ((Attachment) peerKey.attachment()).close = true;
        }
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Wrong amount of arguments");
            return;
        }
        int port = 1080;
        if (args.length == 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                System.err.println("Port number expected");
                return;
            }
        }
        Socks5ProxyServer server = new Socks5ProxyServer();
        server.host = "127.0.0.1";
        server.port = port;
        server.run();
    }
}
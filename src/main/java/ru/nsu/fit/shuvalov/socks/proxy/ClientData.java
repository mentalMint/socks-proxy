package ru.nsu.fit.shuvalov.socks.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static ru.nsu.fit.shuvalov.socks.proxy.ClientData.ClientState.HANDSHAKE;

public class ClientData {
    private int version = -1;
    private int command = -1;
    private short destinationPort = -1;
    private String destinationIp = null;
    private String id = null;
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    public ClientState state = HANDSHAKE;
    public byte[] connectionRequest = {};
    private boolean parsed = false;
    private ByteBuffer toSend = null;

    public enum ClientState {
        HANDSHAKE,
        CONNECTION_REQUEST_RECEIVED
    }

    public ByteBuffer getToSend() {
        return toSend;
    }

    public boolean isParsed() {
        return parsed;
    }

    private void parse() throws IOException {
        if (connectionRequest.length >= 1 && version == -1) {
            version = Byte.toUnsignedInt(connectionRequest[0]);
        }
        if (connectionRequest.length >= 2 && command == -1) {
            command = Byte.toUnsignedInt(connectionRequest[1]);
        }
        if (connectionRequest.length >= 4 && destinationPort == -1) {
            destinationPort = ByteBuffer.wrap(Arrays.copyOfRange(connectionRequest, 2, 4)).getShort();
        }
        if (connectionRequest.length >= 8 && destinationIp == null) {
            InetAddress addr = InetAddress.getByAddress(Arrays.copyOfRange(connectionRequest, 4, 8));
            destinationIp = addr.getCanonicalHostName();
        }
        if (version != -1 && command != -1 && destinationPort != -1 && destinationIp != null) {
            parsed = true;
            byte[] toSend = ByteUtilities.concatenate(
                    ByteUtilities.concatenate(
                            new byte[]{0x00, 0x5A},
                            ByteUtilities.shortToByteArray(getDestinationPort())),
                    getDestinationIp().getBytes()
            );
            this.toSend = ByteBuffer.wrap(toSend);
        }
    }

    public void addToConnectionRequest(byte[] bytes) throws IOException {
        connectionRequest = ByteUtilities.concatenate(connectionRequest, bytes);
        parse();
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setCommand(int command) {
        this.command = command;
    }

    public void setDestinationPort(short destinationPort) {
        this.destinationPort = destinationPort;
    }

    public void setDestinationIp(String destinationIp) {
        this.destinationIp = destinationIp;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public int getCommand() {
        return command;
    }

    public short getDestinationPort() {
        return destinationPort;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public String getId() {
        return id;
    }
}

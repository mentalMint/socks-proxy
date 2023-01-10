package ru.nsu.fit.shuvalov.socks.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ByteUtilities {
    static public byte[] concatenate(byte[] a, byte[] b) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(a);
        outputStream.write(b);
        return outputStream.toByteArray();
    }

    static public byte[] shortToByteArray(short value) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.putShort(value);
        return buffer.array();
    }
}

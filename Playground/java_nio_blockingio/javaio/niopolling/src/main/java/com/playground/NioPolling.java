package com.playground;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NioPolling {

    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(8080));

        serverSocket.configureBlocking(false);

        Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>();

        while (true) {
            System.out.println("polling...");
            SocketChannel socket = serverSocket.accept();

            // 소켓 연결시 Non-Blocking 처리 및 Socket 별로 하나의 ByteBuffer 를 할당
            if (socket != null) {
                socket.configureBlocking(false);
                sockets.put(socket, ByteBuffer.allocate(80));
            }

            sockets.keySet().removeIf(socketCh -> !socketCh.isOpen());

            sockets.forEach((socketCh, byteBuffer) -> {
                try {
                    int data = socketCh.read(byteBuffer);

                    if (data == -1) {
                        closeSocket(socketCh);
                    } else if (data != 0) {
                        // 데이터가 들어온 경우 position 을 0 으로 한 뒤 읽기 및 대문자로 변환
                        byteBuffer.flip();

                        requestToUppercase(byteBuffer);

                        while (byteBuffer.hasRemaining()) {
                            socketCh.write(byteBuffer);
                        }

                        byteBuffer.compact();
                    }
                } catch (IOException e) {
                    closeSocket(socketCh);
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void closeSocket(SocketChannel socketChannel) {
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void requestToUppercase(final ByteBuffer byteBuffer) {
        for (int i = 0; i < byteBuffer.limit(); i++) {
            byteBuffer.put(i, (byte) toUppercase(byteBuffer.get(i)));
        }
    }

    private static int toUppercase(int data) {
        return Character.isLetter(data) ? Character.toUpperCase(data) : data;
    }
}

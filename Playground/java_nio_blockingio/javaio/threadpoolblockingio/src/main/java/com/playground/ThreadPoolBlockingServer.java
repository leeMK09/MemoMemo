package com.playground;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolBlockingServer {

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        while (true) {
            Socket socket = serverSocket.accept();

            executorService.submit(() -> requestToUppercase(socket));
        }
    }

    private static void requestToUppercase(Socket socket) {
        try (
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream()
        ) {
            int data;

            while ((data = in.read()) != -1) {
                data = Character.isLetter(data) ? Character.toUpperCase(data) : data;
                out.write(data);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

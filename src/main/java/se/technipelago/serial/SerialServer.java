/*
 *  Copyright 2006 Goran Ehrsson.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package se.technipelago.serial;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides access to a local serial port over the network.
 *
 * @author Goran Ehrsson
 */
public class SerialServer {

    private static final Logger log = Logger.getLogger(SerialServer.class.getName());
    private static final String PROPERTIES_FILE = "serialserver.properties";
    private static boolean keepReading = true;
    private String device;
    private int timeout;
    private int port;

    private static Properties getProperties() {
        final Properties prop = new Properties();
        InputStream fis = null;
        try {
            File file = new File(PROPERTIES_FILE);
            if (file.exists()) {
                fis = new FileInputStream(file);
                prop.load(fis);
            } else {
                log.log(Level.WARNING, PROPERTIES_FILE + " not found, using default values.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignore) {
                }
            }
        }
        return prop;
    }

    public static void main(String[] args) {
        final Properties prop = getProperties();
        String device = args.length > 0 ? args[0] : prop.getProperty("serialserver.device", "/dev/ttyS0");
        int timeout = args.length > 1 ? Integer.parseInt(args[1]) : Integer.parseInt(prop.getProperty("serialserver.timeout", "5000"));
        int port = args.length > 2 ? Integer.parseInt(args[2]) : Integer.parseInt(prop.getProperty("serialserver.port", "8232"));
        SerialServer server = new SerialServer(device, timeout, port);
        try {
            server.start();
        } catch (IOException ex) {
            log.log(Level.SEVERE, null, ex);
        }
    }

    public SerialServer(final String device, final int timeout, final int port) {
        this.device = device;
        this.timeout = timeout;
        this.port = port;
    }

    /**
     * Close down the emulator.
     */
    public static void shutdown() {
        keepReading = false;
    }

    private void start() throws IOException {
        ServerSocket socket = new ServerSocket(port);
        socket.setSoTimeout(10000);

        log.fine("Serial Server listening on port " + port);

        while (keepReading) {
            Socket connection = null;
            try {
                connection = socket.accept();
                if (keepReading) {
                    log.fine("Accepted connection from " + connection.getInetAddress());
                    startClient(connection);
                } else {
                    connection.getOutputStream().write("Service unavailable".getBytes());
                }
            } catch (SocketTimeoutException e) {
                // Ignore.
            }
        }

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            // Ignore.
        }

        socket.close();
    }

    public void startClient(Socket connection) throws IOException {
        new Thread(new Receiver(connection, device, timeout)).start();
    }
}


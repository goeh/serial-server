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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Enumeration;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * Read serial data from a RS-232 port and transmits data to a network connected client.
 *
 * @author Goran Ehrsson
 */
public class Receiver implements Runnable, SerialPortEventListener {

    private static final Logger log = Logger.getLogger(Receiver.class.getName());
    private static final int BUF_LENGTH = 1024;
    private final byte[] inputBuffer = new byte[BUF_LENGTH];
    private final Socket connection;
    private final String device;
    private final int timeout;
    private InputStream serialInputStream;
    private OutputStream serialOutputStream;

    public Receiver(Socket connection, String device, int timeout) {
        this.connection = connection;
        this.device = device;
        this.timeout = timeout;
    }

    public void run() {
        final CommPortIdentifier portId = getPort(device);

        if (portId == null) {
            log.log(Level.SEVERE, "Port {0} not found", device);
            return;
        }

        log.log(Level.FINE, "Found port {0}", portId.getName());

        /*
         * Open the serial port.
         */
        SerialPort serialPort;
        try {
            serialPort = (SerialPort) portId.open("Modem", timeout);
        } catch (PortInUseException e) {
            log.log(Level.SEVERE, "Port " + portId.getName() + " in use", e);
            return;
        }

        /*
         * Get input stream.
         */
        try {
            serialInputStream = serialPort.getInputStream();
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to get inputstream", e);
            return;
        }

        /*
         * Get output stream.
         */
        try {
            serialOutputStream = serialPort.getOutputStream();
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to get outputstream", e);
            try {
                serialInputStream.close();
            } catch (IOException ioe) {
            }
            return;
        }

        try {
            serialPort.addEventListener(this);
        } catch (TooManyListenersException e) {
            log.log(Level.SEVERE, "Failed to add event listener", e);
            try {
                serialInputStream.close();
            } catch (IOException ioe) {
            }
            return;
        }

        serialPort.notifyOnDataAvailable(true);

        try {
            serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT);
        } catch (UnsupportedCommOperationException e) {
            log.log(Level.WARNING, "Error setting serial port params", e);
            try {
                serialOutputStream.close();
            } catch (IOException ioe) {
            }
            try {
                serialInputStream.close();
            } catch (IOException ioe) {
            }
            return;
        } catch (Exception e) {
            log.warning(e.getMessage());
        }

        try {
            serialPort.notifyOnOutputEmpty(true);
        } catch (Exception e) {
            log.log(Level.WARNING, "Error setting event notification", e);
            try {
                serialOutputStream.close();
            } catch (IOException ioe) {
            }
            try {
                serialInputStream.close();
            } catch (IOException ioe) {
            }
            return;
        }

        mainloop();

        try {
            serialOutputStream.close();
        } catch (IOException e) {
            log.log(Level.WARNING, "Error closing outputstream", e);
        }
        try {
            serialInputStream.close();
        } catch (IOException e) {
            log.log(Level.WARNING, "Error closing inputstream", e);
        }
        serialPort.close();
        log.log(Level.FINE, "Serial port {0} closed", device);
    }

    @SuppressWarnings("unchecked")
    private CommPortIdentifier getPort(final String portName) {
        Enumeration<CommPortIdentifier> portList = CommPortIdentifier.getPortIdentifiers();
        CommPortIdentifier portId = null;
        boolean portFound = false;

        while (portFound == false && portList.hasMoreElements()) {
            portId = portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL && portId.getName().equals(portName)) {
                portFound = true;
            }
        }

        return portId;
    }

    private static final byte[] QUIT = "quit".getBytes();//new byte[]{'q', 'u', 'i', 't'};
    private static final byte[] KILL = "kill".getBytes(); //new byte[]{'k', 'i', 'l', 'l'};

    private void mainloop() {
        try {
            boolean keepGoing = true;
            while (keepGoing) {
                byte[] line = readLine(connection.getInputStream());
                if (line == null) {
                    keepGoing = false;
                } else if (line.length > 0) {
                    if (compareBytes(line, QUIT)) {
                        keepGoing = false;
                    } else if (compareBytes(line, KILL)) {
                        SerialServer.shutdown();
                        keepGoing = false;
                    } else {
                        serialOutputStream.write(line);
                    }
                }
            }
        } catch (IOException ex) {
            log.log(Level.SEVERE, null, ex);
        } finally {
            try {
                connection.close();
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            }
        }
    }

    private byte[] readLine(InputStream is) throws IOException {
        final byte[] readBuffer = new byte[280];
        int index = 0;
        while (is.available() > 0) {
            int n = is.read(readBuffer);
            if (n != -1) {
                System.arraycopy(readBuffer, 0, inputBuffer, index, n);
                index += n;
            }
        }
        byte[] input = new byte[index];
        if (index > 0) {
            System.arraycopy(inputBuffer, 0, input, 0, index);
        }

        return input;
    }

    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {

            case SerialPortEvent.BI:

            case SerialPortEvent.OE:

            case SerialPortEvent.FE:

            case SerialPortEvent.PE:

            case SerialPortEvent.CD:

            case SerialPortEvent.CTS:

            case SerialPortEvent.DSR:

            case SerialPortEvent.RI:

            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                log.fine("Output buffer empty");
                break;

            case SerialPortEvent.DATA_AVAILABLE:
                log.fine("Serial data available");
                try {
                    byte[] readBuffer = new byte[320];
                    int index = 0;
                    while (serialInputStream.available() > 0) {
                        int n = serialInputStream.read(readBuffer);
                        if (n != -1) {
                            System.arraycopy(readBuffer, 0, inputBuffer, index, n);
                            index += n;
                        }
                        try {
                            Thread.sleep(50);
                        } catch (Exception e) {
                            // Ignore.
                        }
                    }
                    try {
                        connection.getOutputStream().write(inputBuffer, 0, index);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Error writing output", e);
                    }
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Wrote {0} bytes", index);
                        System.out.println(escape(inputBuffer, 0, index, true));
                    }
                } catch (IOException e) {
                    log.log(Level.SEVERE, null, e);
                }
                break;
        }
    }

    protected String escape(byte[] bytes, int offset, int length, boolean printWritable) {
        if (offset > length) {
            throw new IllegalArgumentException("offset " + offset + " is greater than length " + length);
        }
        final StringBuilder buf = new StringBuilder();
        for (int i = offset; i < (offset + length); i++) {
            switch (bytes[i]) {
                case '\n':
                    buf.append(printWritable ? "\\n" : "<0x0a>");
                    break;
                case '\r':
                    buf.append(printWritable ? "\\r" : "<0x0d>");
                    break;
                case '\t':
                    buf.append(printWritable ? "\\t" : "<0x09>");
                    break;
                case 0x06:
                    buf.append(printWritable ? "<ACK>" : "<0x06>");
                    break;
                case 0x18:
                    buf.append(printWritable ? "<CAN>" : "<0x18>");
                    break;
                case 0x21:
                    buf.append(printWritable ? "<NAK>" : "<0x21>");
                    break;
                default:
                    if (bytes[i] < 0x20 || bytes[i] > 0x7e || !printWritable) {
                        String s = Integer.toHexString((int) bytes[i] & 0x000000ff);
                        buf.append("<0x");
                        if (s.length() == 1) {
                            buf.append('0');
                        }
                        buf.append(s);
                        buf.append('>');
                    } else {
                        buf.append((char) bytes[i]);
                    }
                    break;
            }
        }
        return buf.toString();
    }

    /**
     * Compare two byte arrays.
     *
     * @param input   the bytes to compare.
     * @param compare the reference byte array.
     * @return true if <code>input</code> starts with the same bytes as <code>compare</code>.
     */
    private boolean compareBytes(final byte[] input, final byte[] compare) {
        if (input.length < compare.length) {
            return false;
        }
        for (int i = 0; i < compare.length; i++) {
            if (compare[i] != input[i]) {
                return false;
            }
        }
        return true;
    }
}

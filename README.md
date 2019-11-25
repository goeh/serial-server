# Serial Communications Network Proxy

This little RS-232 <-> TCP/IP proxy written in Java was developed by me to be able to pull data from a weater station (Davis Vantage Pro2) connected to the serial (RS-232) port on a Ubuntu Linux server. The service has been running 24x7x365 since 2008 and is still running, collecting data every 10 minutes.

## Build

    ./gradlew
    
## Prerequisites for running serialserver on Ubuntu

### librxtxSerial.so from RXTX lib is required.

    sudo apt-get install librxtx-java

## Device permissions.

### Make sure the user has rw permissions on the device.

    $ ls -l /dev/ttyS0
    crw-rw---- 1 root dialout 4, 64 2011-03-05 19:40 /dev/ttyS0

    sudo adduser <username> dialout

## Serial Server start script (serialserver.sh)


    #!/bin/bash
    #
    SERIAL_HOME=$HOME
    SERIAL_PORT=/dev/ttyS0
    SERIAL_BAUD=9600
    SERIAL_TCP=8888

    cd $SERIAL_HOME

    stty -F $SERIAL_PORT $SERIAL_BAUD sane clocal crtscts -hupcl -icrnl -opost -onlcr

    java -classpath $SERIAL_HOME/serialserver-1.1.jar:$SERIAL_HOME/RXTXcomm.jar se.technipelago.serial.SerialServer $SERIAL_PORT 2000 $SERIAL_TCP

## Start the serial server

    serialserver.sh

## Test communicating with the server

    telnet localhost 8888
    at
    OK
    kill
    Connection closed by foreign host.

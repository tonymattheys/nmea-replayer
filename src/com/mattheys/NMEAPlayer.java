/*
 * 
 */
package com.mattheys;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NMEAPlayer thread reads NMEA0183 sentences from the file and broadcasts
 * onto the network via UDP port 11111. Based on the length of the sentence that
 * we send, a delay is introduced to limit the baud rate to 4,800 bps for basic
 * GPS sentences and 38,400 for AIS sentences. This is not quite perfect because
 * we make the decision based on the first character of the sentence. A "$" is
 * taken to be a normal GPS sentence and a "!" is taken to be an AIS sentence.
 * There are some AIS units that send out proprietary $whatever sentences at
 * 38,400 baud but we will send them at 4,800 (meh).
 *
 */
public class NMEAPlayer extends Thread {

    static Boolean playing = false;
    static Boolean running = false;
    static Boolean sprayandpray = false;
    RandomAccessFile randomAccessFile;
    static int PORT = 11111 ;
    
    private static final Logger logger = LogManager.getLogger(NMEAPlayer.class);

    public void NMEAPLayer() {
        // Nothing to see here. 
    }

    /**
     *
     * @param rAF - the random access file containing the NMEA data stream
     *
     * Simply notes where the file is located and that's it.
     */
    public void setRandomAccessFile(RandomAccessFile rAF) {
        logger.debug("Setting up randomAccessFile = " + rAF);
        randomAccessFile = rAF;
        logger.debug("And we are done, randomAccessFile = " + randomAccessFile);
    }

    /**
     * Lets you know if this thread is just sending NMEA data onto the network via
     * the UDP socket. Returns true if data is being sent and false if not.
     *
     * @return Boolean playing - whether or not we are sending NMEA data via UDP
     */
    public Boolean isPlaying() {
        logger.debug("Are we playing? " + playing);
        return playing;
    }

    /**
     *
     * @param s - new value for "playing" Boolean
     */
    public void setPlaying(Boolean s) {
        logger.debug("Setting to playing = " + s);
        playing = s;
    }

    /**
     *
     * @param s - new value for "sprayandpray" Boolean
     */
    public void setSprayAndPray(Boolean s) {
        logger.debug("Setting sprayandpray to " + s);
        sprayandpray = s;
    }

    /**
     * Called when it is time to stop this thread.
     */
    public void killThread() {
        logger.debug("received the KILL signal. Time to leave");
        running = false;
    }

    /**
     *
     * @param gpslocation
     * @param nsew
     * @return decimal GPS location (double)
     *
     * Locations in GPS data streams follow a format like DDDMM.dddd where
     * DDD is the number of degrees of lat or long
     * MM is the number of minutes
     * and ddddd is the decimal portion of the minutes part
     *
     * So to convert to double we need to strip apart the field and then reassemble
     * it into a decimal number. That is what gets returned by the method.
     *
     */
    public double GPStoDecimal(String gpslocation, String nsew) {
        double num;
        long integerPart;
        double fractionalPart;
        double answer;

        logger.debug("Input = " + gpslocation + " " + nsew);
        num = Double.parseDouble(gpslocation);
        integerPart = (long) num;
        fractionalPart = num - integerPart;
        answer = (long) (integerPart / 100l) + ((Math.abs(integerPart - ((long) (integerPart / 100l) * 100)) + Math.abs(fractionalPart)) / 60.0d);
        logger.debug("Answer = " + answer + " " + nsew);
        if (nsew.equalsIgnoreCase("N") || nsew.equalsIgnoreCase("E")) {
            return answer;
        } else {
            return (-1.0d * answer);
        }
    }

    /**
     *
     * The interesting part of this particular class. We basically set up the UDP
     * socket and then enter a loop waiting to play NMEA records onto the network
     * according to the setting of the "playing" Boolean. The class is invoked as
     * its own thread and continues to run until signaled to stop
     *
     * When it's time to stop, some other program will call the "killThread" method
     * which sets another Boolean called "running". When the loop detects that
     * the "running" Boolean is false, it cleans everything up nicely and exits.
     *
     */
    @Override
    public void run() {
        logger.debug("Entering the run() method now");
        running = true;
        InetAddress ipAddress = null;
        DatagramSocket dgramSocket = null;
        try {
            dgramSocket = new DatagramSocket();
            dgramSocket.setBroadcast(true);
            ipAddress = InetAddress.getByName("255.255.255.255");
        } catch (SocketException | UnknownHostException ex) {
            logger.fatal(ex);
            running = false;
        }
        //logger.debug("dgramSocket = " + dgramSocket.getLocalSocketAddress());
        while (running) {
            if (playing) {
                try {
                    String fileLine = randomAccessFile.readLine();
                    if (randomAccessFile.getFilePointer() >= randomAccessFile.length()) {
                        playing = false;
                    }
                    logger.debug("Just read this ==> \"" + fileLine + "\"");
                    byte[] sendData = fileLine.concat("\r\n").getBytes();
                    DatagramPacket udpPacket = new DatagramPacket(sendData, sendData.length, ipAddress, PORT);
                    logger.debug("Sending (" + udpPacket.getLength() + " bytes) Datagram Packet.");
                    dgramSocket.send(udpPacket);
                    logger.debug("SENT...");
                    if (fileLine.startsWith("$")) { // GPS Sentence
                        long delay = (long) (fileLine.length() * 1000) / (long) 480; // delay in milliseconds at 480 characters/second
                        try {
                            if (!sprayandpray) {
                                Thread.sleep(delay);
                            } else {
                                Thread.sleep(10);
                            }
                        } catch (InterruptedException ex) {
                            logger.fatal(ex);
                        }
                        NMEALogReplayerController.updateTimeAndPosition(fileLine);
                    } else { // AIS sentence, presumably
                        long delay = (long) (fileLine.length() * 1000) / (long) 3840; // delay in milliseconds at 3840 characters/second
                        try {
                            if (!sprayandpray) {
                                Thread.sleep(delay);
                            } else {
                                Thread.sleep(10);
                            }
                        } catch (InterruptedException ex) {
                            logger.fatal(ex);
                        }
                    }
                } catch (IOException ex) {
                    logger.fatal(ex);
                    playing = false;
                    running = false;
                }
            } else {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    logger.debug(ex);
                }
            }
        }
        logger.debug("It's all over. Dropped out of while() loop for RUNNING....");
    }
}

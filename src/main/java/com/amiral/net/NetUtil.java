package com.amiral.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/** Network helpers. */
public final class NetUtil {

    public static final String LOOPBACK = "127.0.0.1";

    private NetUtil() {
    }

    /** First IPv4 address of an active, non-loopback, non-virtual interface; 127.0.0.1 if none. */
    public static String localIPv4() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                String name = (ni.getDisplayName() + " " + ni.getName()).toLowerCase();
                if (name.contains("virtual") || name.contains("vmware") || name.contains("vbox")
                        || name.contains("hyper-v") || name.contains("docker") || name.contains("wsl")) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            return LOOPBACK;
        }
        return LOOPBACK;
    }
}

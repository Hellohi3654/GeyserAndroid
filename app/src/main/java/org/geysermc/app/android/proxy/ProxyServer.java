/*
 * Copyright (c) 2020-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/GeyserAndroid
 */

package org.geysermc.app.android.proxy;

import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.BedrockPong;
import com.nukkitx.protocol.bedrock.BedrockServer;
import com.nukkitx.protocol.bedrock.BedrockServerEventHandler;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.v408.Bedrock_v408;
import com.nukkitx.protocol.bedrock.v419.Bedrock_v419;

import org.geysermc.app.android.utils.EventListeners;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lombok.Getter;

public class ProxyServer {

    public static final BedrockPacketCodec CODEC = Bedrock_v419.V419_CODEC;

    private Timer timer;
    private BedrockServer bdServer;
    private BedrockPong bdPong;

    @Getter
    private boolean shuttingDown = false;

    @Getter
    private static ProxyServer instance;

    @Getter
    private ProxyLogger proxyLogger;

    @Getter
    private ScheduledExecutorService generalThreadPool;

    @Getter
    private final Map<String, Player> players = new HashMap<>();

    @Getter
    private String address;

    @Getter
    private int port;

    @Getter
    private static List<EventListeners.OnDisableEventListener> onDisableListeners = new ArrayList();

    public ProxyServer(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public void onEnable() {
        this.instance = this;

        proxyLogger = new ProxyLogger();

        this.generalThreadPool = Executors.newScheduledThreadPool(32);

        // Start a timer to keep the thread running
        timer = new Timer();
        TimerTask task = new TimerTask() { public void run() { } };
        timer.scheduleAtFixedRate(task, 0L, 1000L);

        // Initialise the palettes
        PaletteManger.init();

        start(19132);
    }

    public void onDisable() {
        this.shutdown();

        for (EventListeners.OnDisableEventListener onDisableListener : onDisableListeners) {
            if (onDisableListener != null) onDisableListener.onDisable();
        }
    }

    private void start(int port) {
        proxyLogger.info("Starting...");

        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", port);
        bdServer = new BedrockServer(bindAddress);

        bdPong = new BedrockPong();
        bdPong.setEdition("MCPE");
        bdPong.setMotd("LAN Proxy");
        bdPong.setPlayerCount(0);
        bdPong.setMaximumPlayerCount(1337);
        bdPong.setGameType("Survival");
        bdPong.setIpv4Port(port);
        bdPong.setProtocolVersion(ProxyServer.CODEC.getProtocolVersion());
        bdPong.setVersion(null); // Server tries to connect either way and it looks better

        bdServer.setHandler(new BedrockServerEventHandler() {
            @Override
            public boolean onConnectionRequest(InetSocketAddress address) {
                return true; // Connection will be accepted
            }

            @Override
            public BedrockPong onQuery(InetSocketAddress address) {
                return bdPong;
            }

            @Override
            public void onSessionCreation(BedrockServerSession session) {
                session.setPacketHandler(new PacketHandler(session, instance));
            }
        });

        // Start server up
        bdServer.bind().join();
        proxyLogger.info("Server started on 0.0.0.0:" + port);
    }

    public void shutdown() {
        proxyLogger.info("Shutting down!");
        shuttingDown = true;

        bdServer.close();
        generalThreadPool.shutdown();
        this.instance = null;
        proxyLogger.info("Successfully shutdown!");
    }
}

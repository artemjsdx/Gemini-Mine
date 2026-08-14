package com.artemjsdx.geminimine.forensics;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoopbackReportServer {
    private static final String TAG = "LoopbackReportServer";
    public static final int PREFERRED_PORT = 47321;
    public static final int[] FALLBACK_PORTS = {47321, 47322, 47323, 47324};

    private ServerSocket mServerSocket;
    private int mBoundPort = -1;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    private volatile boolean mReportReady = false;
    private volatile String mReportText = "REPORT_NOT_READY";
    private volatile File mGameLogFile = null;
    private volatile long mLaunchTimeMs = 0;
    private volatile String mClassification = "UNKNOWN";

    public synchronized boolean start(long launchTimeMs, File gameLogFile) {
        if (mRunning.get()) {
            return true;
        }
        mLaunchTimeMs = launchTimeMs;
        mGameLogFile = gameLogFile;

        for (int port : FALLBACK_PORTS) {
            try {
                InetAddress loopback = InetAddress.getByName("127.0.0.1");
                mServerSocket = new ServerSocket(port, 10, loopback);
                mBoundPort = port;
                mRunning.set(true);
                Log.i(TAG, "Loopback report server bound successfully to 127.0.0.1:" + port);
                break;
            } catch (Exception e) {
                Log.w(TAG, "Could not bind port " + port + ": " + e.getMessage());
            }
        }

        if (mBoundPort == -1 || mServerSocket == null) {
            Log.e(TAG, "Failed to bind loopback server to any designated port in range");
            return false;
        }

        mExecutor.execute(this::acceptLoop);
        return true;
    }

    public int getBoundPort() {
        return mBoundPort;
    }

    public boolean isReportReady() {
        return mReportReady;
    }

    public synchronized void updateReport(String reportText, String classification) {
        mReportText = reportText;
        mClassification = classification != null ? classification : "UNKNOWN";
        mReportReady = true;
    }

    private void acceptLoop() {
        while (mRunning.get() && mServerSocket != null && !mServerSocket.isClosed()) {
            try {
                Socket client = mServerSocket.accept();
                client.setSoTimeout(5000);
                mExecutor.execute(() -> handleClient(client));
            } catch (Exception e) {
                if (mRunning.get()) {
                    Log.w(TAG, "Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket s = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream out = s.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }

            String method = parts[0];
            String path = parts[1];

            // Consume rest of headers (up to limit)
            String header;
            int headerLines = 0;
            while ((header = reader.readLine()) != null && !header.isEmpty() && headerLines < 50) {
                headerLines++;
            }

            if (!"GET".equalsIgnoreCase(method)) {
                sendResponse(out, 405, "Method Not Allowed", "text/plain", "Method Not Allowed\n".getBytes(StandardCharsets.UTF_8));
                return;
            }

            if ("/health".equals(path)) {
                sendResponse(out, 200, "OK", "text/plain", "OK\n".getBytes(StandardCharsets.UTF_8));
            } else if ("/status".equals(path)) {
                String json = String.format(
                        "{\"reportReady\": %b, \"port\": %d, \"launchTimeMs\": %d, \"classification\": \"%s\"}\n",
                        mReportReady, mBoundPort, mLaunchTimeMs, mClassification.replace("\"", "\\\"")
                );
                sendResponse(out, 200, "OK", "application/json", json.getBytes(StandardCharsets.UTF_8));
            } else if ("/report".equals(path)) {
                if (!mReportReady) {
                    sendResponse(out, 202, "Accepted", "text/plain", "REPORT_NOT_READY\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    sendResponse(out, 200, "OK", "text/plain; charset=utf-8", mReportText.getBytes(StandardCharsets.UTF_8));
                }
            } else if ("/game-log".equals(path)) {
                if (mGameLogFile != null && mGameLogFile.exists() && mGameLogFile.length() > 0) {
                    byte[] logBytes = new byte[(int) mGameLogFile.length()];
                    try (FileInputStream fis = new FileInputStream(mGameLogFile)) {
                        int read = fis.read(logBytes);
                        sendResponse(out, 200, "OK", "text/plain; charset=utf-8", logBytes);
                    }
                } else {
                    sendResponse(out, 404, "Not Found", "text/plain", "NO_GAME_LOG\n".getBytes(StandardCharsets.UTF_8));
                }
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Not Found\n".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error handling client request: " + e.getMessage());
        }
    }

    private void sendResponse(OutputStream out, int statusCode, String statusText, String contentType, byte[] body) throws Exception {
        String headers = String.format(
                "HTTP/1.1 %d %s\r\n" +
                "Content-Type: %s\r\n" +
                "Content-Length: %d\r\n" +
                "Connection: close\r\n\r\n",
                statusCode, statusText, contentType, body.length
        );
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    public synchronized void stop() {
        mRunning.set(false);
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (Exception ignored) {
            }
        }
        mExecutor.shutdown();
    }
}

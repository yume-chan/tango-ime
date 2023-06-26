package com.npes87184.socket.ime;

import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import static java.lang.Thread.interrupted;

public class SocketIME extends InputMethodService {

    private LocalSocket mLocalSocket = null;
    private OutputStream mOutputStream = null;
    private DataOutputStream mWriter = null;
    private Thread mThread = null;
    private static final String SOCKET_NAME = "socket-ime";

    @Override
    public void onCreate() {
        if (mThread != null && mThread.isAlive()) {
            mThread.interrupt();
        }
        mThread = new Thread(() -> {
            mLocalSocket = new LocalSocket();

            try {
                mLocalSocket.connect(new LocalSocketAddress(SOCKET_NAME), 1000);

                mOutputStream = mLocalSocket.getOutputStream();
                mWriter = new DataOutputStream(mOutputStream);

                var inputStream = mLocalSocket.getInputStream();


                var buffer = new byte[1024];
                while (true) {
                    var read = inputStream.read(buffer);
                    if (read < 0) {
                        throw new IOException("");
                    }

                    var string = new String(buffer, 0, read, StandardCharsets.UTF_8);
                    getCurrentInputConnection().commitText(string, 1);
                }
            } catch (Exception ignored) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    switchToNextInputMethod(false);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mThread != null && mThread.isAlive()) {
            mThread.interrupt();
            mThread = null;
        }

        try {
            if (mLocalSocket != null) {
                mLocalSocket.close();
                mLocalSocket = null;
            }
        } catch (Exception ignored) {
        }
    }

    private static final int MESSAGE_TYPE_START = 0;
    private static final int MESSAGE_TYPE_UPDATE_CURSOR = 1;
    private static final int MESSAGE_TYPE_STOP = 2;

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        try {
            if (mWriter != null) {
                mWriter.writeInt(MESSAGE_TYPE_START);
                mWriter.writeInt(info.inputType);

                CharSequence initialSelectedText = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    initialSelectedText = info.getInitialSelectedText(0);
                }
                if (initialSelectedText != null) {
                    var buffer = initialSelectedText.toString().getBytes(StandardCharsets.UTF_8);
                    mWriter.writeInt(buffer.length);
                    mWriter.write(buffer, 0, buffer.length);
                }

                getCurrentInputConnection().requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE | InputConnection.CURSOR_UPDATE_MONITOR);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
        try {
            if (mWriter != null) {
                mWriter.writeInt(MESSAGE_TYPE_UPDATE_CURSOR);
                var selectionStart = cursorAnchorInfo.getSelectionStart();
                var rect = cursorAnchorInfo.getCharacterBounds(selectionStart);
                mWriter.writeInt((int) rect.top);
                mWriter.writeInt((int) rect.bottom);
                mWriter.writeInt((int) rect.left);
                mWriter.writeInt((int) rect.right);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        try {
            if (mWriter != null) {
                mWriter.writeInt(MESSAGE_TYPE_STOP);
            }
        } catch (Exception ignored) {
        }
    }
}

package com.lrehmann.atoto.wifiadb;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Minimal client for the FYT main-module system-property bridge found on the
 * tested ATOTO S8. This is vendor-specific and intentionally has no fallback
 * to root, boot-image patching, or shell privilege escalation.
 */
final class FytAdbController implements ServiceConnection {
    interface Listener {
        void onRequestSent();
        void onError(String message);
    }

    private enum Request {
        TEMPORARY,
        PERSISTENT,
        DISABLE
    }

    private static final String TOOLKIT_PACKAGE = "com.syu.ms";
    private static final String TOOLKIT_SERVICE = "app.ToolkitService";
    private static final String TOOLKIT_DESCRIPTOR = "com.syu.ipc.IRemoteToolkit";
    private static final String MODULE_DESCRIPTOR = "com.syu.ipc.IRemoteModule";
    private static final int MAIN_MODULE = 0;
    private static final int SET_ANY_SYSTEM_PROPERTY = 161;
    private static final int ADB_PORT = 5555;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private IBinder mainModule;
    private Listener pendingListener;
    private Request pendingRequest;
    private boolean bound;

    FytAdbController(Context context) {
        this.context = context.getApplicationContext();
    }

    void enableTemporary(Listener listener) {
        request(Request.TEMPORARY, listener);
    }

    void enablePersistent(Listener listener) {
        request(Request.PERSISTENT, listener);
    }

    void disablePersistent(Listener listener) {
        request(Request.DISABLE, listener);
    }

    boolean isToolkitServicePresent() {
        Intent intent = toolkitIntent();
        return context.getPackageManager().resolveService(intent, 0) != null;
    }

    private void request(Request request, Listener listener) {
        if (pendingListener != null) {
            listener.onError("Another FYT request is already in progress.");
            return;
        }
        pendingListener = listener;
        pendingRequest = request;
        if (mainModule != null && mainModule.isBinderAlive()) {
            runPendingRequest();
            return;
        }

        try {
            bound = context.bindService(toolkitIntent(), this, Context.BIND_AUTO_CREATE);
            if (!bound) {
                fail("FYT ToolkitService did not bind. This firmware may be incompatible.");
            }
        } catch (Exception error) {
            fail("FYT ToolkitService bind failed: " + safeMessage(error));
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        try {
            mainModule = getRemoteModule(service, MAIN_MODULE);
            if (mainModule == null) {
                fail("The FYT main module was unavailable.");
                return;
            }
            runPendingRequest();
        } catch (Exception error) {
            fail("FYT module connection failed: " + safeMessage(error));
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mainModule = null;
        // The ServiceConnection remains registered and may reconnect. Keep
        // `bound` true so close() still performs the matching unbindService().
    }

    void close() {
        pendingListener = null;
        pendingRequest = null;
        if (bound) {
            try {
                context.unbindService(this);
            } catch (Exception ignored) {
                // The service may already have disconnected during shutdown.
            }
        }
        bound = false;
        mainModule = null;
    }

    private void runPendingRequest() {
        Request request = pendingRequest;
        if (request == null) {
            return;
        }
        try {
            switch (request) {
                case TEMPORARY:
                    setProperty("service.adb.tcp.port", Integer.toString(ADB_PORT));
                    break;
                case PERSISTENT:
                    setProperty("persist.adb.tcp.port", Integer.toString(ADB_PORT));
                    setProperty("service.adb.tcp.port", Integer.toString(ADB_PORT));
                    break;
                case DISABLE:
                    setProperty("persist.adb.tcp.port", "");
                    setProperty("service.adb.tcp.port", "-1");
                    break;
            }
            mainHandler.postDelayed(() -> {
                try {
                    setProperty("ctl.restart", "adbd");
                    succeed();
                } catch (Exception error) {
                    fail("adbd restart failed: " + safeMessage(error));
                }
            }, 400L);
        } catch (Exception error) {
            fail("Wi-Fi ADB property request failed: " + safeMessage(error));
        }
    }

    private Intent toolkitIntent() {
        Intent intent = new Intent();
        intent.setClassName(TOOLKIT_PACKAGE, TOOLKIT_SERVICE);
        return intent;
    }

    private void setProperty(String name, String value) throws RemoteException {
        IBinder module = mainModule;
        if (module == null || !module.isBinderAlive()) {
            throw new RemoteException("FYT main module disconnected");
        }

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE_DESCRIPTOR);
            data.writeInt(SET_ANY_SYSTEM_PROPERTY);
            data.writeIntArray(new int[]{0, 0});
            data.writeFloatArray(null);
            data.writeStringArray(new String[]{name, value});
            if (!module.transact(1, data, null, IBinder.FLAG_ONEWAY)) {
                throw new RemoteException("FYT property transaction was rejected");
            }
        } finally {
            data.recycle();
        }
    }

    private static IBinder getRemoteModule(IBinder toolkit, int module)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(TOOLKIT_DESCRIPTOR);
            data.writeInt(module);
            if (!toolkit.transact(1, data, reply, 0)) {
                throw new RemoteException("FYT toolkit transaction was rejected");
            }
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void succeed() {
        Listener listener = pendingListener;
        pendingListener = null;
        pendingRequest = null;
        if (listener != null) {
            listener.onRequestSent();
        }
    }

    private void fail(String message) {
        Listener listener = pendingListener;
        pendingListener = null;
        pendingRequest = null;
        if (listener != null) {
            listener.onError(message);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}

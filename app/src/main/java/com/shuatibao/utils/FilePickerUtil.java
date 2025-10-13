package com.shuatibao.utils;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;

public class FilePickerUtil {

    private static final int FILE_PICK_REQUEST_CODE = 1001;

    public static void pickDocument(AppCompatActivity activity) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-word",
                "application/vnd.ms-word.document.macroEnabled.12",
                "application/vnd.ms-word.template.macroEnabled.12",
                "application/pdf",
                "application/vnd.pdf"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            activity.startActivityForResult(Intent.createChooser(intent, "选择文档"), FILE_PICK_REQUEST_CODE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isFilePickResult(int requestCode) {
        return requestCode == FILE_PICK_REQUEST_CODE;
    }

    public static FilePickResult processFilePickResult(int resultCode, Intent data, Activity activity) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String fileName = getFileName(activity, uri);
                String fileType = getFileType(activity, uri, fileName); // 传入activity参数
                return new FilePickResult(uri, fileName, fileType, true, "");
            }
        }
        return new FilePickResult(null, "", "", false, "文件选择取消");
    }

    private static String getFileName(Activity activity, Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = activity.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                } else {
                    result = path;
                }
            }
        }
        return result != null ? result : "unknown";
    }

    // 在 FilePickerUtil 的 getFileType 方法中添加更严格的检测
    private static String getFileType(Activity activity, Uri uri, String fileName) {
        try {
            // 首先根据文件名后缀判断（最可靠）
            if (fileName != null) {
                String lowerFileName = fileName.toLowerCase();
                if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) {
                    Log.d("FilePickerUtil", "根据文件名判断为Word文档: " + fileName);
                    return "word";
                } else if (lowerFileName.endsWith(".pdf")) {
                    Log.d("FilePickerUtil", "根据文件名判断为PDF文档: " + fileName);
                    return "pdf";
                } else if (lowerFileName.endsWith(".txt")) {
                    Log.d("FilePickerUtil", "根据文件名判断为文本文件: " + fileName);
                    return "text";
                }
            }

            // 其次根据MIME类型判断
            try {
                String mimeType = activity.getContentResolver().getType(uri);
                Log.d("FilePickerUtil", "检测到MIME类型: " + mimeType);
                if (mimeType != null) {
                    if (mimeType.contains("word") || mimeType.contains("msword") ||
                            mimeType.contains("officedocument.wordprocessingml")) {
                        Log.d("FilePickerUtil", "根据MIME类型判断为Word文档: " + mimeType);
                        return "word";
                    } else if (mimeType.contains("pdf")) {
                        Log.d("FilePickerUtil", "根据MIME类型判断为PDF文档: " + mimeType);
                        return "pdf";
                    } else if (mimeType.contains("text/plain")) {
                        return "text";
                    }
                }
            } catch (Exception e) {
                Log.e("FilePickerUtil", "获取MIME类型失败: " + e.getMessage());
            }

            // 最后根据URL扩展名判断
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (extension != null) {
                String lowerExtension = extension.toLowerCase();
                Log.d("FilePickerUtil", "检测到URL扩展名: " + extension);
                if (lowerExtension.equals("doc") || lowerExtension.equals("docx")) {
                    return "word";
                } else if (lowerExtension.equals("pdf")) {
                    return "pdf";
                } else if (lowerExtension.equals("txt")) {
                    return "text";
                }
            }
        } catch (Exception e) {
            Log.e("FilePickerUtil", "文件类型检测失败: " + e.getMessage());
        }

        // 默认返回unknown
        Log.d("FilePickerUtil", "无法确定文件类型，返回unknown");
        return "unknown";
    }

    public static InputStream getInputStream(Activity activity, Uri uri) {
        try {
            return activity.getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class FilePickResult {
        public Uri fileUri;
        public String fileName;
        public String fileType;
        public boolean isSuccess;
        public String errorMessage;

        public FilePickResult(Uri fileUri, String fileName, String fileType, boolean isSuccess, String errorMessage) {
            this.fileUri = fileUri;
            this.fileName = fileName;
            this.fileType = fileType;
            this.isSuccess = isSuccess;
            this.errorMessage = errorMessage;
        }
    }
}
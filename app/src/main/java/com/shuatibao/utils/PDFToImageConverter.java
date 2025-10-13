package com.shuatibao.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFToImageConverter {
    private static final String TAG = "PDFToImageConverter";

    // OCR图片限制
    private static final int MAX_IMAGE_WIDTH = 2000;
    private static final int MAX_IMAGE_HEIGHT = 3000;
    private static final int TARGET_IMAGE_WIDTH = 1200;

    /**
     * 将PDF转换为图片列表
     */
    public static List<Bitmap> convertPDFToImages(Context context, Uri pdfUri, String fileName) {
        List<Bitmap> bitmaps = new ArrayList<>();
        PdfRenderer pdfRenderer = null;
        ParcelFileDescriptor fileDescriptor = null;

        try {
            // 获取文件描述符
            fileDescriptor = context.getContentResolver().openFileDescriptor(pdfUri, "r");
            if (fileDescriptor == null) {
                Log.e(TAG, "无法打开PDF文件描述符");
                return bitmaps;
            }

            // 创建PDF渲染器
            pdfRenderer = new PdfRenderer(fileDescriptor);

            int pageCount = pdfRenderer.getPageCount();
            Log.d(TAG, "PDF文档页数: " + pageCount + ", 文件名: " + fileName);

            // 限制最大页数，避免内存溢出
            int maxPages = Math.min(pageCount, 50); // 最多处理50页
            if (pageCount > maxPages) {
                Log.w(TAG, "PDF页数过多(" + pageCount + ")，只处理前" + maxPages + "页");
            }

            // 渲染每一页为图片
            for (int i = 0; i < maxPages; i++) {
                PdfRenderer.Page page = null;
                try {
                    page = pdfRenderer.openPage(i);

                    // 计算缩放比例以适应目标宽度
                    float scale = (float) TARGET_IMAGE_WIDTH / page.getWidth();
                    int width = (int) (page.getWidth() * scale);
                    int height = (int) (page.getHeight() * scale);

                    // 创建Bitmap
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawColor(Color.WHITE);

                    // 渲染PDF页面到Bitmap
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    // 压缩图片以适应OCR要求
                    Bitmap compressedBitmap = compressBitmapForOCR(bitmap);
                    bitmaps.add(compressedBitmap);

                    // 回收原bitmap
                    if (bitmap != compressedBitmap) {
                        bitmap.recycle();
                    }

                    Log.d(TAG, "PDF第 " + (i + 1) + " 页转换为图片，尺寸: " +
                            compressedBitmap.getWidth() + "x" + compressedBitmap.getHeight());

                } catch (Exception e) {
                    Log.e(TAG, "PDF第 " + (i + 1) + " 页转换失败", e);
                    // 创建错误提示图片
                    Bitmap errorBitmap = createErrorImage("第 " + (i + 1) + " 页解析失败");
                    bitmaps.add(errorBitmap);
                } finally {
                    // 确保页面被正确关闭
                    if (page != null) {
                        try {
                            page.close();
                        } catch (Exception e) {
                            Log.e(TAG, "关闭PDF页面失败", e);
                        }
                    }
                }
            }

            Log.d(TAG, "PDF转换完成，生成图片数量: " + bitmaps.size());

        } catch (Exception e) {
            Log.e(TAG, "PDF转图片失败: " + e.getMessage(), e);
            // 创建错误提示图片
            Bitmap errorBitmap = createErrorImage("PDF解析失败: " + e.getMessage());
            bitmaps.add(errorBitmap);
        } finally {
            // 清理资源 - 严格按照顺序关闭
            if (pdfRenderer != null) {
                try {
                    pdfRenderer.close();
                } catch (Exception e) {
                    Log.e(TAG, "关闭PDF渲染器失败", e);
                }
            }

            if (fileDescriptor != null) {
                try {
                    fileDescriptor.close();
                } catch (Exception e) {
                    Log.e(TAG, "关闭文件描述符失败", e);
                }
            }
        }

        return bitmaps;
    }

    /**
     * 安全地转换PDF（带资源管理）
     */
    public static List<Bitmap> convertPDFToImagesSafely(Context context, Uri pdfUri, String fileName) {
        List<Bitmap> bitmaps = new ArrayList<>();

        try {
            bitmaps = convertPDFToImages(context, pdfUri, fileName);
        } catch (Exception e) {
            Log.e(TAG, "PDF转换发生异常: " + e.getMessage(), e);
            // 即使发生异常，也返回已成功转换的图片
        }

        return bitmaps;
    }

    /**
     * 压缩图片以适应OCR要求
     */
    private static Bitmap compressBitmapForOCR(Bitmap original) {
        try {
            int width = original.getWidth();
            int height = original.getHeight();

            // 如果图片太大，进行缩放
            if (width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT) {
                float scale = Math.min(
                        (float) MAX_IMAGE_WIDTH / width,
                        (float) MAX_IMAGE_HEIGHT / height
                );

                int newWidth = (int) (width * scale);
                int newHeight = (int) (height * scale);

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
                Log.d(TAG, "PDF图片缩放: " + width + "x" + height + " -> " + newWidth + "x" + newHeight);
                return scaledBitmap;
            }

            return original;

        } catch (Exception e) {
            Log.e(TAG, "PDF图片压缩失败", e);
            return original;
        }
    }

    /**
     * 创建错误提示图片
     */
    private static Bitmap createErrorImage(String errorMessage) {
        int width = 800;
        int height = 400;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(20);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        // 绘制错误信息
        canvas.drawText("PDF解析错误", 50, 50, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(16);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        // 换行显示错误信息
        String[] lines = splitTextLines(errorMessage, width - 100, paint);
        int y = 100;
        for (String line : lines) {
            canvas.drawText(line, 50, y, paint);
            y += 30;
        }

        return bitmap;
    }

    private static String[] splitTextLines(String text, int maxWidth, Paint paint) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return new String[]{"未知错误"};
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float width = paint.measureText(testLine);

            if (width <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? word : " " + word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    /**
     * 清理Bitmap资源
     */
    public static void recycleBitmaps(List<Bitmap> bitmaps) {
        if (bitmaps == null) return;

        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        bitmaps.clear();
    }
}
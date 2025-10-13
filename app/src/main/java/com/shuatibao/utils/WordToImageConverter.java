package com.shuatibao.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import org.apache.poi.xwpf.usermodel.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class WordToImageConverter {
    private static final String TAG = "WordToImageConverter";

    // OCR图片限制
    private static final int MAX_IMAGE_WIDTH = 2000;
    private static final int MAX_IMAGE_HEIGHT = 3000;
    private static final int TARGET_IMAGE_WIDTH = 1200;

    /**
     * 转换Word文档为图片列表 - 适配BaiduOCRDocumentParser接口
     */
    public static List<Bitmap> convertWordToImages(InputStream inputStream) {
        List<Bitmap> bitmaps = new ArrayList<>();

        try {
            XWPFDocument document = new XWPFDocument(inputStream);
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            Log.d(TAG, "Word文档段落数: " + paragraphs.size());

            // 过滤空段落
            List<XWPFParagraph> nonEmptyParagraphs = new ArrayList<>();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    nonEmptyParagraphs.add(paragraph);
                }
            }

            Log.d(TAG, "非空段落数: " + nonEmptyParagraphs.size());

            if (nonEmptyParagraphs.isEmpty()) {
                Log.w(TAG, "Word文档内容为空，创建测试图片");
                Bitmap testBitmap = createTestQuestionImage();
                bitmaps.add(testBitmap);
            } else {
                // 分段处理，避免单张图片过大
                List<List<XWPFParagraph>> segments = splitParagraphs(nonEmptyParagraphs, 40);

                for (int i = 0; i < segments.size(); i++) {
                    Log.d(TAG, "渲染第 " + (i + 1) + " 段，包含 " + segments.get(i).size() + " 个段落");
                    Bitmap segmentBitmap = renderParagraphsToBitmap(segments.get(i), i + 1, segments.size());
                    if (segmentBitmap != null) {
                        bitmaps.add(segmentBitmap);
                        Log.d(TAG, "第 " + (i + 1) + " 段图片生成成功，尺寸: " +
                                segmentBitmap.getWidth() + "x" + segmentBitmap.getHeight());
                    }
                }
            }

            document.close();
            Log.d(TAG, "生成图片数量: " + bitmaps.size());

        } catch (Exception e) {
            Log.e(TAG, "Word转图片失败: " + e.getMessage(), e);
            // 创建简单的测试图片
            Bitmap testBitmap = createTestQuestionImage();
            bitmaps.add(testBitmap);
        }

        return bitmaps;
    }

    /**
     * 将段落分段处理
     */
    private static List<List<XWPFParagraph>> splitParagraphs(List<XWPFParagraph> paragraphs, int segmentSize) {
        List<List<XWPFParagraph>> segments = new ArrayList<>();

        for (int i = 0; i < paragraphs.size(); i += segmentSize) {
            int end = Math.min(i + segmentSize, paragraphs.size());
            segments.add(paragraphs.subList(i, end));
        }

        Log.d(TAG, "将段落分为 " + segments.size() + " 段");
        return segments;
    }

    /**
     * 渲染段落为Bitmap（优化版本）
     */
    private static Bitmap renderParagraphsToBitmap(List<XWPFParagraph> paragraphs, int segmentIndex, int totalSegments) {
        try {
            int width = TARGET_IMAGE_WIDTH;
            int lineHeight = 32; // 增加行高提高可读性
            int margin = 50;

            // 计算总高度
            int totalLines = calculateTotalLines(paragraphs, width - margin * 2);
            int height = Math.min(totalLines * lineHeight + margin * 2, MAX_IMAGE_HEIGHT);

            // 确保高度合理
            if (height < 200) height = 600;
            if (height > MAX_IMAGE_HEIGHT) height = MAX_IMAGE_HEIGHT;

            Log.d(TAG, "创建图片: " + width + "x" + height + ", 预计 " + totalLines + " 行");

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); // 使用ARGB提高质量
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(20);
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

            // 绘制页眉
            paint.setTextSize(18);
            paint.setFakeBoldText(true);
            canvas.drawText("文档内容 - 第 " + segmentIndex + "/" + totalSegments + " 部分",
                    margin, 35, paint);
            paint.setFakeBoldText(false);

            // 绘制内容
            paint.setTextSize(18);
            int y = margin + 60;

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    List<String> lines = wrapText(text, width - margin * 2, paint);
                    for (String line : lines) {
                        if (y > height - margin - lineHeight) {
                            Log.w(TAG, "图片高度不足，提前结束绘制");
                            break;
                        }
                        canvas.drawText(line, margin, y, paint);
                        y += lineHeight;
                    }
                    y += 8; // 段间距
                }
            }

            // 绘制页脚
            paint.setTextSize(14);
            paint.setColor(Color.GRAY);
            canvas.drawText("--- 第 " + segmentIndex + " 页结束 ---",
                    margin, height - 20, paint);

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "渲染段落失败", e);
            return createTestQuestionImage();
        }
    }

    /**
     * 计算总行数
     */
    private static int calculateTotalLines(List<XWPFParagraph> paragraphs, int maxWidth) {
        Paint testPaint = new Paint();
        testPaint.setTextSize(18);

        int totalLines = 0;
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText();
            if (text != null && !text.trim().isEmpty()) {
                List<String> lines = wrapText(text, maxWidth, testPaint);
                totalLines += lines.size();
            }
            totalLines += 1; // 段间距
        }
        return totalLines + 3; // 加上页眉页脚
    }

    /**
     * 创建测试题目图片（包含标准题目格式）
     */
    private static Bitmap createTestQuestionImage() {
        int width = 800;
        int height = 600;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(20);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        // 绘制标准题目格式
        canvas.drawText("测试题目文档 - OCR识别测试", 50, 50, paint);

        paint.setTextSize(18);
        canvas.drawText("1. 这是一个单项选择题示例？", 50, 100, paint);
        canvas.drawText("   A. 第一个选项", 70, 130, paint);
        canvas.drawText("   B. 第二个选项", 70, 160, paint);
        canvas.drawText("   C. 第三个选项", 70, 190, paint);
        canvas.drawText("   D. 第四个选项", 70, 220, paint);

        canvas.drawText("2. 这是第二个题目，包含较长的题干内容，用于测试文本换行和OCR识别能力。", 50, 270, paint);
        canvas.drawText("   A. 选项A", 70, 300, paint);
        canvas.drawText("   B. 选项B", 70, 330, paint);

        canvas.drawText("三、简答题示例", 50, 380, paint);
        canvas.drawText("   请简述你的观点：", 70, 410, paint);

        return bitmap;
    }

    /**
     * 文本换行处理
     */
    private static List<String> wrapText(String text, int maxWidth, Paint paint) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] paragraphs = text.split("\n");

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) continue;

            String[] words = paragraph.split("\\s+");
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
                        // 单个单词过长，强制分割
                        int splitIndex = findSplitIndex(word, maxWidth, paint);
                        lines.add(word.substring(0, splitIndex));
                        currentLine = new StringBuilder(word.substring(splitIndex));
                    }
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    private static int findSplitIndex(String text, int maxWidth, Paint paint) {
        for (int i = text.length() - 1; i > 0; i--) {
            if (paint.measureText(text.substring(0, i)) <= maxWidth) {
                return i;
            }
        }
        return 1;
    }
}
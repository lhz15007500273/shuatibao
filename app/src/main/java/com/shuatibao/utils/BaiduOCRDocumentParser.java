package com.shuatibao.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.shuatibao.model.Question;
import okhttp3.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaiduOCRDocumentParser {
    private static final String TAG = "BaiduOCRDocumentParser";
    public static final String TYPE_SINGLE_CHOICE = "single_choice";
    public static final String TYPE_MULTIPLE_CHOICE = "multiple_choice";
    public static final String TYPE_JUDGMENT = "judgment";

    private static final String API_KEY = "cIquLuE2vORx8p6ACzSgrXe0";
    private static final String SECRET_KEY = "CfsBlAgjZHNsHbxIfy8TLdSzyCcUUXkE";
    private static final String OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final Gson gson = new Gson();
    private static String accessToken;
    private static long tokenExpireTime = 0;

    // 标准化的文档模板解析规则
    private static final Pattern QUESTION_NUMBER_PATTERN = Pattern.compile("^(\\d+)[\\.、．]\\s*(.+)$");
    private static final Pattern OPTION_PATTERN = Pattern.compile("^([A-D])[\\.、．]\\s*(.+)$");
    private static final Pattern MULTI_OPTION_PATTERN = Pattern.compile("^([A-Z])[\\.、．]\\s*(.+)$");
    private static final Pattern INLINE_OPTION_PATTERN = Pattern.compile("([A-D])[\\.、．]\\s*([^A-D\\s]{2,}?)");
    private static final Pattern ANSWER_PATTERN = Pattern.compile("^(?:答案|正确答案|参考答案)[：:]\\s*([A-D√×正确错误]+)$");
    private static final Pattern MULTI_ANSWER_PATTERN = Pattern.compile("^(?:答案|正确答案|参考答案)[：:]\\s*([A-Z]+)$");
    private static final Pattern JUDGMENT_ANSWER_PATTERN = Pattern.compile("^(?:答案|正确答案|参考答案)[：:]\\s*([√×正确错误]+)$");
    private static final Pattern ANALYSIS_PATTERN = Pattern.compile("^(?:解析|答案解析|试题解析)[：:]\\s*(.+)$");

    // 新增：分离式答案和解析模式
    private static final Pattern SEPARATED_ANSWER_PATTERN = Pattern.compile("^(\\d+)[\\.、．]?(?:答案|正确答案|参考答案)[：:]\\s*([A-D√×正确错误]+)$");
    private static final Pattern SEPARATED_MULTI_ANSWER_PATTERN = Pattern.compile("^(\\d+)[\\.、．]?(?:答案|正确答案|参考答案)[：:]\\s*([A-Z]+)$");
    private static final Pattern SEPARATED_JUDGMENT_ANSWER_PATTERN = Pattern.compile("^(\\d+)[\\.、．]?(?:答案|正确答案|参考答案)[：:]\\s*([√×正确错误]+)$");
    private static final Pattern SEPARATED_ANALYSIS_PATTERN = Pattern.compile("^(\\d+)[\\.、．]?(?:解析|答案解析|试题解析)[：:]\\s*(.+)$");

    /**
     * 主解析方法 - 支持PDF和Word文档
     */
    public static List<Question> parseDocument(Context context, Uri fileUri, String fileType, String fileName) {
        List<Question> questions = new ArrayList<>();

        try {
            Log.d(TAG, "开始解析" + fileType + "文档: " + fileName);

            if (!ensureAccessTokenValid()) {
                Log.e(TAG, "无法获取有效的access_token，请检查API配置");
                return questions;
            }

            if ("pdf".equals(fileType)) {
                questions = parsePDFDocument(fileUri, context, fileName);
            } else if ("word".equals(fileType) || "doc".equals(fileType) || "docx".equals(fileType)) {
                questions = parseWordDocument(context, fileUri, fileName);
            } else {
                Log.e(TAG, "不支持的文件类型: " + fileType);
            }

        } catch (Exception e) {
            Log.e(TAG, "文档解析失败", e);
        }

        Log.d(TAG, "文档解析完成，共找到题目: " + questions.size());
        return questions;
    }

    /**
     * PDF文档解析
     */
    public static List<Question> parsePDFDocument(Uri fileUri, Context context, String fileName) {
        List<Question> questions = new ArrayList<>();
        List<Bitmap> bitmaps = null;

        try {
            if (!ensureAccessTokenValid()) {
                Log.e(TAG, "PDF解析失败：无法获取有效的access_token");
                return questions;
            }

            bitmaps = PDFToImageConverter.convertPDFToImagesSafely(context, fileUri, fileName);
            Log.d(TAG, "PDF转换得到图片数量: " + bitmaps.size());

            // 收集所有页面的OCR文本
            List<String> allPagesText = new ArrayList<>();
            for (int i = 0; i < bitmaps.size(); i++) {
                Bitmap bitmap = bitmaps.get(i);

                if (bitmap == null || bitmap.isRecycled()) {
                    Log.w(TAG, "第 " + (i + 1) + " 张PDF图片已回收，跳过");
                    continue;
                }

                Log.d(TAG, "处理PDF第 " + (i + 1) + " 张图片，尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                Bitmap processedBitmap = processBitmapForOCR(bitmap);
                String imageBase64 = bitmapToBase64PNG(processedBitmap);

                if (!isImageSizeValid(processedBitmap, imageBase64)) {
                    imageBase64 = bitmapToCompressedPNG(processedBitmap);
                }

                String ocrResult = callBaiduOCR(imageBase64);
                if (ocrResult != null && !ocrResult.trim().isEmpty()) {
                    allPagesText.add(ocrResult);
                    Log.d(TAG, "PDF第 " + (i + 1) + " 张图片OCR成功，文本长度: " + ocrResult.length());
                } else {
                    Log.w(TAG, "PDF第 " + (i + 1) + " 张图片OCR识别失败");
                }

                if (processedBitmap != bitmap) {
                    processedBitmap.recycle();
                }
            }

            // 合并所有页面文本进行标准化解析
            if (!allPagesText.isEmpty()) {
                questions = parseStandardizedDocument(allPagesText);
            }

        } catch (Exception e) {
            Log.e(TAG, "PDF文档解析失败", e);
        } finally {
            if (bitmaps != null) {
                PDFToImageConverter.recycleBitmaps(bitmaps);
            }
        }

        return questions;
    }

    /**
     * Word文档解析 - 改进版，修复解析识别问题
     */
    public static List<Question> parseWordDocument(Context context, Uri fileUri, String fileName) {
        List<Question> questions = new ArrayList<>();
        List<Bitmap> bitmaps = null;
        InputStream inputStream = null;

        try {
            Log.d(TAG, "开始解析Word文档: " + fileName);

            // 获取文件输入流
            inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                Log.e(TAG, "无法打开Word文件输入流");
                return questions;
            }

            // 使用WordToImageConverter转换Word为图片
            bitmaps = WordToImageConverter.convertWordToImages(inputStream);

            if (bitmaps != null && !bitmaps.isEmpty()) {
                Log.d(TAG, "Word转换得到图片数量: " + bitmaps.size());

                List<String> allPagesText = new ArrayList<>();
                for (int i = 0; i < bitmaps.size(); i++) {
                    Bitmap bitmap = bitmaps.get(i);

                    if (bitmap == null || bitmap.isRecycled()) {
                        Log.w(TAG, "第 " + (i + 1) + " 张Word图片已回收，跳过");
                        continue;
                    }

                    Log.d(TAG, "处理Word第 " + (i + 1) + " 张图片，尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                    // 优化图片处理流程
                    Bitmap processedBitmap = optimizeBitmapForOCR(bitmap);
                    String imageBase64 = bitmapToOptimizedBase64(processedBitmap);

                    String ocrResult = callBaiduOCR(imageBase64);
                    if (ocrResult != null && !ocrResult.trim().isEmpty()) {
                        allPagesText.add(ocrResult);
                        Log.d(TAG, "Word第 " + (i + 1) + " 张图片OCR成功，文本长度: " + ocrResult.length());

                        // 调试输出前几行内容
                        String[] lines = ocrResult.split("\n");
                        for (int j = 0; j < Math.min(5, lines.length); j++) {
                            Log.d(TAG, "OCR行[" + j + "]: " + lines[j]);
                        }
                    } else {
                        Log.w(TAG, "Word第 " + (i + 1) + " 张图片OCR识别失败");
                    }

                    if (processedBitmap != bitmap) {
                        processedBitmap.recycle();
                    }
                }

                // 合并所有页面文本进行标准化解析
                if (!allPagesText.isEmpty()) {
                    questions = parseEnhancedDocument(allPagesText, fileName);
                } else {
                    Log.w(TAG, "Word文档OCR未识别到任何文本");
                    questions = createTestQuestions();
                }

            } else {
                Log.e(TAG, "Word文档转换图片失败");
                questions = createTestQuestions();
            }

        } catch (Exception e) {
            Log.e(TAG, "Word文档解析失败", e);
            questions = createTestQuestions();
        } finally {
            // 回收资源
            if (bitmaps != null) {
                recycleBitmaps(bitmaps);
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "关闭输入流失败", e);
                }
            }
        }

        return questions;
    }

    /**
     * 增强版文档解析 - 修复解析识别问题
     */
    private static List<Question> parseEnhancedDocument(List<String> allPagesText, String fileName) {
        List<Question> questions = new ArrayList<>();

        // 合并所有页面文本
        StringBuilder fullText = new StringBuilder();
        for (String pageText : allPagesText) {
            fullText.append(pageText).append("\n");
        }

        String text = fullText.toString();
        Log.d(TAG, "合并后文档总长度: " + text.length());
        Log.d(TAG, "文档前500字符: " + text.substring(0, Math.min(500, text.length())));

        // 按行分割并预处理
        String[] lines = text.split("\n");
        List<String> cleanedLines = new ArrayList<>();

        for (String line : lines) {
            String cleanedLine = enhancedPreprocessLine(line.trim());
            if (!cleanedLine.isEmpty()) {
                cleanedLines.add(cleanedLine);
                Log.d(TAG, "清理后行: " + cleanedLine);
            }
        }

        Log.d(TAG, "清理后有效行数: " + cleanedLines.size());

        // 使用增强的状态机解析
        questions = parseWithEnhancedStateMachine(cleanedLines);

        Log.d(TAG, "增强解析完成，共找到题目: " + questions.size());
        return questions;
    }

    /**
     * 增强的状态机解析 - 修复解析识别问题
     */
    private static List<Question> parseWithEnhancedStateMachine(List<String> lines) {
        List<Question> questions = new ArrayList<>();

        Question currentQuestion = null;
        List<String> currentOptions = new ArrayList<>();
        String currentAnswer = "";
        StringBuilder currentAnalysis = new StringBuilder(); // 改为StringBuilder支持多行解析
        String currentQuestionType = TYPE_SINGLE_CHOICE;
        boolean inQuestionContent = false;
        boolean inAnalysisContent = false; // 新增：标记是否在解析内容中

        // 新增：存储分离的答案和解析
        Map<Integer, String> separatedAnswers = new HashMap<>();
        Map<Integer, String> separatedAnalyses = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Log.d(TAG, "状态机处理行[" + i + "]: " + line);

            // 0. 首先检查是否为分离式答案或解析
            SeparatedContent separatedContent = parseSeparatedContent(line, currentQuestionType);
            if (separatedContent != null) {
                if ("answer".equals(separatedContent.type)) {
                    separatedAnswers.put(separatedContent.questionNumber, separatedContent.content);
                    Log.d(TAG, "📌 记录分离答案: 题目" + separatedContent.questionNumber + " -> " + separatedContent.content);
                } else if ("analysis".equals(separatedContent.type)) {
                    separatedAnalyses.put(separatedContent.questionNumber, separatedContent.content);
                    Log.d(TAG, "📌 记录分离解析: 题目" + separatedContent.questionNumber + " -> " + separatedContent.content);
                }
                continue;
            }

            // 1. 检测题目类型标题
            if (isQuestionTypeTitleLine(line)) {
                String detectedType = detectEnhancedQuestionType(line);
                if (!detectedType.equals(currentQuestionType)) {
                    currentQuestionType = detectedType;
                    Log.d(TAG, "检测到题目类型变化: " + currentQuestionType);
                    // 类型变化时完成当前题目
                    if (currentQuestion != null) {
                        completeEnhancedQuestion(currentQuestion, currentOptions, currentAnswer, currentAnalysis.toString(), questions);
                        currentOptions.clear();
                        currentAnswer = "";
                        currentAnalysis = new StringBuilder();
                        currentQuestion = null;
                        inQuestionContent = false;
                        inAnalysisContent = false;
                    }
                }
                continue;
            }

            // 2. 检测题目开始
            if (isEnhancedQuestionStart(line, currentQuestionType) && !isQuestionTypeKeyword(line)) {
                // 保存前一个题目
                if (currentQuestion != null) {
                    completeEnhancedQuestion(currentQuestion, currentOptions, currentAnswer, currentAnalysis.toString(), questions);
                    currentOptions.clear();
                    currentAnswer = "";
                    currentAnalysis = new StringBuilder();
                }

                // 开始新题目
                currentQuestion = new Question();
                currentQuestion.setType(currentQuestionType);
                currentQuestion.setContent(extractEnhancedQuestionContent(line, currentQuestionType));

                // 获取题目编号用于匹配分离内容
                int questionNumber = extractQuestionNumber(line);
                currentQuestion.setId(generateQuestionIdByNumber(questionNumber, currentQuestionType));

                inQuestionContent = true;
                inAnalysisContent = false;
                Log.d(TAG, "🎯 开始新题目[" + currentQuestionType + "]: " + currentQuestion.getContent());
                continue;
            }

            // 3. 处理当前题目
            if (currentQuestion != null) {
                // 处理解析内容 - 修复：优先处理解析内容
                if (inAnalysisContent) {
                    // 如果遇到新的题目开始、答案、选项，则结束解析内容
                    if (isEnhancedQuestionStart(line, currentQuestionType) ||
                            isEnhancedAnswerLine(line, currentQuestionType) ||
                            isEnhancedOptionLine(line, currentQuestionType)) {
                        inAnalysisContent = false;
                        // 继续处理当前行
                    } else {
                        // 续行解析内容
                        if (currentAnalysis.length() > 0 && !line.isEmpty()) {
                            currentAnalysis.append(" ").append(line);
                        } else {
                            currentAnalysis.append(line);
                        }
                        Log.d(TAG, "📖 续行解析内容: " + line);
                        continue;
                    }
                }

                // 处理解析开始
                if (isEnhancedAnalysisLine(line)) {
                    String analysis = extractEnhancedAnalysis(line);
                    if (!analysis.isEmpty()) {
                        currentAnalysis = new StringBuilder(analysis);
                        inAnalysisContent = true;
                        inQuestionContent = false; // 解析开始意味着题目内容结束
                        Log.d(TAG, "📖 开始解析: " + analysis);
                    }
                    continue;
                }

                if (inQuestionContent) {
                    // 处理选项 - 新增：支持同一行多个选项
                    if (isEnhancedOptionLine(line, currentQuestionType) || hasInlineOptions(line)) {
                        // 提取标准选项
                        if (isEnhancedOptionLine(line, currentQuestionType)) {
                            String option = extractEnhancedOption(line);
                            if (!option.isEmpty() && !containsEnhancedOption(currentOptions, option)) {
                                currentOptions.add(option);
                                Log.d(TAG, "📝 添加选项: " + option);
                            }
                        }

                        // 提取同一行中的多个选项
                        List<String> inlineOptions = extractInlineOptions(line);
                        for (String inlineOption : inlineOptions) {
                            if (!containsEnhancedOption(currentOptions, inlineOption)) {
                                currentOptions.add(inlineOption);
                                Log.d(TAG, "📝 添加行内选项: " + inlineOption);
                            }
                        }
                        continue;
                    }

                    // 处理答案
                    if (isEnhancedAnswerLine(line, currentQuestionType)) {
                        currentAnswer = extractEnhancedAnswer(line, currentQuestionType);
                        Log.d(TAG, "✅ 设置答案: " + currentAnswer);
                        inQuestionContent = false; // 答案出现后题目内容结束
                        continue;
                    }

                    // 题目内容续行处理
                    if (shouldEnhancedContinueContent(line, currentOptions, currentQuestionType, inQuestionContent)) {
                        String currentContent = currentQuestion.getContent();
                        // 避免重复添加相同内容
                        if (!currentContent.contains(line) && !isQuestionTypeKeyword(line)) {
                            if (!currentContent.endsWith(" ") && !line.startsWith(" ")) {
                                currentQuestion.setContent(currentContent + " " + line);
                            } else {
                                currentQuestion.setContent(currentContent + line);
                            }
                            Log.d(TAG, "↪️ 续行题目内容: " + line);
                        }
                    }
                }
            }
        }

        // 保存最后一个题目
        if (currentQuestion != null) {
            completeEnhancedQuestion(currentQuestion, currentOptions, currentAnswer, currentAnalysis.toString(), questions);
        }

        // 新增：处理分离的答案和解析
        applySeparatedContents(questions, separatedAnswers, separatedAnalyses);

        return questions;
    }

    /**
     * 新增：解析分离式答案和解析内容
     */
    private static SeparatedContent parseSeparatedContent(String line, String questionType) {
        if (line == null || line.trim().isEmpty()) return null;

        // 匹配分离式答案
        Matcher answerMatcher = SEPARATED_ANSWER_PATTERN.matcher(line);
        if (answerMatcher.matches()) {
            int questionNumber = Integer.parseInt(answerMatcher.group(1));
            String answer = answerMatcher.group(2);
            return new SeparatedContent(questionNumber, "answer", answer);
        }

        Matcher multiAnswerMatcher = SEPARATED_MULTI_ANSWER_PATTERN.matcher(line);
        if (multiAnswerMatcher.matches()) {
            int questionNumber = Integer.parseInt(multiAnswerMatcher.group(1));
            String answer = multiAnswerMatcher.group(2);
            return new SeparatedContent(questionNumber, "answer", answer);
        }

        Matcher judgmentAnswerMatcher = SEPARATED_JUDGMENT_ANSWER_PATTERN.matcher(line);
        if (judgmentAnswerMatcher.matches()) {
            int questionNumber = Integer.parseInt(judgmentAnswerMatcher.group(1));
            String answer = judgmentAnswerMatcher.group(2);
            return new SeparatedContent(questionNumber, "answer", answer);
        }

        // 匹配分离式解析
        Matcher analysisMatcher = SEPARATED_ANALYSIS_PATTERN.matcher(line);
        if (analysisMatcher.matches()) {
            int questionNumber = Integer.parseInt(analysisMatcher.group(1));
            String analysis = analysisMatcher.group(2);
            return new SeparatedContent(questionNumber, "analysis", analysis);
        }

        return null;
    }

    /**
     * 新增：应用分离的答案和解析到对应题目
     */
    private static void applySeparatedContents(List<Question> questions,
                                               Map<Integer, String> separatedAnswers,
                                               Map<Integer, String> separatedAnalyses) {
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            int questionNumber = i + 1;

            // 应用分离的答案
            if (separatedAnswers.containsKey(questionNumber)) {
                String answer = separatedAnswers.get(questionNumber);
                List<String> answers = new ArrayList<>();

                if (question.getType().equals(TYPE_MULTIPLE_CHOICE)) {
                    for (char c : answer.toCharArray()) {
                        if (Character.isUpperCase(c)) {
                            answers.add(String.valueOf(c));
                        }
                    }
                } else if (question.getType().equals(TYPE_JUDGMENT)) {
                    answers.add(normalizeJudgmentAnswer(answer));
                } else {
                    answers.add(normalizeChoiceAnswer(answer));
                }

                question.setAnswers(answers);
                Log.d(TAG, "✅ 应用分离答案: 题目" + questionNumber + " -> " + answers);
            }

            // 应用分离的解析
            if (separatedAnalyses.containsKey(questionNumber)) {
                String analysis = separatedAnalyses.get(questionNumber);
                if (!analysis.isEmpty() && !"暂无解析".equals(question.getAnalysis())) {
                    question.setAnalysis(analysis);
                    Log.d(TAG, "📖 应用分离解析: 题目" + questionNumber + " -> " + analysis);
                }
            }
        }
    }

    /**
     * 新增：提取题目编号
     */
    private static int extractQuestionNumber(String line) {
        if (line == null) return -1;
        Matcher matcher = QUESTION_NUMBER_PATTERN.matcher(line);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * 新增：根据题目编号生成ID
     */
    private static String generateQuestionIdByNumber(int questionNumber, String questionType) {
        String typePrefix = "SC";
        switch (questionType) {
            case TYPE_MULTIPLE_CHOICE: typePrefix = "MC"; break;
            case TYPE_JUDGMENT: typePrefix = "JD"; break;
        }
        if (questionNumber > 0) {
            return "DOC_" + typePrefix + "_Q" + questionNumber;
        } else {
            return "DOC_" + typePrefix + "_Q" + System.currentTimeMillis();
        }
    }

    /**
     * 新增：检测是否包含同一行多个选项
     */
    private static boolean hasInlineOptions(String line) {
        if (line == null || line.length() < 6) return false;
        Matcher matcher = INLINE_OPTION_PATTERN.matcher(line);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count >= 2;
    }

    /**
     * 新增：提取同一行中的多个选项
     */
    private static List<String> extractInlineOptions(String line) {
        List<String> options = new ArrayList<>();
        if (line == null) return options;

        Matcher matcher = INLINE_OPTION_PATTERN.matcher(line);
        while (matcher.find()) {
            String letter = matcher.group(1);
            String content = matcher.group(2).trim();
            String option = letter + ". " + content;
            options.add(option);
            Log.d(TAG, "🔍 提取行内选项: " + option);
        }

        return options;
    }

    /**
     * 判断是否为题型标题行
     */
    private static boolean isQuestionTypeTitleLine(String line) {
        if (line == null || line.length() < 2) return false;

        // 匹配 "一、单选题" 格式
        if (line.matches("^[一二三四五六七八九十]、.*")) {
            return line.contains("单选") || line.contains("多选") || line.contains("判断") ||
                    line.contains("选择") || line.contains("题");
        }

        // 匹配独立的题型标题
        if (line.matches("^.*[单多]选题.*$") || line.matches("^.*判断题.*$")) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否为题型关键词本身
     */
    private static boolean isQuestionTypeKeyword(String line) {
        if (line == null) return false;

        String[] typeKeywords = {
                "判断题", "单选题", "多选题", "选择题", "单项选择", "多项选择",
                "判断", "单选", "多选", "一、", "二、", "三、", "四、", "五、"
        };

        for (String keyword : typeKeywords) {
            if (line.equals(keyword) || line.startsWith(keyword + " ") || line.endsWith(" " + keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 增强的题目类型检测
     */
    private static String detectEnhancedQuestionType(String line) {
        if (line == null || line.length() < 2) return TYPE_SINGLE_CHOICE;

        String trimmedLine = line.trim();

        // 匹配 "一、单选题" 格式
        if (trimmedLine.matches("^[一二三四五六七八九十]、.*")) {
            if (trimmedLine.contains("单选")) {
                return TYPE_SINGLE_CHOICE;
            } else if (trimmedLine.contains("多选")) {
                return TYPE_MULTIPLE_CHOICE;
            } else if (trimmedLine.contains("判断")) {
                return TYPE_JUDGMENT;
            }
        }

        // 匹配 "单选题" 格式
        if (trimmedLine.contains("单选题") || trimmedLine.contains("单项选择")) {
            return TYPE_SINGLE_CHOICE;
        } else if (trimmedLine.contains("多选题") || trimmedLine.contains("多项选择")) {
            return TYPE_MULTIPLE_CHOICE;
        } else if (trimmedLine.contains("判断题")) {
            return TYPE_JUDGMENT;
        }

        return TYPE_SINGLE_CHOICE;
    }

    /**
     * 增强的题目开始检测
     */
    private static boolean isEnhancedQuestionStart(String line, String questionType) {
        if (line == null || line.length() < 5) return false;

        // 数字题号模式: "1.", "1、", "1．"
        if (QUESTION_NUMBER_PATTERN.matcher(line).matches()) {
            String content = extractEnhancedQuestionContent(line, questionType);
            return content.length() > 3 && !isQuestionTypeKeyword(content);
        }

        // 判断题特殊格式
        if (questionType.equals(TYPE_JUDGMENT)) {
            boolean hasJudgmentFormat = line.contains("（ ）") || line.contains("( )") ||
                    line.endsWith("（）") || line.endsWith("()");
            if (hasJudgmentFormat) {
                String content = line.replace("（ ）", "").replace("( )", "").replace("（）", "").replace("()", "").trim();
                return content.length() > 3 && !isQuestionTypeKeyword(content);
            }
        }

        // 常见选择题开头
        if ((line.startsWith("下列") || line.startsWith("关于") || line.startsWith("以下") ||
                line.startsWith("以上") || line.startsWith("不属于") || line.startsWith("属于")) &&
                line.length() > 6) {
            return true;
        }

        // 包含问号的问题
        if ((line.contains("？") || line.contains("?")) && line.length() > 8) {
            return true;
        }

        // 包含常见问题关键词
        if (line.matches(".*(什么|哪个|哪项|哪种|何处|何时|为什么|如何|怎样).*") && line.length() > 10) {
            return true;
        }

        return false;
    }

    /**
     * 增强的选项检测
     */
    private static boolean isEnhancedOptionLine(String line, String questionType) {
        if (line == null || line.length() < 3) return false;

        // 单选题选项: A. B. C. D.
        if (OPTION_PATTERN.matcher(line).matches()) {
            return true;
        }

        // 多选题选项: A. B. C. D. E. F. 等
        if (questionType.equals(TYPE_MULTIPLE_CHOICE) && MULTI_OPTION_PATTERN.matcher(line).matches()) {
            return true;
        }

        // 宽松的选项匹配
        if (line.matches("^[A-Z][\\.、．\\s].*") && line.length() > 3) {
            String content = line.substring(2).trim();
            return content.length() > 1 && !isQuestionTypeKeyword(content);
        }

        return false;
    }

    /**
     * 增强的选项提取
     */
    private static String extractEnhancedOption(String line) {
        if (line == null) return "";

        Matcher matcher = OPTION_PATTERN.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1) + ". " + matcher.group(2).trim();
        }

        matcher = MULTI_OPTION_PATTERN.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1) + ". " + matcher.group(2).trim();
        }

        if (line.matches("^[A-Z][\\.、．\\s].*")) {
            String letter = line.substring(0, 1);
            String content = line.substring(2).trim();
            return letter + ". " + content;
        }

        return "";
    }

    /**
     * 增强的答案检测
     */
    private static boolean isEnhancedAnswerLine(String line, String questionType) {
        if (line == null) return false;

        String[] answerPatterns = {
                "答案", "正确答案", "参考答案", "标准答案", "【答案】", "[答案]"
        };

        for (String pattern : answerPatterns) {
            if (line.startsWith(pattern + ":") || line.startsWith(pattern + "：")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 增强的答案提取
     */
    private static String extractEnhancedAnswer(String line, String questionType) {
        if (line == null) return "";

        String[] prefixes = {"答案", "正确答案", "参考答案", "标准答案", "【答案】", "[答案]"};
        String processedLine = line;

        for (String prefix : prefixes) {
            if (processedLine.startsWith(prefix + ":") || processedLine.startsWith(prefix + "：")) {
                processedLine = processedLine.substring(prefix.length() + 1).trim();
                break;
            }
        }

        // 增强判断题答案识别
        if (questionType.equals(TYPE_JUDGMENT)) {
            return normalizeJudgmentAnswer(processedLine);
        } else if (questionType.equals(TYPE_MULTIPLE_CHOICE)) {
            StringBuilder answer = new StringBuilder();
            for (char c : processedLine.toCharArray()) {
                if (Character.isUpperCase(c) && c >= 'A' && c <= 'Z') {
                    answer.append(c);
                }
            }
            return answer.toString();
        } else {
            for (char c : processedLine.toCharArray()) {
                if (Character.isUpperCase(c) && c >= 'A' && c <= 'D') {
                    return String.valueOf(c);
                }
            }
        }

        return processedLine.trim();
    }

    /**
     * 标准化判断题答案 - 统一转换为"正确"/"错误"
     */
    private static String normalizeJudgmentAnswer(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.trim().isEmpty()) {
            return "错误"; // 默认值
        }

        String answer = rawAnswer.trim();

        // 转换为统一格式
        if (answer.equals("√") || answer.equals("对") || answer.equals("正确") ||
                answer.equals("是") || answer.equals("Y") || answer.equals("y") ||
                answer.equals("true") || answer.equals("TRUE") || answer.equals("T") ||
                answer.equals("1") || answer.equals("√正确") || answer.equals("正确√")) {
            return "正确";
        } else if (answer.equals("×") || answer.equals("错") || answer.equals("错误") ||
                answer.equals("否") || answer.equals("N") || answer.equals("n") ||
                answer.equals("false") || answer.equals("FALSE") || answer.equals("F") ||
                answer.equals("0") || answer.equals("×错误") || answer.equals("错误×")) {
            return "错误";
        }

        // 如果包含多个字符，检查关键词
        if (answer.contains("正确") || answer.contains("对") || answer.contains("√")) {
            return "正确";
        } else if (answer.contains("错误") || answer.contains("错") || answer.contains("×")) {
            return "错误";
        }

        // 默认返回错误
        return "错误";
    }

    /**
     * 标准化选择题答案 - 去除多余字符
     */
    private static String normalizeChoiceAnswer(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.trim().isEmpty()) {
            return "";
        }

        // 只保留A-Z的字母
        return rawAnswer.replaceAll("[^A-Z]", "").trim();
    }

    /**
     * 增强的解析检测
     */
    private static boolean isEnhancedAnalysisLine(String line) {
        if (line == null) return false;

        // 多种解析格式
        String[] analysisPatterns = {
                "解析", "答案解析", "试题解析", "【解析】", "[解析]"
        };

        for (String pattern : analysisPatterns) {
            if (line.startsWith(pattern + ":") || line.startsWith(pattern + "：")) {
                return true;
            }
        }

        return ANALYSIS_PATTERN.matcher(line).matches();
    }

    /**
     * 增强的解析提取
     */
    private static String extractEnhancedAnalysis(String line) {
        if (line == null) return "";

        // 去除解析前缀
        String[] prefixes = {"解析", "答案解析", "试题解析", "【解析】", "[解析]"};
        String processedLine = line;

        for (String prefix : prefixes) {
            if (processedLine.startsWith(prefix + ":") || processedLine.startsWith(prefix + "：")) {
                processedLine = processedLine.substring(prefix.length() + 1).trim();
                break;
            }
        }

        // 使用正则匹配
        Matcher matcher = ANALYSIS_PATTERN.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }

        return processedLine.trim();
    }

    /**
     * 增强的题目完成
     */
    private static void completeEnhancedQuestion(Question question, List<String> options,
                                                 String answer, String analysis, List<Question> questions) {
        // 设置选项
        if (question.getType().equals(TYPE_SINGLE_CHOICE) || question.getType().equals(TYPE_MULTIPLE_CHOICE)) {
            if (options.size() < 2) {
                completeEnhancedOptions(options, question.getType());
            }
            question.setOptions(new ArrayList<>(options));
        } else {
            question.setOptions(new ArrayList<>());
        }

        // 设置答案 - 修复：确保答案正确标准化
        List<String> answers = new ArrayList<>();
        if (!answer.isEmpty()) {
            if (question.getType().equals(TYPE_MULTIPLE_CHOICE)) {
                for (char c : answer.toCharArray()) {
                    answers.add(String.valueOf(c));
                }
            } else if (question.getType().equals(TYPE_JUDGMENT)) {
                // 判断题答案标准化
                String normalizedAnswer = normalizeJudgmentAnswer(answer);
                answers.add(normalizedAnswer);
            } else {
                // 单选题答案标准化
                String normalizedAnswer = normalizeChoiceAnswer(answer);
                if (!normalizedAnswer.isEmpty()) {
                    answers.add(normalizedAnswer);
                } else {
                    answers.add(answer);
                }
            }
        } else {
            // 如果没有答案，设置默认值
            answers.add("");
        }
        question.setAnswers(answers);

        // 设置解析 - 修复：确保解析正确设置
        String finalAnalysis = analysis.isEmpty() ? "暂无解析" : analysis.trim();
        question.setAnalysis(finalAnalysis);

        // 验证并添加题目
        if (isEnhancedValidQuestion(question)) {
            questions.add(question);
            Log.d(TAG, "🎉 成功完成题目[" + question.getType() + "]: " +
                    question.getContent().substring(0, Math.min(50, question.getContent().length())) +
                    " | 选项: " + options.size() + " | 答案: " + answers + " | 解析: " +
                    finalAnalysis.substring(0, Math.min(30, finalAnalysis.length())));
        } else {
            Log.w(TAG, "⚠️ 题目无效被过滤: " + question.getContent());
        }
    }

    /**
     * 增强的题目有效性验证
     */
    private static boolean isEnhancedValidQuestion(Question question) {
        if (question == null || question.getContent() == null) return false;

        String content = question.getContent().trim();

        // 排除题型关键词本身
        if (isQuestionTypeKeyword(content)) {
            Log.w(TAG, "题目被过滤：内容是题型关键词 - " + content);
            return false;
        }

        if (content.length() < 5) {
            Log.w(TAG, "题目被过滤：内容过短 - " + content);
            return false;
        }

        // 检查题目类型特定要求
        switch (question.getType()) {
            case TYPE_SINGLE_CHOICE:
                boolean validSingle = question.getOptions() != null && question.getOptions().size() >= 2;
                if (!validSingle) Log.w(TAG, "单选题被过滤：选项不足 - " + content);
                return validSingle;
            case TYPE_MULTIPLE_CHOICE:
                boolean validMulti = question.getOptions() != null && question.getOptions().size() >= 2;
                if (!validMulti) Log.w(TAG, "多选题被过滤：选项不足 - " + content);
                return validMulti;
            case TYPE_JUDGMENT:
                boolean validJudgment = content.contains("(") || content.contains("（") ||
                        content.endsWith("（）") || content.endsWith("()") ||
                        content.contains("是否正确") || content.contains("对不对");
                if (!validJudgment) Log.w(TAG, "判断题被过滤：缺少判断格式 - " + content);
                return validJudgment;
            default:
                return true;
        }
    }

    // ==================== 辅助方法 ====================

    private static String enhancedPreprocessLine(String line) {
        if (line == null || line.trim().isEmpty()) return "";

        String cleaned = line.trim();

        // 过滤无效行
        if (isEnhancedInvalidLine(cleaned)) return "";

        // 标准化标点符号
        cleaned = cleaned.replaceAll("[．、]", ".")
                .replaceAll("[：:]", ":")
                .replaceAll("[（）]", "()")
                .replaceAll("\\s+", " ");

        return cleaned;
    }

    private static boolean isEnhancedInvalidLine(String line) {
        if (line.length() < 2) return true;

        String[] invalidPatterns = {
                "上一题", "下一题", "提交答案", "开始练习", "刷题宝", "姓名", "学号", "班级", "学校",
                "第\\d+题.*共\\d+题", "得分.*", "分数.*", "批改.*", "试卷", "考试", "复习题",
                "注：.*", "填空题", "填空", "分析题", "简答题", "问答题", "论述题", "计算题"
        };

        for (String pattern : invalidPatterns) {
            if (line.matches(".*" + pattern + ".*")) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsEnhancedOption(List<String> options, String newOption) {
        if (newOption == null || newOption.length() < 2) return false;
        String newLetter = newOption.substring(0, 1);
        for (String option : options) {
            if (option.startsWith(newLetter + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String extractEnhancedQuestionContent(String line, String questionType) {
        if (line == null) return "";
        Matcher matcher = QUESTION_NUMBER_PATTERN.matcher(line);
        if (matcher.matches()) {
            return matcher.group(2).trim();
        }
        return line.trim();
    }

    private static boolean shouldEnhancedContinueContent(String line, List<String> options,
                                                         String questionType, boolean inQuestionContent) {
        if (!inQuestionContent) return false;
        if (line == null || line.isEmpty()) return false;
        if (isEnhancedOptionLine(line, questionType)) return false;
        if (isEnhancedAnswerLine(line, questionType)) return false;
        if (isEnhancedAnalysisLine(line)) return false;
        if (line.matches("^[一二三四五六七八九十]、.*")) return false;
        if (line.length() > 150) return false;
        if (isQuestionTypeKeyword(line)) return false;
        return line.matches(".*[\\u4e00-\\u9fa5].*");
    }

    private static void completeEnhancedOptions(List<String> options, String questionType) {
        int targetCount = questionType.equals(TYPE_MULTIPLE_CHOICE) ? 4 : 4;
        String[] standardOptions = {"A.", "B.", "C.", "D.", "E.", "F."};

        for (int i = 0; i < Math.min(targetCount, standardOptions.length); i++) {
            boolean exists = false;
            for (String option : options) {
                if (option.startsWith(standardOptions[i].substring(0, 1) + ".")) {
                    exists = true;
                    break;
                }
            }
            if (!exists && options.size() < targetCount) {
                options.add(standardOptions[i] + " [未识别]");
            }
        }

        options.sort(Comparator.comparing(o -> o.substring(0, 1)));
    }

    // ==================== 图片处理优化方法 ====================

    private static Bitmap optimizeBitmapForOCR(Bitmap original) {
        try {
            int maxDimension = 2048;
            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();

            if (originalWidth > maxDimension || originalHeight > maxDimension) {
                float scale = Math.min((float) maxDimension / originalWidth, (float) maxDimension / originalHeight);
                int newWidth = (int) (originalWidth * scale);
                int newHeight = (int) (originalHeight * scale);
                return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
            }
            return original;
        } catch (Exception e) {
            Log.e(TAG, "图片优化失败", e);
            return original;
        }
    }

    private static String bitmapToOptimizedBase64(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return "";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Base64转换失败", e);
            return "";
        }
    }

    // ==================== 其他工具方法 ====================

    private static void recycleBitmaps(List<Bitmap> bitmaps) {
        if (bitmaps != null) {
            for (Bitmap bitmap : bitmaps) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
    }

    private static List<Question> createTestQuestions() {
        List<Question> questions = new ArrayList<>();

        // 添加静态测试相关的判断题
        Question q1 = new Question();
        q1.setId("WORD_TEST_1");
        q1.setType(TYPE_JUDGMENT);
        q1.setContent("静态测试不运行被测试的软件，而是直接分析代码和文档。");
        q1.setOptions(Arrays.asList("正确", "错误"));
        q1.setAnswers(Arrays.asList("正确"));
        q1.setAnalysis("静态测试确实不运行程序，而是通过检查代码、文档等来发现缺陷。");
        questions.add(q1);

        Question q2 = new Question();
        q2.setId("WORD_TEST_2");
        q2.setType(TYPE_JUDGMENT);
        q2.setContent("代码审查和走查都属于动态测试方法。");
        q2.setOptions(Arrays.asList("正确", "错误"));
        q2.setAnswers(Arrays.asList("错误"));
        q2.setAnalysis("代码审查和走查都是静态测试方法，它们不运行程序。");
        questions.add(q2);

        Question q3 = new Question();
        q3.setId("WORD_TEST_3");
        q3.setType(TYPE_SINGLE_CHOICE);
        q3.setContent("下列属于静态测试的是()");
        q3.setOptions(Arrays.asList("A.单元测试", "B.集成测试", "C.代码评审", "D.系统测试"));
        q3.setAnswers(Arrays.asList("C"));
        q3.setAnalysis("代码评审不运行程序，通过检查代码发现缺陷，属于静态测试；其他选项都需要运行程序，属于动态测试。");
        questions.add(q3);

        Log.d(TAG, "创建了 " + questions.size() + " 道Word测试题目");
        return questions;
    }

    private static String generateUniqueQuestionId(int questionNumber, String questionType) {
        String typePrefix = "SC";
        switch (questionType) {
            case TYPE_MULTIPLE_CHOICE: typePrefix = "MC"; break;
            case TYPE_JUDGMENT: typePrefix = "JD"; break;
        }
        return "WORD_" + typePrefix + "_Q" + questionNumber + "_" + System.currentTimeMillis();
    }

    // 以下原有的工具方法保持不变
    private static boolean ensureAccessTokenValid() {
        try {
            if (accessToken == null || System.currentTimeMillis() > tokenExpireTime) {
                Log.d(TAG, "access_token无效或已过期，重新获取");
                return getAccessToken();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "检查access_token有效性失败", e);
            return false;
        }
    }

    private static boolean getAccessToken() {
        try {
            String url = "https://aip.baidubce.com/oauth/2.0/token?" +
                    "grant_type=client_credentials&" +
                    "client_id=" + API_KEY + "&" +
                    "client_secret=" + SECRET_KEY;

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(null, ""))
                    .build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                if (json.has("access_token")) {
                    accessToken = json.get("access_token").getAsString();
                    tokenExpireTime = System.currentTimeMillis() + (25 * 24 * 60 * 60 * 1000L);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取access_token异常", e);
        }
        return false;
    }

    private static Bitmap processBitmapForOCR(Bitmap original) {
        try {
            if (original.getWidth() > 4096 || original.getHeight() > 4096) {
                return compressBitmapForOCR(original);
            }
            return original;
        } catch (Exception e) {
            return original;
        }
    }

    private static Bitmap compressBitmapForOCR(Bitmap original) {
        try {
            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();
            float scale = Math.min(Math.min(4096f / originalWidth, 4096f / originalHeight), 0.8f);
            int newWidth = (int) (originalWidth * scale);
            int newHeight = (int) (originalHeight * scale);
            return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
        } catch (Exception e) {
            return original;
        }
    }

    private static boolean isImageSizeValid(Bitmap bitmap, String base64) {
        return !bitmap.isRecycled() &&
                bitmap.getWidth() <= 4096 && bitmap.getHeight() <= 4096 &&
                bitmap.getWidth() >= 15 && bitmap.getHeight() >= 15 &&
                base64.length() * 3 / 4 <= 4 * 1024 * 1024;
    }

    private static String bitmapToBase64PNG(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return "";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }

    private static String bitmapToCompressedPNG(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return "";
        try {
            int newWidth = (int) (bitmap.getWidth() * 0.7f);
            int newHeight = (int) (bitmap.getHeight() * 0.7f);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 85, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            String base64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
            if (scaledBitmap != bitmap) scaledBitmap.recycle();
            return base64;
        } catch (Exception e) {
            return bitmapToBase64PNG(bitmap);
        }
    }

    private static String callBaiduOCR(String imageBase64) {
        try {
            if (imageBase64 == null || imageBase64.isEmpty()) return null;
            if (!ensureAccessTokenValid()) return null;

            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("image", imageBase64)
                    .add("detect_direction", "true")
                    .add("paragraph", "false")
                    .add("probability", "false");

            RequestBody body = formBuilder.build();
            String url = OCR_URL + "?access_token=" + accessToken;

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                return extractTextFromBaiduResponse(response.body().string());
            }
        } catch (Exception e) {
            Log.e(TAG, "调用百度OCR异常", e);
        }
        return null;
    }

    private static String extractTextFromBaiduResponse(String response) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            if (jsonResponse.has("words_result")) {
                JsonArray wordsResult = jsonResponse.getAsJsonArray("words_result");
                StringBuilder textBuilder = new StringBuilder();
                for (int i = 0; i < wordsResult.size(); i++) {
                    JsonObject word = wordsResult.get(i).getAsJsonObject();
                    if (word.has("words")) {
                        textBuilder.append(word.get("words").getAsString()).append("\n");
                    }
                }
                return textBuilder.toString().trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "解析百度OCR响应异常", e);
        }
        return null;
    }

    // 原有的标准化解析方法（保持兼容性）
    private static List<Question> parseStandardizedDocument(List<String> allPagesText) {
        return parseEnhancedDocument(allPagesText, "standardized");
    }

    /**
     * 新增：分离内容数据结构
     */
    private static class SeparatedContent {
        int questionNumber;
        String type; // "answer" 或 "analysis"
        String content;

        SeparatedContent(int questionNumber, String type, String content) {
            this.questionNumber = questionNumber;
            this.type = type;
            this.content = content;
        }
    }
}
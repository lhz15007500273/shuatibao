package com.shuatibao.utils;

import android.content.Context;
import android.util.Log;
import com.shuatibao.model.Question;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentParser {
    private static final String TAG = "DocumentParser";

    // 题目类型常量
    private static final String TYPE_SINGLE_CHOICE = "single_choice";
    private static final String TYPE_MULTIPLE_CHOICE = "multiple_choice";
    private static final String TYPE_JUDGMENT = "judgment";
    private static final String TYPE_FILL_BLANK = "fill_blank";

    // 常见中文字符集
    private static final String[] CHINESE_CHARSETS = {"GBK", "GB2312", "UTF-8", "GB18030"};

    public static List<Question> parseWordDocument(Context context, InputStream inputStream) {
        return parseTextContent(inputStream);
    }

    public static List<Question> parsePdfDocument(Context context, InputStream inputStream) {
        return parseTextContent(inputStream);
    }

    private static List<Question> parseTextContent(InputStream inputStream) {
        List<Question> questions = new ArrayList<>();

        try {
            // 读取文档内容，解决乱码问题
            String content = readContentWithChineseEncoding(inputStream);

            Log.d(TAG, "文档内容长度: " + content.length());
            Log.d(TAG, "文档内容前500000字符: " + (content.length() > 500000 ? content.substring(0, 500000) : content));

            // 检查是否包含中文字符
            if (!containsChinese(content)) {
                Log.e(TAG, "文档内容不包含中文字符，可能是编码问题");
                return questions;
            }

            // 解析文本内容
            questions = parseEnhancedExamPaper(content);

            Log.d(TAG, "解析到题目数量: " + questions.size());

        } catch (Exception e) {
            Log.e(TAG, "解析文档失败", e);
            questions = new ArrayList<>();
        }

        return questions;
    }

    /**
     * 增强的试卷解析方法
     */
    private static List<Question> parseEnhancedExamPaper(String text) {
        List<Question> questions = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return questions;
        }

        Log.d(TAG, "开始增强解析试卷...");

        // 方法1: 按大题类型分割（支持更多格式）
        String[] mainSections = text.split("(一、|二、|三、|四、|五、|六、|七、|八、|九、|十、|第[一二三四五六七八九十]部分|卷\\([一二三四五六七八九十]\\))");
        Log.d(TAG, "检测到试卷大题数量: " + mainSections.length);

        if (mainSections.length > 1) {
            for (int i = 1; i < mainSections.length; i++) {
                String section = mainSections[i].trim();
                if (section.isEmpty()) continue;

                // 提取大题标题和类型
                String[] titleAndContent = section.split("\\n", 2);
                String sectionTitle = titleAndContent.length > 0 ? titleAndContent[0].trim() : "";
                String sectionContent = titleAndContent.length > 1 ? titleAndContent[1] : section;

                String sectionType = determineSectionType(sectionTitle);
                Log.d(TAG, "处理大题: " + sectionTitle + ", 类型: " + sectionType);

                // 解析该大题下的所有题目
                List<Question> sectionQuestions = parseEnhancedQuestionsInSection(sectionContent, sectionType);
                questions.addAll(sectionQuestions);

                Log.d(TAG, "大题 " + sectionTitle + " 解析到 " + sectionQuestions.size() + " 道题目");
            }
        }

        // 方法2: 如果大题分割失败，使用题目编号解析
        if (questions.isEmpty()) {
            Log.d(TAG, "大题分割失败，使用增强题目编号解析");
            questions = parseEnhancedWithQuestionNumber(text);
        }

        return questions;
    }

    /**
     * 增强的大题题目解析
     */
    private static List<Question> parseEnhancedQuestionsInSection(String sectionContent, String sectionType) {
        List<Question> questions = new ArrayList<>();

        // 使用正则表达式匹配所有题目编号格式
        Pattern questionPattern = Pattern.compile(
                "(?:(?:^|\\n)\\s*(\\d+)[\\.．\\)]\\s*([^\\d]*?(?=(?:\\n\\s*\\d+[\\.．\\)]|\\n\\s*答案|\\n\\s*解析|$))))",
                Pattern.DOTALL
        );

        Matcher matcher = questionPattern.matcher(sectionContent);

        int count = 0;
        while (matcher.find()) {
            String questionNumber = matcher.group(1);
            String questionText = matcher.group(2).trim();

            if (!questionText.isEmpty()) {
                Question question = parseEnhancedSingleQuestion(questionNumber, questionText, sectionType);
                if (question != null && !question.getContent().trim().isEmpty()) {
                    questions.add(question);
                    count++;

                    // 调试信息
                    String contentPreview = question.getContent().length() > 30 ?
                            question.getContent().substring(0, 30) + "..." : question.getContent();
                    Log.d(TAG, "解析题目 " + questionNumber + ": " + contentPreview);
                }
            }
        }

        Log.d(TAG, "在大题中解析到 " + count + " 道题目");
        return questions;
    }

    /**
     * 增强的单个题目解析
     */
    private static Question parseEnhancedSingleQuestion(String questionNumber, String questionText, String defaultType) {
        try {
            if (questionText.trim().isEmpty()) {
                return null;
            }

            String content = "";
            List<String> options = new ArrayList<>();
            List<String> answers = new ArrayList<>();
            String analysis = "";

            // 分离题目内容和选项部分
            String[] lines = questionText.split("\\n");
            StringBuilder contentBuilder = new StringBuilder();
            boolean inOptions = false;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 检测选项开始 (A. B. C. D.)
                if (line.matches("^[A-D][\\.．、].*")) {
                    inOptions = true;
                    options.add(formatOptionLine(line));
                    continue;
                }

                // 检测答案开始
                if (line.startsWith("答案") || line.startsWith("正确答案") || line.startsWith("参考答案")) {
                    answers = extractAnswersFromLine(line);
                    continue;
                }

                // 检测解析开始
                if (line.startsWith("解析") || line.startsWith("试题解析")) {
                    analysis = extractAnalysisFromLine(line);
                    continue;
                }

                // 如果是内容部分
                if (!inOptions) {
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("\n");
                    }
                    contentBuilder.append(line);
                } else {
                    // 如果是选项的延续内容
                    if (!options.isEmpty()) {
                        String lastOption = options.get(options.size() - 1);
                        options.set(options.size() - 1, lastOption + " " + line);
                    }
                }
            }

            content = contentBuilder.toString().trim();
            if (content.isEmpty()) {
                return null;
            }

            // 确定题型
            String type = determineEnhancedQuestionType(content, options, defaultType, questionText);

            // 为判断题和填空题生成默认答案（如果没有找到答案）
            if (answers.isEmpty()) {
                if (TYPE_JUDGMENT.equals(type)) {
                    answers.add("正确");
                } else if (TYPE_FILL_BLANK.equals(type)) {
                    answers.add("参考答案");
                }
            }

            // 创建题目对象
            Question question = new Question(type, content, options, answers, analysis);
            question.setId("Q" + questionNumber + "_" + System.currentTimeMillis());

            Log.d(TAG, "创建题目 " + questionNumber + " - 类型: " + type +
                    ", 选项数: " + options.size() + ", 答案数: " + answers.size());

            return question;

        } catch (Exception e) {
            Log.e(TAG, "解析题目 " + questionNumber + " 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 增强的题目编号解析
     */
    private static List<Question> parseEnhancedWithQuestionNumber(String text) {
        List<Question> questions = new ArrayList<>();

        // 匹配所有题目编号格式（包括各种括号和点）
        Pattern pattern = Pattern.compile(
                "(?:^|\\n)\\s*(\\d+)[\\.．\\)]\\s*([^\\d]*?(?=(?:\\n\\s*\\d+[\\.．\\)]|\\n\\s*答案|\\n\\s*解析|$)))",
                Pattern.DOTALL | Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(text);

        int count = 0;
        while (matcher.find()) {
            String questionNumber = matcher.group(1);
            String questionText = matcher.group(2).trim();

            if (!questionText.isEmpty()) {
                Question question = parseEnhancedSingleQuestion(questionNumber, questionText, TYPE_SINGLE_CHOICE);
                if (question != null) {
                    questions.add(question);
                    count++;
                }
            }
        }

        Log.d(TAG, "题目编号解析找到 " + count + " 道题目");
        return questions;
    }

    /**
     * 从单行提取答案
     */
    private static List<String> extractAnswersFromLine(String line) {
        List<String> answers = new ArrayList<>();

        Pattern[] patterns = {
                Pattern.compile("答案[：:]\\s*([^\\n]+)"),
                Pattern.compile("正确答案[：:]\\s*([^\\n]+)"),
                Pattern.compile("参考答案[：:]\\s*([^\\n]+)"),
                Pattern.compile("【答案】\\s*([^】]+)")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String answerStr = matcher.group(1).trim();
                // 分割多个答案
                String[] answerArray = answerStr.split("[、,，]");
                for (String answer : answerArray) {
                    if (!answer.trim().isEmpty()) {
                        answers.add(answer.trim());
                    }
                }
                break;
            }
        }

        return answers;
    }

    /**
     * 从单行提取解析
     */
    private static String extractAnalysisFromLine(String line) {
        Pattern[] patterns = {
                Pattern.compile("解析[：:]\\s*([^\\n]+)"),
                Pattern.compile("试题解析[：:]\\s*([^\\n]+)"),
                Pattern.compile("【解析】\\s*([^】]+)")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return "";
    }

    /**
     * 增强的题型判断
     */
    private static String determineEnhancedQuestionType(String content, List<String> options,
                                                        String defaultType, String fullText) {
        // 1. 根据选项数量判断
        if (!options.isEmpty()) {
            if (options.size() >= 2) {
                // 检查是否为多选题特征
                if (fullText.contains("多选") || fullText.contains("哪些") ||
                        fullText.contains("至少") || fullText.contains("都") ||
                        options.size() > 4) {
                    return TYPE_MULTIPLE_CHOICE;
                }
                return TYPE_SINGLE_CHOICE;
            }
        }

        // 2. 根据内容特征判断
        if (content.contains("（ ）") || content.contains("( )") ||
                content.contains("是否正确") || content.contains("判断对错") ||
                content.contains("下列说法正确的是")) {
            return TYPE_JUDGMENT;
        }

        if (content.contains("______") || content.contains("_") ||
                content.contains("空白") || content.contains("填写") ||
                content.contains("填入") || content.matches(".*[^。！？]\\s*$")) {
            return TYPE_FILL_BLANK;
        }

        // 3. 使用默认类型
        return defaultType;
    }

    // 以下方法保持不变...
    private static String readContentWithChineseEncoding(InputStream inputStream) throws IOException {
        // 标记输入流以便重置
        if (inputStream.markSupported()) {
            inputStream.mark(1024 * 1024 * 5); // 5MB
        }

        // 优先尝试UTF-8（现代文档常用编码）
        String content = readWithCharset(inputStream, "UTF-8");
        if (containsChinese(content) && content.length() > 100) {
            Log.d(TAG, "使用UTF-8编码成功读取中文内容");
            return content;
        }

        // 重置输入流
        if (inputStream.markSupported()) {
            inputStream.reset();
        }

        // 尝试其他编码
        for (String charset : CHINESE_CHARSETS) {
            if ("UTF-8".equals(charset)) continue;

            try {
                content = readWithCharset(inputStream, charset);
                if (containsChinese(content) && content.length() > 100) {
                    Log.d(TAG, "使用编码 " + charset + " 成功读取中文内容");
                    return content;
                }

                if (inputStream.markSupported()) {
                    inputStream.reset();
                }
            } catch (Exception e) {
                Log.w(TAG, "编码 " + charset + " 读取失败: " + e.getMessage());
            }
        }

        Log.w(TAG, "所有编码尝试失败，返回UTF-8读取的内容");
        return content;
    }

    private static String readWithCharset(InputStream inputStream, String charset) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName(charset)));
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[8192];
        int charsRead;

        while ((charsRead = reader.read(buffer)) != -1) {
            content.append(buffer, 0, charsRead);
        }
        reader.close();
        return content.toString();
    }

    private static boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) return false;
        Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]");
        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }

    private static String determineSectionType(String sectionTitle) {
        if (sectionTitle.contains("单选题") || sectionTitle.contains("单选") ||
                sectionTitle.contains("选择题") || sectionTitle.contains("选择")) {
            return TYPE_SINGLE_CHOICE;
        } else if (sectionTitle.contains("多选题") || sectionTitle.contains("多选")) {
            return TYPE_MULTIPLE_CHOICE;
        } else if (sectionTitle.contains("判断题") || sectionTitle.contains("判断")) {
            return TYPE_JUDGMENT;
        } else if (sectionTitle.contains("填空题") || sectionTitle.contains("填空")) {
            return TYPE_FILL_BLANK;
        } else if (sectionTitle.contains("分析题") || sectionTitle.contains("问答") ||
                sectionTitle.contains("简答") || sectionTitle.contains("论述")) {
            return TYPE_FILL_BLANK;
        }
        return TYPE_SINGLE_CHOICE;
    }

    private static String formatOptionLine(String line) {
        if (line.matches("^[A-D][\\.．、].*")) {
            return line.replace("．", ".").replace("、", ".").replaceAll("^([A-D])[\\.](.*)", "$1. $2");
        }
        return line;
    }
}
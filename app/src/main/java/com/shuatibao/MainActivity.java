package com.shuatibao;

import static android.content.ContentValues.TAG;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.shuatibao.activity.CreateQuestionActivity;
import com.shuatibao.activity.QuestionBankActivity;
import com.shuatibao.activity.WrongQuestionsActivity;
import com.shuatibao.database.DatabaseHelper;
import com.shuatibao.model.ExamPaper;
import com.shuatibao.model.Question;
import com.shuatibao.utils.BaiduOCRDocumentParser;
import com.shuatibao.utils.FilePickerUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private MaterialCardView cardUpload, cardQuestionBank;
    private ProgressDialog progressDialog;
    private DatabaseHelper databaseHelper;

    private static final int FILE_PICK_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化数据库
        databaseHelper = new DatabaseHelper(this);
        databaseHelper.initializeSampleData();

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        cardUpload = findViewById(R.id.card_upload);
        cardQuestionBank = findViewById(R.id.card_question_bank);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在解析文档...");
        progressDialog.setCancelable(false);
    }

    private void setupClickListeners() {
        // 上传文档点击事件
        cardUpload.setOnClickListener(v -> {
            showUploadDialog();
        });

        // 题库点击事件
        cardQuestionBank.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuestionBankActivity.class);
            startActivity(intent);
        });

        // 错题本点击事件
        findViewById(R.id.card_wrong_questions).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WrongQuestionsActivity.class);
            startActivity(intent);
        });
        // 新建试题点击事件
        findViewById(R.id.card_create_question).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateQuestionActivity.class);
            startActivity(intent);
        });
    }

    private void showUploadDialog() {
        new AlertDialog.Builder(this)
                .setTitle("上传文档")
                .setMessage("请选择要上传的Word或PDF文档")
                .setPositiveButton("选择文档", (dialog, which) -> {
                    pickDocument();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickDocument() {
        FilePickerUtil.pickDocument(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (FilePickerUtil.isFilePickResult(requestCode)) {
            FilePickerUtil.FilePickResult result = FilePickerUtil.processFilePickResult(resultCode, data, this);

            if (result.isSuccess) {
                // 调试信息：打印文件信息
                Log.d("MainActivity", "选择的文件: " + result.fileName + ", 类型: " + result.fileType);

                // 使用放宽的文件类型检查
                if (!isSupportedFileType(result.fileType, result.fileName)) {
                    Toast.makeText(this, "请选择Word(.doc/.docx)或PDF(.pdf)文档，当前文件: " + result.fileName, Toast.LENGTH_LONG).show();
                    return;
                }
                // 开始解析文档
                parseDocument(result.fileUri, result.fileName, result.fileType);
            } else {
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 放宽文件类型检查
    private boolean isSupportedFileType(String fileType, String fileName) {
        // 如果文件类型检测为word或pdf，直接通过
        if ("word".equals(fileType) || "pdf".equals(fileType)) {
            return true;
        }

        // 如果文件类型检测失败，根据文件名后缀判断
        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            boolean isSupported = lowerFileName.endsWith(".doc") ||
                    lowerFileName.endsWith(".docx") ||
                    lowerFileName.endsWith(".pdf");
            Log.d("MainActivity", "文件名检查: " + fileName + ", 是否支持: " + isSupported);
            return isSupported;
        }

        Log.d("MainActivity", "文件名为空，不支持");
        return false;
    }

    private void parseDocument(Uri fileUri, String fileName, String fileType) {
        progressDialog.show();
        progressDialog.setMessage("正在解析文档...");

        new Thread(() -> {
            try {
                List<Question> questions = new ArrayList<>();

                // 使用百度OCR解析，根据第一段代码的方法签名修正调用
                questions = BaiduOCRDocumentParser.parseDocument(MainActivity.this, fileUri, fileType, fileName);

                List<Question> finalQuestions = questions;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    handleParseResult(finalQuestions, fileName);
                });

            } catch (Exception e) {
                Log.e("MainActivity", "处理文件失败", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "文件处理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private List<Question> removeDuplicateQuestions(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return new ArrayList<>();
        }

        List<Question> uniqueQuestions = new ArrayList<>();
        Set<String> contentSet = new HashSet<>();

        for (Question question : questions) {
            // 修复：检查question和content是否为null
            if (question == null || question.getContent() == null) {
                Log.w(TAG, "发现空题目或空内容，跳过");
                continue;
            }

            String content = question.getContent().trim();
            if (content.isEmpty()) {
                Log.w(TAG, "题目内容为空，跳过");
                continue;
            }

            // 简化内容用于去重比较
            String simplifiedContent = content.replaceAll("\\s+", " ")
                    .replaceAll("[.．、]", ".")
                    .substring(0, Math.min(content.length(), 50)); // 取前50字符比较

            if (!contentSet.contains(simplifiedContent)) {
                contentSet.add(simplifiedContent);
                uniqueQuestions.add(question);
            } else {
                Log.d(TAG, "发现重复题目，跳过: " + simplifiedContent);
            }
        }

        return uniqueQuestions;
    }

    /**
     * 保存题目到数据库
     */
    private void saveQuestionsToDatabase(List<Question> questions, String fileName) {
        if (questions == null || questions.isEmpty()) {
            Log.w(TAG, "没有题目需要保存");
            return;
        }

        // 生成试卷标题
        String paperTitle = generatePaperTitle(fileName);

        // 创建试卷并保存
        ExamPaper examPaper = new ExamPaper(paperTitle, questions, fileName);
        long result = databaseHelper.saveExamPaper(examPaper);

        if (result != -1) {
            Log.d("MainActivity", "成功保存试卷: " + paperTitle + ", 题目数量: " + questions.size());
        } else {
            Log.e("MainActivity", "保存试卷失败");
        }
    }

    /**
     * 生成试卷标题
     */
    private String generatePaperTitle(String fileName) {
        if (fileName == null) {
            return "导入的试卷_" + System.currentTimeMillis();
        }

        // 去除文件扩展名，作为试卷标题
        String title = fileName.replace(".docx", "")
                .replace(".doc", "")
                .replace(".pdf", "")
                .replace(".PDF", "")
                .replace("_", " ")
                .replace("-", " ");

        // 如果标题为空，使用默认标题
        if (title.trim().isEmpty()) {
            title = "导入的试卷_" + System.currentTimeMillis();
        }

        return title;
    }

    private void handleParseResult(List<Question> questions, String fileName) {
        // 修复：添加空值检查
        if (questions == null) {
            Log.e(TAG, "题目列表为null");
            Toast.makeText(this, "解析失败：题目列表为空", Toast.LENGTH_LONG).show();
            return;
        }

        if (questions.isEmpty()) {
            Toast.makeText(this, "未解析到任何题目，请检查文档格式", Toast.LENGTH_LONG).show();
            return;
        }

        // 修复：过滤掉空题目
        List<Question> validQuestions = new ArrayList<>();
        for (Question question : questions) {
            if (question != null && question.getContent() != null && !question.getContent().trim().isEmpty()) {
                validQuestions.add(question);
            } else {
                Log.w(TAG, "过滤掉空题目: " + question);
            }
        }

        if (validQuestions.isEmpty()) {
            Toast.makeText(this, "所有题目内容都为空，请检查文档格式", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "有效题目数量: " + validQuestions.size() + "/" + questions.size());

        // 统计题目类型和有无解析的情况
        int singleChoiceCount = 0;
        int multipleChoiceCount = 0;
        int judgmentCount = 0;
        int fillBlankCount = 0;
        int noAnalysisCount = 0;
        int noAnswerCount = 0;

        for (Question question : validQuestions) {
            // 修复：确保类型不为null
            String type = question.getType();
            if (type == null) {
                type = "unknown";
                question.setType(type);
            }

            switch (type) {
                case "single_choice":
                    singleChoiceCount++;
                    break;
                case "multiple_choice":
                    multipleChoiceCount++;
                    break;
                case "judgment":
                    judgmentCount++;
                    break;
                case "fill_blank":
                    fillBlankCount++;
                    break;
                default:
                    // 未知类型，按单选题处理
                    singleChoiceCount++;
                    question.setType("single_choice");
                    break;
            }

            // 修复：检查解析和答案是否为null
            if (question.getAnalysis() == null || question.getAnalysis().trim().isEmpty()) {
                noAnalysisCount++;
                // 设置默认解析
                question.setAnalysis("暂无解析");
            }

            if (question.getAnswers() == null || question.getAnswers().isEmpty()) {
                noAnswerCount++;
            }
        }

        // 保存到数据库
        saveQuestionsToDatabase(validQuestions, fileName);

        // 显示解析结果详情
        String message = String.format("解析成功！\n\n" +
                        "📊 题目统计\n" +
                        "• 题目总数: %d\n" +
                        "• 单选题: %d\n" +
                        "• 多选题: %d\n" +
                        "• 判断题: %d\n" +
                        "• 填空题: %d\n\n" +
                        "📝 内容统计\n" +
                        "• 无解析题目: %d\n" +
                        "• 无答案题目: %d",
                validQuestions.size(), singleChoiceCount, multipleChoiceCount,
                judgmentCount, fillBlankCount, noAnalysisCount, noAnswerCount);

        new AlertDialog.Builder(this)
                .setTitle("文档解析完成")
                .setMessage(message)
                .setPositiveButton("查看题库", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity.this, QuestionBankActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("继续上传", null)
                .show();

        // 同时显示Toast提示
        Toast.makeText(this, "成功导入 " + validQuestions.size() + " 道题目", Toast.LENGTH_SHORT).show();
    }
}
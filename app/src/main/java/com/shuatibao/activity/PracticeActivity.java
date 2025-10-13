package com.shuatibao.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.shuatibao.R;
import com.shuatibao.database.DatabaseHelper;
import com.shuatibao.model.ExamPaper;
import com.shuatibao.model.Question;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PracticeActivity extends AppCompatActivity {

    private TextView tvQuestionNumber, tvQuestionContent, tvQuestionType, tvResult, tvAnalysis, tvCorrectAnswer;
    private RadioGroup rgOptions;
    private LinearLayout llMultipleOptions, llResult, llJudgmentOptions, llFillBlankInput;
    private Button btnPrev, btnNext, btnSubmit;
    private EditText etFillBlank;

    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private DatabaseHelper databaseHelper;
    private String paperId;
    private boolean isAnswerSubmitted = false;

    // 新增：记录每道题的答题状态
    private Map<Integer, QuestionState> questionStates;

    private static final String TAG = "PracticeActivity";

    // 内部类：记录题目状态
    private static class QuestionState {
        List<String> userAnswers;
        boolean isSubmitted;
        boolean isCorrect;

        QuestionState(List<String> userAnswers, boolean isSubmitted, boolean isCorrect) {
            this.userAnswers = userAnswers;
            this.isSubmitted = isSubmitted;
            this.isCorrect = isCorrect;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        databaseHelper = new DatabaseHelper(this);

        // 初始化答题状态记录
        questionStates = new HashMap<>();

        initViews();
        loadPracticeData(); // 修改为新的数据加载方法
        displayQuestion();
    }

    private void initViews() {
        tvQuestionNumber = findViewById(R.id.tv_question_number);
        tvQuestionContent = findViewById(R.id.tv_question_content);
        tvQuestionType = findViewById(R.id.tv_question_type);
        tvResult = findViewById(R.id.tv_result);
        tvAnalysis = findViewById(R.id.tv_analysis);
        tvCorrectAnswer = findViewById(R.id.tv_correct_answer);

        rgOptions = findViewById(R.id.rg_options);
        llMultipleOptions = findViewById(R.id.ll_multiple_options);
        llResult = findViewById(R.id.ll_result);
        llJudgmentOptions = findViewById(R.id.ll_judgment_options);
        llFillBlankInput = findViewById(R.id.ll_fill_blank_input);
        etFillBlank = findViewById(R.id.et_fill_blank);

        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnSubmit = findViewById(R.id.btn_submit);

        // 设置返回按钮
        findViewById(R.id.iv_back).setOnClickListener(v -> {
            // 如果是错题本单个练习且答对了，设置结果码
            if (isSingleQuestionFromWrongBook() && isAnswerCorrectForCurrentQuestion()) {
                setResult(RESULT_OK);
            }
            finish();
        });

        // 按钮点击事件
        btnPrev.setOnClickListener(v -> showPreviousQuestion());
        btnNext.setOnClickListener(v -> showNextQuestion());

        // 修正提交按钮点击逻辑
        btnSubmit.setOnClickListener(v -> {
            checkAnswer();

            // 如果是单个题目模式且答对了，延迟显示完成对话框
            if (isSingleQuestionMode() && isAnswerCorrectForCurrentQuestion()) {
                new android.os.Handler().postDelayed(() -> {
                    showCompletionDialog();
                }, 1500); // 1.5秒后显示完成对话框
            }
        });

        // 填空题输入监听
        etFillBlank.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 当用户输入内容时，启用提交按钮
                if (s.length() > 0) {
                    btnSubmit.setEnabled(true);
                } else {
                    btnSubmit.setEnabled(false);
                }
            }
        });
    }

    // 在 PracticeActivity 中添加返回按钮处理
    @Override
    public void onBackPressed() {
        // 如果是错题本单个练习且答对了，设置结果码
        if (isSingleQuestionFromWrongBook() && isAnswerCorrectForCurrentQuestion()) {
            setResult(RESULT_OK);
        }
        super.onBackPressed();
    }

    // 在 PracticeActivity 的 loadPracticeData() 方法中，确保正确处理单个题目模式
    private void loadPracticeData() {
        try {
            // 检查是否是从错题本跳转的单个题目练习
            if (getIntent().hasExtra("question_list")) {
                // 从错题本跳转，使用传递的题目列表
                ArrayList<Question> questionList = (ArrayList<Question>) getIntent().getSerializableExtra("question_list");
                if (questionList != null && !questionList.isEmpty()) {
                    questions = questionList;
                    paperId = null; // 单个题目练习没有paperId
                    Log.d(TAG, "加载单个题目练习，题目数量: " + questions.size());
                    return;
                }
            }

            // 正常模式：从试卷加载
            paperId = getIntent().getStringExtra("paper_id");
            if (paperId != null) {
                loadPaperData();
            } else {
                Log.e(TAG, "没有有效的paperId或题目列表");
                Toast.makeText(this, "加载题目失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "加载练习数据失败: " + e.getMessage(), e);
            Toast.makeText(this, "加载题目失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadPaperData() {
        if (paperId == null) return;

        List<ExamPaper> allPapers = databaseHelper.getAllExamPapers();
        for (ExamPaper paper : allPapers) {
            if (paper.getId().equals(paperId)) {
                questions = paper.getQuestions();
                break;
            }
        }
    }

    private void displayQuestion() {
        if (questions == null || questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            return;
        }

        Question question = questions.get(currentQuestionIndex);

        // 重置UI状态
        resetUIState();

        // 更新题目信息
        tvQuestionNumber.setText("第 " + (currentQuestionIndex + 1) + " 题 / 共 " + questions.size() + " 题");
        tvQuestionContent.setText(question.getContent());
        tvQuestionType.setText(question.getTypeName());

        // 根据题型设置选项
        setupOptions(question);

        // 恢复答题状态（如果之前答过）
        restoreQuestionState();

        // 更新按钮状态
        updateButtonStates();
    }

    private void resetUIState() {
        // 隐藏所有选项区域
        hideAllOptionViews();

        // 重置结果区域
        llResult.setVisibility(View.GONE);
        tvCorrectAnswer.setVisibility(View.GONE);
        tvAnalysis.setVisibility(View.GONE);

        // 重置提交按钮
        btnSubmit.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);

        // 重置当前提交状态
        isAnswerSubmitted = false;
    }

    private void hideAllOptionViews() {
        rgOptions.setVisibility(View.GONE);
        llMultipleOptions.setVisibility(View.GONE);
        llJudgmentOptions.setVisibility(View.GONE);
        llFillBlankInput.setVisibility(View.GONE);

        // 清空选项
        rgOptions.clearCheck();
        rgOptions.removeAllViews();
        llMultipleOptions.removeAllViews();
        etFillBlank.setText("");
    }

    private void setupOptions(Question question) {
        if (question.getOptions() == null || question.getOptions().isEmpty()) {
            if ("judgment".equals(question.getType())) {
                setupJudgmentOptions();
            } else if ("fill_blank".equals(question.getType())) {
                setupFillBlankInput();
            }
            return;
        }

        if ("single_choice".equals(question.getType())) {
            setupSingleChoiceOptions(question);
        } else if ("multiple_choice".equals(question.getType())) {
            setupMultipleChoiceOptions(question);
        }
    }

    private void setupSingleChoiceOptions(Question question) {
        rgOptions.setVisibility(View.VISIBLE);

        for (int i = 0; i < question.getOptions().size(); i++) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(question.getOptions().get(i));
            radioButton.setId(View.generateViewId());
            radioButton.setTextSize(16);
            radioButton.setPadding(16, 16, 16, 16);
            radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    btnSubmit.setEnabled(true);
                }
            });
            rgOptions.addView(radioButton);
        }
    }

    private void setupMultipleChoiceOptions(Question question) {
        llMultipleOptions.setVisibility(View.VISIBLE);

        for (int i = 0; i < question.getOptions().size(); i++) {
            androidx.appcompat.widget.AppCompatCheckBox checkBox = new androidx.appcompat.widget.AppCompatCheckBox(this);
            checkBox.setText(question.getOptions().get(i));
            checkBox.setId(View.generateViewId());
            checkBox.setTextSize(16);
            checkBox.setPadding(16, 16, 16, 16);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);
            checkBox.setLayoutParams(params);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                boolean hasSelection = hasMultipleChoiceSelection();
                btnSubmit.setEnabled(hasSelection);
            });

            llMultipleOptions.addView(checkBox);
        }
    }

    private boolean hasMultipleChoiceSelection() {
        for (int j = 0; j < llMultipleOptions.getChildCount(); j++) {
            View child = llMultipleOptions.getChildAt(j);
            if (child instanceof androidx.appcompat.widget.AppCompatCheckBox) {
                androidx.appcompat.widget.AppCompatCheckBox cb = (androidx.appcompat.widget.AppCompatCheckBox) child;
                if (cb.isChecked()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setupJudgmentOptions() {
        llJudgmentOptions.setVisibility(View.VISIBLE);

        RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
        judgmentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            btnSubmit.setEnabled(checkedId != -1);
        });
    }

    private void setupFillBlankInput() {
        llFillBlankInput.setVisibility(View.VISIBLE);
    }

    // 新增：恢复题目状态
    private void restoreQuestionState() {
        QuestionState state = questionStates.get(currentQuestionIndex);
        if (state == null) {
            return;
        }

        Question question = questions.get(currentQuestionIndex);

        // 恢复用户答案
        restoreUserAnswers(question, state.userAnswers);

        // 如果已经提交过，显示结果
        if (state.isSubmitted) {
            isAnswerSubmitted = true;
            showQuestionResult(question, state.isCorrect, state.userAnswers);
        }
    }

    private void restoreUserAnswers(Question question, List<String> userAnswers) {
        if (userAnswers == null || userAnswers.isEmpty()) {
            return;
        }

        if ("single_choice".equals(question.getType())) {
            // 恢复单选题选择
            for (int i = 0; i < rgOptions.getChildCount(); i++) {
                RadioButton radioButton = (RadioButton) rgOptions.getChildAt(i);
                String optionText = radioButton.getText().toString();
                if (optionText.startsWith(userAnswers.get(0))) {
                    radioButton.setChecked(true);
                    btnSubmit.setEnabled(true);
                    break;
                }
            }

        } else if ("multiple_choice".equals(question.getType())) {
            // 恢复多选题选择
            for (int i = 0; i < llMultipleOptions.getChildCount(); i++) {
                View child = llMultipleOptions.getChildAt(i);
                if (child instanceof androidx.appcompat.widget.AppCompatCheckBox) {
                    androidx.appcompat.widget.AppCompatCheckBox checkBox = (androidx.appcompat.widget.AppCompatCheckBox) child;
                    String optionText = checkBox.getText().toString();
                    for (String userAnswer : userAnswers) {
                        if (optionText.startsWith(userAnswer)) {
                            checkBox.setChecked(true);
                            break;
                        }
                    }
                }
            }
            btnSubmit.setEnabled(hasMultipleChoiceSelection());

        } else if ("judgment".equals(question.getType())) {
            // 恢复判断题选择 - 修复方法
            restoreJudgmentSelection(userAnswers.get(0));

        } else if ("fill_blank".equals(question.getType())) {
            // 恢复填空题输入
            etFillBlank.setText(userAnswers.get(0));
            btnSubmit.setEnabled(true);
        }
    }

    // 新增：修复判断题选择恢复方法
    private void restoreJudgmentSelection(String userAnswer) {
        RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);

        // 清除当前选择
        judgmentGroup.clearCheck();

        // 根据用户答案设置选择
        if ("正确".equals(userAnswer)) {
            // 找到"正确"选项并选中
            for (int i = 0; i < judgmentGroup.getChildCount(); i++) {
                View child = judgmentGroup.getChildAt(i);
                if (child instanceof RadioButton) {
                    RadioButton radioButton = (RadioButton) child;
                    if ("正确".equals(radioButton.getText().toString())) {
                        radioButton.setChecked(true);
                        break;
                    }
                }
            }
        } else if ("错误".equals(userAnswer)) {
            // 找到"错误"选项并选中
            for (int i = 0; i < judgmentGroup.getChildCount(); i++) {
                View child = judgmentGroup.getChildAt(i);
                if (child instanceof RadioButton) {
                    RadioButton radioButton = (RadioButton) child;
                    if ("错误".equals(radioButton.getText().toString())) {
                        radioButton.setChecked(true);
                        break;
                    }
                }
            }
        }

        btnSubmit.setEnabled(true);
    }

    // 在 PracticeActivity.java 中修改 checkAnswer() 方法
    private void checkAnswer() {
        try {
            if (questions == null || currentQuestionIndex >= questions.size()) return;

            Question question = questions.get(currentQuestionIndex);
            List<String> userAnswers = getUserAnswers(question);
            List<String> correctAnswers = question.getAnswers();

            boolean isCorrect = isAnswerCorrect(userAnswers, correctAnswers, question.getType());

            // 保存答题状态
            questionStates.put(currentQuestionIndex, new QuestionState(userAnswers, true, isCorrect));
            isAnswerSubmitted = true;

            // 如果答错了，并且不是单个题目模式，添加到错题本
            if (!isCorrect && paperId != null && !isSingleQuestionMode()) {
                try {
                    // 确保题目有ID，如果没有则生成一个
                    if (question.getId() == null || question.getId().isEmpty()) {
                        question.setId(System.currentTimeMillis() + "_" + question.getContent().hashCode());
                    }

                    long result = databaseHelper.addWrongQuestion(question, paperId);
                    Log.d(TAG, "错题保存结果: " + result);

                } catch (Exception e) {
                    Log.e(TAG, "保存错题失败: " + e.getMessage(), e);
                    // 不抛出异常，继续执行
                }
            }

            // 新增：如果答对了，并且是从错题本跳转的单个题目模式，从错题本中移除
            if (isCorrect && isSingleQuestionFromWrongBook()) {
                try {
                    int deleteResult = databaseHelper.removeWrongQuestion(question.getId());
                    if (deleteResult > 0) {
                        Log.d(TAG, "答对题目，已从错题本中移除: " + question.getId());
                        // 设置标记，在返回错题本时刷新数据
                        setResult(RESULT_OK);
                    } else {
                        Log.d(TAG, "从错题本移除题目失败，可能题目不存在: " + question.getId());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "从错题本移除题目时发生异常: " + e.getMessage(), e);
                }
            }

            // 显示结果
            showQuestionResult(question, isCorrect, userAnswers);

            // 更新按钮状态
            updateButtonStates();

        } catch (Exception e) {
            Log.e(TAG, "检查答案时发生异常: " + e.getMessage(), e);
            Toast.makeText(this, "处理答案时发生错误", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查是否是从错题本跳转的单个题目练习
     */
    private boolean isSingleQuestionFromWrongBook() {
        return getIntent().getBooleanExtra("from_wrong_questions", false) &&
                isSingleQuestionMode();
    }

    /**
     * 检查是否为单个题目模式
     */
    private boolean isSingleQuestionMode() {
        return getIntent().getBooleanExtra("single_question_mode", false) ||
                getIntent().hasExtra("question_list");
    }

    // 修改 showCompletionDialog() 方法
    private void showCompletionDialog() {
        String message;
        String positiveButtonText = "确定";

        if (isSingleQuestionMode()) {
            Question currentQuestion = questions.get(currentQuestionIndex);
            boolean isCorrect = false;

            // 检查当前题目是否答对
            QuestionState state = questionStates.get(currentQuestionIndex);
            if (state != null) {
                isCorrect = state.isCorrect;
            }

            if (isCorrect && isSingleQuestionFromWrongBook()) {
                message = "回答正确！这道题已从错题本中移除。";
            } else if (isCorrect) {
                message = "回答正确！";
            } else {
                message = "回答错误，请继续努力！";
            }
        } else {
            message = "恭喜！你已经完成了所有题目的练习。";
            positiveButtonText = "完成";
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("练习完成")
                .setMessage(message)
                .setPositiveButton(positiveButtonText, (dialog, which) -> {
                    // 如果是错题本单个练习且答对了，设置结果码通知刷新
                    if (isSingleQuestionFromWrongBook() && isAnswerCorrectForCurrentQuestion()) {
                        setResult(RESULT_OK);
                    }
                    finish();
                });

        // 只有在正常练习模式下才显示"重新练习"按钮
        if (!isSingleQuestionMode()) {
            builder.setNegativeButton("重新练习", (dialog, which) -> {
                // 清空答题记录
                questionStates.clear();
                currentQuestionIndex = 0;
                displayQuestion();
            });
        }

        builder.setCancelable(false)
                .show();
    }

    /**
     * 检查当前题目是否答对
     */
    private boolean isAnswerCorrectForCurrentQuestion() {
        QuestionState state = questionStates.get(currentQuestionIndex);
        return state != null && state.isCorrect;
    }

    private void showQuestionResult(Question question, boolean isCorrect, List<String> userAnswers) {
        try {
            llResult.setVisibility(View.VISIBLE);
            btnSubmit.setVisibility(View.GONE);

            if (isCorrect) {
                tvResult.setText("回答正确！✓");
                tvResult.setTextColor(getResources().getColor(R.color.success));
                tvCorrectAnswer.setVisibility(View.GONE);
            } else {
                tvResult.setText("回答错误！✗");
                tvResult.setTextColor(getResources().getColor(R.color.error));

                // 显示正确答案
                StringBuilder correctAnswerText = new StringBuilder("正确答案：");
                if (question.getAnswers() != null && !question.getAnswers().isEmpty()) {
                    for (String answer : question.getAnswers()) {
                        correctAnswerText.append(answer);
                    }
                } else {
                    correctAnswerText.append("无答案");
                }
                tvCorrectAnswer.setText(correctAnswerText.toString());
                tvCorrectAnswer.setVisibility(View.VISIBLE);
            }

            // 显示解析 - 如果解析为空，显示友好提示
            if (question.getAnalysis() != null && !question.getAnalysis().trim().isEmpty()) {
                tvAnalysis.setText("解析：" + question.getAnalysis());
                tvAnalysis.setVisibility(View.VISIBLE);
            } else {
                tvAnalysis.setText("暂无解析");
                tvAnalysis.setVisibility(View.VISIBLE);
                tvAnalysis.setTextColor(getResources().getColor(R.color.text_secondary));
            }
        } catch (Exception e) {
            Log.e(TAG, "显示结果时发生异常: " + e.getMessage(), e);
        }
    }

    private List<String> getUserAnswers(Question question) {
        List<String> userAnswers = new java.util.ArrayList<>();

        try {
            if ("single_choice".equals(question.getType())) {
                int selectedId = rgOptions.getCheckedRadioButtonId();
                if (selectedId != -1) {
                    RadioButton selectedRadio = findViewById(selectedId);
                    String selectedText = selectedRadio.getText().toString();
                    if (selectedText.length() > 0) {
                        // 提取选项字母（A、B、C、D）
                        userAnswers.add(selectedText.substring(0, 1));
                    }
                }

            } else if ("multiple_choice".equals(question.getType())) {
                for (int i = 0; i < llMultipleOptions.getChildCount(); i++) {
                    View child = llMultipleOptions.getChildAt(i);
                    if (child instanceof androidx.appcompat.widget.AppCompatCheckBox) {
                        androidx.appcompat.widget.AppCompatCheckBox checkBox = (androidx.appcompat.widget.AppCompatCheckBox) child;
                        if (checkBox.isChecked()) {
                            String optionText = checkBox.getText().toString();
                            if (optionText.length() > 0) {
                                // 提取选项字母（A、B、C、D）
                                userAnswers.add(optionText.substring(0, 1));
                            }
                        }
                    }
                }

            } else if ("judgment".equals(question.getType())) {
                RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
                int selectedId = judgmentGroup.getCheckedRadioButtonId();
                if (selectedId != -1) {
                    RadioButton selectedRadio = findViewById(selectedId);
                    String selectedText = selectedRadio.getText().toString();
                    if ("正确".equals(selectedText)) {
                        userAnswers.add("正确");
                    } else if ("错误".equals(selectedText)) {
                        userAnswers.add("错误");
                    }
                }

            } else if ("fill_blank".equals(question.getType())) {
                String userInput = etFillBlank.getText().toString().trim();
                if (!userInput.isEmpty()) {
                    userAnswers.add(userInput);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取用户答案时发生异常: " + e.getMessage(), e);
        }

        return userAnswers;
    }

    private boolean isAnswerCorrect(List<String> userAnswers, List<String> correctAnswers, String type) {
        try {
            if (userAnswers.isEmpty() || correctAnswers == null || correctAnswers.isEmpty()) {
                return false;
            }

            if ("single_choice".equals(type)) {
                return userAnswers.size() == 1 && correctAnswers.size() == 1 &&
                        userAnswers.get(0).equals(correctAnswers.get(0));

            } else if ("multiple_choice".equals(type)) {
                if (userAnswers.size() != correctAnswers.size()) {
                    return false;
                }
                // 使用HashSet比较，忽略顺序
                return new java.util.HashSet<>(userAnswers).equals(new java.util.HashSet<>(correctAnswers));

            } else if ("judgment".equals(type)) {
                return userAnswers.size() == 1 && correctAnswers.size() == 1 &&
                        userAnswers.get(0).equals(correctAnswers.get(0));

            } else if ("fill_blank".equals(type)) {
                return !userAnswers.isEmpty() && !correctAnswers.isEmpty() &&
                        correctAnswers.get(0).toLowerCase().contains(userAnswers.get(0).toLowerCase());
            }
        } catch (Exception e) {
            Log.e(TAG, "判断答案正确性时发生异常: " + e.getMessage(), e);
        }

        return false;
    }

    private void showPreviousQuestion() {
        if (currentQuestionIndex > 0) {
            // 保存当前题目的未提交答案
            if (!isAnswerSubmitted) {
                saveCurrentAnswer();
            }

            currentQuestionIndex--;
            displayQuestion();
        }
    }

    // 修改 showNextQuestion() 方法
    private void showNextQuestion() {
        if (currentQuestionIndex < questions.size() - 1) {
            // 保存当前题目的未提交答案
            if (!isAnswerSubmitted) {
                saveCurrentAnswer();
            }

            currentQuestionIndex++;
            displayQuestion();
        } else if (currentQuestionIndex == questions.size() - 1 && isAnswerSubmitted) {
            // 如果是单个题目模式，直接显示完成对话框
            if (isSingleQuestionMode()) {
                showCompletionDialog();
            } else {
                // 正常模式显示完成对话框
                showCompletionDialog();
            }
        }
    }

    // 新增：保存当前未提交的答案
    private void saveCurrentAnswer() {
        try {
            Question question = questions.get(currentQuestionIndex);
            List<String> userAnswers = getUserAnswers(question);

            if (!userAnswers.isEmpty()) {
                questionStates.put(currentQuestionIndex, new QuestionState(userAnswers, false, false));
            }
        } catch (Exception e) {
            Log.e(TAG, "保存当前答案时发生异常: " + e.getMessage(), e);
        }
    }

    private void updateButtonStates() {
        btnPrev.setEnabled(currentQuestionIndex > 0);
        btnNext.setEnabled(currentQuestionIndex < questions.size() - 1 || isAnswerSubmitted);

        if (currentQuestionIndex < questions.size() - 1) {
            btnNext.setText("下一题");
        } else {
            btnNext.setText("完成");
        }
    }
}
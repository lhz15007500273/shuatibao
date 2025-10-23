package com.shuatibao.activity;

import android.app.Dialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.shuatibao.R;
import com.shuatibao.database.DatabaseHelper;
import com.shuatibao.model.ExamPaper;
import com.shuatibao.model.Question;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PracticeActivity extends AppCompatActivity {

    private TextView tvQuestionNumber, tvQuestionContent, tvQuestionType, tvResult, tvAnalysis, tvCorrectAnswer, tvTitle;
    private RadioGroup rgOptions;
    private LinearLayout llMultipleOptions, llResult, llJudgmentOptions, llFillBlankInput, llEssayInput;
    private Button btnPrev, btnNext, btnSubmit;
    private TextView btnAnswerSheet;
    private EditText etFillBlank, etEssay;

    private LinearLayout llMultipleBlanksContainer;
    private List<EditText> multipleBlankInputs;

    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private DatabaseHelper databaseHelper;
    private String paperId;
    private boolean isAnswerSubmitted = false;
    // 添加考试完成状态标记
    private boolean isExamCompleted = false;

    private Map<Integer, QuestionState> questionStates;

    private Dialog answerSheetDialog;
    private GridView gvQuestions;
    private TextView tvAnsweredCount, tvUnansweredCount, tvCorrectCount;
    private boolean isRestoringState = false;

    private static final String TAG = "PracticeActivity";

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

        questionStates = new HashMap<>();
        multipleBlankInputs = new ArrayList<>();

        initViews();
        loadPracticeData();
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }

        if (answerSheetDialog != null && answerSheetDialog.isShowing()) {
            answerSheetDialog.dismiss();
            answerSheetDialog = null;
        }

        super.onDestroy();
    }
    /**
     * 跳转到指定题目
     * @param position 题目索引
     */
    private void jumpToQuestion(int position) {
        if (!isValidQuestionIndex(position)) {
            Log.e(TAG, "跳转题目失败，无效的索引: " + position);
            return;
        }

        // 保存当前题目的答案（如果未提交）
        if (!isExamCompleted) {
            saveCurrentAnswer();
        }

        // 更新当前题目索引
        currentQuestionIndex = position;

        // 显示题目
        displayQuestion();

        // 如果答题卡对话框正在显示，关闭它
        if (answerSheetDialog != null && answerSheetDialog.isShowing()) {
            answerSheetDialog.dismiss();
        }

        Log.d(TAG, "跳转到第 " + (position + 1) + " 题");
    }
    private void initViews() {
        tvQuestionNumber = findViewById(R.id.tv_question_number);
        tvQuestionContent = findViewById(R.id.tv_question_content);
        tvQuestionType = findViewById(R.id.tv_question_type);
        tvResult = findViewById(R.id.tv_result);
        tvAnalysis = findViewById(R.id.tv_analysis);
        tvCorrectAnswer = findViewById(R.id.tv_correct_answer);
        tvTitle = findViewById(R.id.tv_title);

        rgOptions = findViewById(R.id.rg_options);
        llMultipleOptions = findViewById(R.id.ll_multiple_options);
        llResult = findViewById(R.id.ll_result);
        llJudgmentOptions = findViewById(R.id.ll_judgment_options);
        llFillBlankInput = findViewById(R.id.ll_fill_blank_input);
        llEssayInput = findViewById(R.id.ll_essay_input);
        etFillBlank = findViewById(R.id.et_fill_blank);
        etEssay = findViewById(R.id.et_essay);

        btnAnswerSheet = findViewById(R.id.btn_answer_sheet);

        llMultipleBlanksContainer = new LinearLayout(this);
        llMultipleBlanksContainer.setOrientation(LinearLayout.VERTICAL);

        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);

        View ivBack = findViewById(R.id.iv_back);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> showExitConfirmDialog());
        }

        if (btnAnswerSheet != null) {
            // 提交后仍可查看答题卡
            btnAnswerSheet.setOnClickListener(v -> {
                showAnswerSheet();
                // 若已提交，禁用答题卡的提交答案功能，但仍可查看题目
                if (isExamCompleted && gvQuestions != null) {
                    gvQuestions.setOnItemClickListener((parent, view, position, id) -> {
                        // 允许跳转到题目查看，但不允许提交新答案
                        jumpToQuestion(position);
                        // 可以添加提示说明只能查看不能修改
                        Toast.makeText(this, "已提交试卷，只能查看解析", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (isExamCompleted) {
                    Toast.makeText(this, "已提交过试卷，无法再次提交", Toast.LENGTH_SHORT).show();
                    return;
                }
                submitCurrentAnswer();
            });
        }

// 提交后仍可切换题目查看解析
        btnPrev.setOnClickListener(v -> {
            showPreviousQuestion();
            // 提交后显示查看模式提示
            if (isExamCompleted) {
                Toast.makeText(this, "查看模式：可查看题目解析", Toast.LENGTH_SHORT).show();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (isExamCompleted) {
                // 提交后允许自由切换所有题目查看解析
                showNextQuestion();
                Toast.makeText(this, "查看模式：可查看题目解析", Toast.LENGTH_SHORT).show();
                return;
            }

            // 未提交时正常逻辑
            if (currentQuestionIndex == questions.size() - 1) {
                submitAllAnswers();
            } else {
                showNextQuestion();
            }
        });

        setupRadioGroupListener();

        etFillBlank.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isRestoringState && !isExamCompleted) {
                    saveCurrentAnswer();
                }
            }
        });

        etEssay.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isRestoringState && !isExamCompleted) {
                    saveCurrentAnswer();
                }
            }
        });
    }

    private void setupRadioGroupListener() {
        if (rgOptions != null) {
            rgOptions.setOnCheckedChangeListener(null);

            rgOptions.post(() -> {
                rgOptions.setOnCheckedChangeListener((group, checkedId) -> {
                    if (checkedId != -1 && !isRestoringState && !isExamCompleted) {
                        Log.d(TAG, "RadioGroup选择改变: " + checkedId);
                        saveCurrentAnswer();
                    }
                });
            });
        }
    }

    @Override
    public void onBackPressed() {
        showExitConfirmDialog();
    }

    private void showExitConfirmDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("退出练习")
                .setMessage("确定要退出练习吗？您的答题进度将会保存。")
                .setPositiveButton("确定", (dialog, which) -> {
                    if (!isExamCompleted) {
                        saveCurrentAnswer();
                    }
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadPracticeData() {
        try {
            if (getIntent().hasExtra("question_list")) {
                ArrayList<Question> questionList = (ArrayList<Question>) getIntent().getSerializableExtra("question_list");
                if (questionList != null && !questionList.isEmpty()) {
                    questions = questionList;
                    paperId = null;
                    tvTitle.setText("错题练习");
                    Log.d(TAG, "加载单个题目练习，题目数量: " + questions.size());
                    displayQuestion();
                    return;
                }
            }

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
        if (paperId == null) {
            Log.e(TAG, "paperId为null，无法加载试卷数据");
            return;
        }

        new AsyncTask<Void, Void, List<Question>>() {
            @Override
            protected List<Question> doInBackground(Void... voids) {
                try {
                    List<ExamPaper> allPapers = databaseHelper.getAllExamPapers();
                    for (ExamPaper paper : allPapers) {
                        if (paper.getId().equals(paperId)) {
                            return paper.getQuestions();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "异步加载试卷数据失败: " + e.getMessage(), e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<Question> result) {
                if (result != null && !result.isEmpty()) {
                    questions = result;
                    tvTitle.setText(getPaperTitle(paperId));
                    displayQuestion();
                } else {
                    Log.e(TAG, "未找到对应的试卷或题目为空");
                    Toast.makeText(PracticeActivity.this, "加载题目失败", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }.execute();
    }

    private String getPaperTitle(String paperId) {
        try {
            List<ExamPaper> allPapers = databaseHelper.getAllExamPapers();
            for (ExamPaper paper : allPapers) {
                if (paper.getId().equals(paperId)) {
                    return paper.getTitle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取试卷标题失败: " + e.getMessage());
        }
        return "练习模式";
    }

    private boolean isValidQuestionIndex(int index) {
        return questions != null && index >= 0 && index < questions.size();
    }
    private void displayQuestion() {
        if (!isValidQuestionIndex(currentQuestionIndex)) {
            Log.e(TAG, "无效的题目索引: " + currentQuestionIndex);
            return;
        }

        Question question = questions.get(currentQuestionIndex);
        if (question == null) {
            Log.e(TAG, "题目对象为null，索引: " + currentQuestionIndex);
            return;
        }

        resetUIState();

        tvQuestionNumber.setText("第 " + (currentQuestionIndex + 1) + " 题 / 共 " + questions.size() + " 题");
        tvQuestionContent.setText(question.getContent() != null ? question.getContent() : "题目内容为空");
        tvQuestionType.setText(question.getTypeName() != null ? question.getTypeName() : "未知题型");

        setupOptions(question);
        restoreQuestionState();
        updateButtonStates(); // 确保调用更新按钮状态
        setupRadioGroupListener();

        // 如果考试已完成，禁用所有交互控件（但保持导航按钮可用）
        if (isExamCompleted) {
            disableAllInteractiveViews();
            // 确保导航按钮在提交后仍然可用
            btnPrev.setEnabled(currentQuestionIndex > 0);
            btnNext.setEnabled(currentQuestionIndex < questions.size() - 1);
        } else {
            // 未提交时，确保所有输入控件都是可用的
            enableAllInteractiveViews();
        }
    }
    // 启用所有交互控件
    private void enableAllInteractiveViews() {
        // 启用单选按钮组
        if (rgOptions != null) {
            for (int i = 0; i < rgOptions.getChildCount(); i++) {
                View child = rgOptions.getChildAt(i);
                if (child instanceof RadioButton) {
                    child.setEnabled(true);
                }
            }
        }

        // 启用复选框
        if (llMultipleOptions != null) {
            for (int i = 0; i < llMultipleOptions.getChildCount(); i++) {
                View child = llMultipleOptions.getChildAt(i);
                if (child instanceof CheckBox) {
                    child.setEnabled(true);
                }
            }
        }

        // 启用判断题选项
        RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
        if (judgmentGroup != null) {
            for (int i = 0; i < judgmentGroup.getChildCount(); i++) {
                View child = judgmentGroup.getChildAt(i);
                if (child instanceof RadioButton) {
                    child.setEnabled(true);
                }
            }
        }

        // 启用输入框
        if (etFillBlank != null) etFillBlank.setEnabled(true);
        if (etEssay != null) etEssay.setEnabled(true);

        // 启用多个填空题输入框
        for (EditText et : multipleBlankInputs) {
            if (et != null) et.setEnabled(true);
        }

        // 启用提交按钮
        if (btnSubmit != null) btnSubmit.setEnabled(true);
    }
    // 禁用所有交互控件（除了导航按钮）
    private void disableAllInteractiveViews() {
        // 禁用单选按钮组
        if (rgOptions != null) {
            for (int i = 0; i < rgOptions.getChildCount(); i++) {
                View child = rgOptions.getChildAt(i);
                if (child instanceof RadioButton) {
                    child.setEnabled(false);
                }
            }
        }

        // 禁用复选框
        if (llMultipleOptions != null) {
            for (int i = 0; i < llMultipleOptions.getChildCount(); i++) {
                View child = llMultipleOptions.getChildAt(i);
                if (child instanceof CheckBox) {
                    child.setEnabled(false);
                }
            }
        }

        // 禁用判断题选项
        RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
        if (judgmentGroup != null) {
            for (int i = 0; i < judgmentGroup.getChildCount(); i++) {
                View child = judgmentGroup.getChildAt(i);
                if (child instanceof RadioButton) {
                    child.setEnabled(false);
                }
            }
        }

        // 禁用输入框
        if (etFillBlank != null) etFillBlank.setEnabled(false);
        if (etEssay != null) etEssay.setEnabled(false);

        // 禁用多个填空题输入框
        for (EditText et : multipleBlankInputs) {
            if (et != null) et.setEnabled(false);
        }

        // 禁用提交按钮
        if (btnSubmit != null) btnSubmit.setEnabled(false);

        // 注意：不禁用 btnPrev 和 btnNext，提交后仍然允许查看题目
    }

    private void resetUIState() {
        isRestoringState = true;

        hideAllOptionViews();

        llResult.setVisibility(View.GONE);
        tvCorrectAnswer.setVisibility(View.GONE);
        tvAnalysis.setVisibility(View.GONE);

        isAnswerSubmitted = false;

        if (llMultipleBlanksContainer != null) {
            llMultipleBlanksContainer.removeAllViews();
        }
        multipleBlankInputs.clear();

        if (etFillBlank != null) {
            etFillBlank.setText("");
            etFillBlank.setVisibility(View.VISIBLE);
        }
        if (etEssay != null) {
            etEssay.setText("");
        }

        isRestoringState = false;
    }

    private void hideAllOptionViews() {
        if (rgOptions != null) {
            rgOptions.setOnCheckedChangeListener(null);
            rgOptions.setVisibility(View.GONE);
            rgOptions.clearCheck();
            rgOptions.removeAllViews();
        }

        if (llMultipleOptions != null) {
            llMultipleOptions.setVisibility(View.GONE);
            llMultipleOptions.removeAllViews();
        }

        if (llJudgmentOptions != null) {
            llJudgmentOptions.setVisibility(View.GONE);
            RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
            if (judgmentGroup != null) {
                judgmentGroup.clearCheck();
            }
        }

        if (llFillBlankInput != null) {
            llFillBlankInput.setVisibility(View.GONE);
        }

        if (llEssayInput != null) {
            llEssayInput.setVisibility(View.GONE);
        }
    }

    private void setupOptions(Question question) {
        if (question == null) {
            Log.e(TAG, "setupOptions: question为null");
            return;
        }

        String questionType = question.getType();
        if (questionType == null) {
            Log.e(TAG, "题目类型为null");
            return;
        }

        if (question.getOptions() == null || question.getOptions().isEmpty()) {
            switch (questionType) {
                case "judgment":
                    setupJudgmentOptions();
                    break;
                case "fill_blank":
                    if (question.getAnswers() != null && question.getAnswers().size() > 1) {
                        setupMultipleBlanksInput(question);
                    } else {
                        setupFillBlankInput();
                    }
                    break;
                case "essay":
                    setupEssayInput();
                    break;
                default:
                    Log.w(TAG, "未知的题目类型: " + questionType);
            }
            return;
        }

        switch (questionType) {
            case "single_choice":
                setupSingleChoiceOptions(question);
                break;
            case "multiple_choice":
                setupMultipleChoiceOptions(question);
                break;
            default:
                Log.w(TAG, "不支持的题目类型: " + questionType);
        }
    }

    private void setupSingleChoiceOptions(Question question) {
        if (rgOptions == null) return;

        rgOptions.setVisibility(View.VISIBLE);

        List<String> options = question.getOptions();
        if (options == null) return;

        for (int i = 0; i < options.size(); i++) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(options.get(i));
            radioButton.setId(View.generateViewId());
            radioButton.setTextSize(16);
            radioButton.setPadding(16, 16, 16, 16);

            radioButton.setOnClickListener(v -> {
                if (!isRestoringState && !isExamCompleted) {
                    Log.d(TAG, "RadioButton点击: " + radioButton.getText());
                    saveCurrentAnswer();
                }
            });

            rgOptions.addView(radioButton);
        }
    }

    private void setupMultipleChoiceOptions(Question question) {
        if (llMultipleOptions == null) return;

        llMultipleOptions.setVisibility(View.VISIBLE);

        List<String> options = question.getOptions();
        if (options == null) return;

        for (int i = 0; i < options.size(); i++) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(options.get(i));
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
                if (!isRestoringState && !isExamCompleted) {
                    saveCurrentAnswer();
                }
            });

            llMultipleOptions.addView(checkBox);
        }
    }

    private void setupJudgmentOptions() {
        if (llJudgmentOptions == null) return;

        llJudgmentOptions.setVisibility(View.VISIBLE);

        RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
        if (judgmentGroup != null) {
            judgmentGroup.removeAllViews();

            RadioButton rbCorrect = new RadioButton(this);
            rbCorrect.setText("正确");
            rbCorrect.setId(View.generateViewId());
            rbCorrect.setTextSize(16);
            rbCorrect.setPadding(16, 16, 16, 16);

            RadioButton rbWrong = new RadioButton(this);
            rbWrong.setText("错误");
            rbWrong.setId(View.generateViewId());
            rbWrong.setTextSize(16);
            rbWrong.setPadding(16, 16, 16, 16);

            judgmentGroup.addView(rbCorrect);
            judgmentGroup.addView(rbWrong);

            rbCorrect.setOnClickListener(v -> {
                if (!isRestoringState && !isExamCompleted) {
                    saveCurrentAnswer();
                }
            });

            rbWrong.setOnClickListener(v -> {
                if (!isRestoringState && !isExamCompleted) {
                    saveCurrentAnswer();
                }
            });
        }
    }

    private void setupFillBlankInput() {
        if (llFillBlankInput != null) {
            llFillBlankInput.setVisibility(View.VISIBLE);
            if (etFillBlank != null) {
                etFillBlank.setEnabled(!isExamCompleted);
            }
        }
    }

    private void setupEssayInput() {
        if (llEssayInput != null) {
            llEssayInput.setVisibility(View.VISIBLE);
            if (etEssay != null) {
                etEssay.setEnabled(!isExamCompleted);
            }
        }
    }
    private void setupMultipleBlanksInput(Question question) {
        if (llFillBlankInput == null) return;

        llFillBlankInput.setVisibility(View.VISIBLE);

        if (etFillBlank != null) {
            etFillBlank.setVisibility(View.GONE);
        }

        if (llMultipleBlanksContainer.getParent() == null) {
            llFillBlankInput.addView(llMultipleBlanksContainer);
        }

        List<String> correctAnswers = question.getAnswers();
        if (correctAnswers != null) {
            for (int i = 0; i < correctAnswers.size(); i++) {
                addBlankInputField(i + 1);
            }
        }

        // 根据提交状态设置输入框的启用/禁用状态
        if (isExamCompleted) {
            for (EditText et : multipleBlankInputs) {
                if (et != null) et.setEnabled(false);
            }
        } else {
            for (EditText et : multipleBlankInputs) {
                if (et != null) et.setEnabled(true);
            }
        }
    }

    private void addBlankInputField(int blankNumber) {
        if (llMultipleBlanksContainer == null) return;

        LinearLayout blankLayout = new LinearLayout(this);
        blankLayout.setOrientation(LinearLayout.VERTICAL);
        blankLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView tvBlankLabel = new TextView(this);
        tvBlankLabel.setText("第" + blankNumber + "空：");
        tvBlankLabel.setTextColor(getResources().getColor(R.color.text_primary));
        tvBlankLabel.setTextSize(14);
        tvBlankLabel.setPadding(0, 8, 0, 4);
        blankLayout.addView(tvBlankLabel);

        EditText etBlankAnswer = new EditText(this);
        etBlankAnswer.setHint("请输入第" + blankNumber + "空答案");
        etBlankAnswer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        etBlankAnswer.setPadding(16, 12, 16, 12);
        etBlankAnswer.setTextSize(16);
        etBlankAnswer.setMinHeight(dpToPx(48));
        etBlankAnswer.setBackgroundResource(android.R.drawable.edit_text);
        etBlankAnswer.setTag("blank_" + blankNumber);

        // 根据提交状态设置初始启用/禁用状态
        etBlankAnswer.setEnabled(!isExamCompleted);

        multipleBlankInputs.add(etBlankAnswer);

        etBlankAnswer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isRestoringState && !isExamCompleted) {
                    saveCurrentAnswer();
                }
            }
        });

        blankLayout.addView(etBlankAnswer);
        llMultipleBlanksContainer.addView(blankLayout);
    }

    private void restoreQuestionState() {
        if (questionStates == null) return;

        QuestionState state = questionStates.get(currentQuestionIndex);
        if (state == null) {
            return;
        }

        Question question = questions.get(currentQuestionIndex);
        if (question == null) return;

        isRestoringState = true;

        restoreUserAnswers(question, state.userAnswers);

        if (state.isSubmitted) {
            isAnswerSubmitted = true;
            showQuestionResult(question, state.isCorrect, state.userAnswers);
        }

        isRestoringState = false;

        Log.d(TAG, "恢复第 " + (currentQuestionIndex + 1) + " 题状态: " + state.userAnswers);
    }

    private void restoreUserAnswers(Question question, List<String> userAnswers) {
        if (userAnswers == null || userAnswers.isEmpty()) {
            return;
        }

        String questionType = question.getType();
        if (questionType == null) return;

        try {
            switch (questionType) {
                case "single_choice":
                    restoreSingleChoiceAnswers(userAnswers);
                    break;
                case "multiple_choice":
                    restoreMultipleChoiceAnswers(userAnswers);
                    break;
                case "judgment":
                    restoreJudgmentSelection(userAnswers.get(0));
                    break;
                case "fill_blank":
                    if (question.getAnswers() != null && question.getAnswers().size() > 1) {
                        restoreMultipleBlanksAnswers(userAnswers);
                    } else {
                        if (etFillBlank != null) {
                            etFillBlank.setText(userAnswers.get(0));
                        }
                    }
                    break;
                case "essay":
                    if (etEssay != null) {
                        etEssay.setText(userAnswers.get(0));
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "恢复用户答案失败: " + e.getMessage(), e);
        }
    }

    private void restoreSingleChoiceAnswers(List<String> userAnswers) {
        if (rgOptions == null || userAnswers.isEmpty()) return;

        for (int i = 0; i < rgOptions.getChildCount(); i++) {
            View child = rgOptions.getChildAt(i);
            if (child instanceof RadioButton) {
                RadioButton radioButton = (RadioButton) child;
                String optionText = radioButton.getText().toString();
                if (!optionText.isEmpty() && optionText.startsWith(userAnswers.get(0))) {
                    radioButton.setChecked(true);
                    Log.d(TAG, "恢复单选题选择: " + optionText);
                    break;
                }
            }
        }
    }

    private void restoreMultipleChoiceAnswers(List<String> userAnswers) {
        if (llMultipleOptions == null) return;

        for (int i = 0; i < llMultipleOptions.getChildCount(); i++) {
            View child = llMultipleOptions.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) child;
                String optionText = checkBox.getText().toString();
                for (String userAnswer : userAnswers) {
                    if (!optionText.isEmpty() && optionText.startsWith(userAnswer)) {
                        checkBox.setChecked(true);
                        break;
                    }
                }
            }
        }
    }

    private void restoreMultipleBlanksAnswers(List<String> userAnswers) {
        for (int i = 0; i < multipleBlankInputs.size() && i < userAnswers.size(); i++) {
            EditText etAnswer = multipleBlankInputs.get(i);
            if (etAnswer != null) {
                etAnswer.setText(userAnswers.get(i));
            }
        }
    }

    private void restoreJudgmentSelection(String userAnswer) {
        RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
        if (judgmentGroup == null) return;

        for (int i = 0; i < judgmentGroup.getChildCount(); i++) {
            View child = judgmentGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                RadioButton radioButton = (RadioButton) child;
                if (userAnswer.equals(radioButton.getText().toString())) {
                    radioButton.setChecked(true);
                    break;
                }
            }
        }
    }

    private void submitCurrentAnswer() {
        if (!isValidQuestionIndex(currentQuestionIndex) || isExamCompleted) return;

        saveCurrentAnswer();
        checkSingleAnswer(currentQuestionIndex);

        Question question = questions.get(currentQuestionIndex);
        QuestionState state = questionStates.get(currentQuestionIndex);
        if (state != null) {
            showQuestionResult(question, state.isCorrect, state.userAnswers);
        }

        Toast.makeText(this, "答案已提交", Toast.LENGTH_SHORT).show();
    }

    private void checkSingleAnswer(int questionIndex) {
        try {
            if (!isValidQuestionIndex(questionIndex)) return;

            Question question = questions.get(questionIndex);
            QuestionState state = questionStates.get(questionIndex);

            if (state == null) {
                Log.w(TAG, "题目状态为空，索引: " + questionIndex);
                return;
            }

            List<String> userAnswers = state.userAnswers;
            List<String> correctAnswers = question.getAnswers();

            Log.d(TAG, "检查第 " + (questionIndex + 1) + " 题答案 - 用户答案: " + userAnswers + ", 正确答案: " + correctAnswers);

            boolean isCorrect = isAnswerCorrect(userAnswers, correctAnswers, question.getType());

            // 更新状态为已提交
            questionStates.put(questionIndex, new QuestionState(userAnswers, true, isCorrect));

            if (!isCorrect && paperId != null && !isSingleQuestionMode()) {
                try {
                    if (question.getId() == null || question.getId().isEmpty()) {
                        question.setId(System.currentTimeMillis() + "_" + (question.getContent() != null ? question.getContent().hashCode() : ""));
                    }
                    databaseHelper.addWrongQuestion(question, paperId);
                } catch (Exception e) {
                    Log.e(TAG, "保存错题失败: " + e.getMessage(), e);
                }
            }

            if (isCorrect && isSingleQuestionFromWrongBook()) {
                try {
                    databaseHelper.removeWrongQuestion(question.getId());
                    setResult(RESULT_OK);
                } catch (Exception e) {
                    Log.e(TAG, "从错题本移除题目时发生异常: " + e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "检查答案时发生异常: " + e.getMessage(), e);
        }
    }

    private boolean isSingleQuestionFromWrongBook() {
        return getIntent().getBooleanExtra("from_wrong_questions", false) &&
                isSingleQuestionMode();
    }

    private boolean isSingleQuestionMode() {
        return getIntent().getBooleanExtra("single_question_mode", false) ||
                getIntent().hasExtra("question_list");
    }

    private void showQuestionResult(Question question, boolean isCorrect, List<String> userAnswers) {
        try {
            if (llResult == null) return;

            llResult.setVisibility(View.VISIBLE);

            if (isCorrect) {
                tvResult.setText("回答正确！✓");
                tvResult.setTextColor(getResources().getColor(R.color.success));
                if (tvCorrectAnswer != null) {
                    tvCorrectAnswer.setVisibility(View.GONE);
                }
            } else {
                tvResult.setText("回答错误！✗");
                tvResult.setTextColor(getResources().getColor(R.color.error));

                if (tvCorrectAnswer != null) {
                    StringBuilder correctAnswerText = new StringBuilder("正确答案：");
                    if (question.getAnswers() != null && !question.getAnswers().isEmpty()) {
                        for (int i = 0; i < question.getAnswers().size(); i++) {
                            if (i > 0) correctAnswerText.append("，");
                            correctAnswerText.append(question.getAnswers().get(i));
                        }
                    } else {
                        correctAnswerText.append("无答案");
                    }
                    tvCorrectAnswer.setText(correctAnswerText.toString());
                    tvCorrectAnswer.setVisibility(View.VISIBLE);
                }
            }

            if (tvAnalysis != null) {
                if (question.getAnalysis() != null && !question.getAnalysis().trim().isEmpty()) {
                    tvAnalysis.setText("解析：" + question.getAnalysis());
                    tvAnalysis.setVisibility(View.VISIBLE);
                    tvAnalysis.setTextColor(getResources().getColor(R.color.text_primary));
                } else {
                    tvAnalysis.setText("暂无解析");
                    tvAnalysis.setVisibility(View.VISIBLE);
                    tvAnalysis.setTextColor(getResources().getColor(R.color.text_secondary));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "显示结果时发生异常: " + e.getMessage(), e);
        }
    }

    private List<String> getUserAnswers(Question question) {
        List<String> userAnswers = new ArrayList<>();

        try {
            if (question == null) return userAnswers;

            String questionType = question.getType();
            if (questionType == null) return userAnswers;

            switch (questionType) {
                case "single_choice":
                    userAnswers = getSingleChoiceAnswers();
                    break;
                case "multiple_choice":
                    userAnswers = getMultipleChoiceAnswers();
                    break;
                case "judgment":
                    userAnswers = getJudgmentAnswers();
                    break;
                case "fill_blank":
                    if (question.getAnswers() != null && question.getAnswers().size() > 1) {
                        userAnswers = getMultipleBlanksAnswers();
                    } else {
                        userAnswers = getSingleFillBlankAnswer();
                    }
                    break;
                case "essay":
                    userAnswers = getEssayAnswer();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取用户答案时发生异常: " + e.getMessage(), e);
        }

        return userAnswers;
    }

    private List<String> getSingleChoiceAnswers() {
        List<String> answers = new ArrayList<>();
        if (rgOptions == null) return answers;

        int selectedId = rgOptions.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton selectedRadio = findViewById(selectedId);
            if (selectedRadio != null) {
                String selectedText = selectedRadio.getText().toString();
                if (!selectedText.isEmpty()) {
                    answers.add(selectedText.substring(0, 1));
                    Log.d(TAG, "获取单选题答案: " + selectedText.substring(0, 1));
                }
            }
        }
        return answers;
    }

    private List<String> getMultipleChoiceAnswers() {
        List<String> answers = new ArrayList<>();
        if (llMultipleOptions == null) return answers;

        for (int i = 0; i < llMultipleOptions.getChildCount(); i++) {
            View child = llMultipleOptions.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) child;
                if (checkBox.isChecked()) {
                    String optionText = checkBox.getText().toString();
                    if (!optionText.isEmpty()) {
                        answers.add(optionText.substring(0, 1));
                    }
                }
            }
        }
        return answers;
    }

    private List<String> getJudgmentAnswers() {
        List<String> answers = new ArrayList<>();
        RadioGroup judgmentGroup = findViewById(R.id.rg_judgment);
        if (judgmentGroup == null) return answers;

        int selectedId = judgmentGroup.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton selectedRadio = findViewById(selectedId);
            if (selectedRadio != null) {
                answers.add(selectedRadio.getText().toString());
            }
        }
        return answers;
    }

    private List<String> getSingleFillBlankAnswer() {
        List<String> answers = new ArrayList<>();
        if (etFillBlank != null) {
            String userInput = etFillBlank.getText().toString().trim();
            if (!userInput.isEmpty()) {
                answers.add(userInput);
            }
        }
        return answers;
    }

    private List<String> getEssayAnswer() {
        List<String> answers = new ArrayList<>();
        if (etEssay != null) {
            String userInput = etEssay.getText().toString().trim();
            if (!userInput.isEmpty()) {
                answers.add(userInput);
            }
        }
        return answers;
    }

    private List<String> getMultipleBlanksAnswers() {
        List<String> answers = new ArrayList<>();
        for (EditText etAnswer : multipleBlankInputs) {
            if (etAnswer != null) {
                String answer = etAnswer.getText().toString().trim();
                answers.add(answer);
            }
        }
        return answers;
    }

    private boolean isAnswerCorrect(List<String> userAnswers, List<String> correctAnswers, String type) {
        try {
            if (userAnswers == null || userAnswers.isEmpty() ||
                    correctAnswers == null || correctAnswers.isEmpty()) {
                return false;
            }

            if (type == null) return false;

            switch (type) {
                case "single_choice":
                    return userAnswers.size() == 1 && correctAnswers.size() == 1 &&
                            userAnswers.get(0).equals(correctAnswers.get(0));

                case "multiple_choice":
                    if (userAnswers.size() != correctAnswers.size()) {
                        return false;
                    }
                    return new HashSet<>(userAnswers).equals(new HashSet<>(correctAnswers));

                case "judgment":
                    return userAnswers.size() == 1 && correctAnswers.size() == 1 &&
                            userAnswers.get(0).equals(correctAnswers.get(0));

                case "fill_blank":
                    if (userAnswers.size() != correctAnswers.size()) {
                        return false;
                    }
                    for (int i = 0; i < userAnswers.size(); i++) {
                        String userAnswer = normalizeAnswer(userAnswers.get(i));
                        String correctAnswer = normalizeAnswer(correctAnswers.get(i));
                        if (!userAnswer.equals(correctAnswer)) {
                            return false;
                        }
                    }
                    return true;

                case "essay":
                    return !userAnswers.isEmpty() && !userAnswers.get(0).isEmpty();

                default:
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "判断答案正确性时发生异常: " + e.getMessage(), e);
        }
        return false;
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) return "";
        return answer.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private void showPreviousQuestion() {
        if (!isValidQuestionIndex(currentQuestionIndex)) return;

        if (currentQuestionIndex > 0) {
            saveCurrentAnswer();
            currentQuestionIndex--;
            displayQuestion();
        }
    }

    private void showNextQuestion() {
        if (!isValidQuestionIndex(currentQuestionIndex)) return;

        if (currentQuestionIndex < questions.size() - 1) {
            saveCurrentAnswer();
            currentQuestionIndex++;
            displayQuestion();
        }
    }

    private void saveCurrentAnswer() {
        try {
            if (!isValidQuestionIndex(currentQuestionIndex) || isExamCompleted) return;

            Question question = questions.get(currentQuestionIndex);
            List<String> userAnswers = getUserAnswers(question);

            QuestionState currentState = questionStates.get(currentQuestionIndex);
            if (currentState != null && currentState.isSubmitted) {
                questionStates.put(currentQuestionIndex, new QuestionState(userAnswers, true, currentState.isCorrect));
            } else {
                questionStates.put(currentQuestionIndex, new QuestionState(userAnswers, false, false));
            }

            Log.d(TAG, "保存第 " + (currentQuestionIndex + 1) + " 题答案: " + userAnswers);
        } catch (Exception e) {
            Log.e(TAG, "保存当前答案时发生异常: " + e.getMessage(), e);
        }
    }

    private void updateButtonStates() {
        if (btnPrev == null || btnNext == null) return;

        // 提交后仍然允许使用导航按钮查看题目
        if (isExamCompleted) {
            btnPrev.setEnabled(currentQuestionIndex > 0);
            btnNext.setEnabled(currentQuestionIndex < questions.size() - 1);
            return;
        }

        // 未提交时的正常逻辑
        btnPrev.setEnabled(currentQuestionIndex > 0);

        if (currentQuestionIndex < questions.size() - 1) {
            btnNext.setText("下一题");
            btnNext.setEnabled(true);
        } else {
            btnNext.setText("交卷");
            btnNext.setEnabled(true);
        }
    }

    private void showAnswerSheet() {
        if (answerSheetDialog == null) {
            answerSheetDialog = new Dialog(this);
            answerSheetDialog.setContentView(R.layout.dialog_answer_sheet);
            if (answerSheetDialog.getWindow() != null) {
                answerSheetDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            answerSheetDialog.setCancelable(true);

            gvQuestions = answerSheetDialog.findViewById(R.id.gv_questions);
            tvAnsweredCount = answerSheetDialog.findViewById(R.id.tv_answered_count);
            tvUnansweredCount = answerSheetDialog.findViewById(R.id.tv_unanswered_count);
            tvCorrectCount = answerSheetDialog.findViewById(R.id.tv_correct_count);

            Button btnClose = answerSheetDialog.findViewById(R.id.btn_close_sheet);
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> answerSheetDialog.dismiss());
            }

            Button btnSubmitAll = answerSheetDialog.findViewById(R.id.btn_submit_all);
            if (btnSubmitAll != null) {
                btnSubmitAll.setOnClickListener(v -> {
                    if (isExamCompleted) {
                        Toast.makeText(this, "已提交过试卷，无法再次提交", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitAllAnswers();
                    answerSheetDialog.dismiss();
                });
            }

            Button btnFinish = answerSheetDialog.findViewById(R.id.btn_finish_practice);
            if (btnFinish != null) {
                btnFinish.setOnClickListener(v -> {
                    if (isExamCompleted) { // 已完成练习时才允许结束
                        answerSheetDialog.dismiss();
                        startCountdownAndExit();
                    } else {
                        Toast.makeText(PracticeActivity.this, "请先提交试卷再结束练习", Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }

        updateAnswerSheetData();
        answerSheetDialog.show();
    }

    // 修复：确保提交所有题目的答案
    private void submitAllAnswers() {
        if (isExamCompleted) {
            Toast.makeText(this, "已提交过试卷，无法再次提交", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存当前题目的答案
        saveCurrentAnswer();

        // 遍历所有题目，确保每道题都有答案记录
        for (int i = 0; i < questions.size(); i++) {
            // 如果题目没有答案记录，创建一个
            if (!questionStates.containsKey(i)) {
                Question question = questions.get(i);
                List<String> userAnswers = getUserAnswers(question);
                questionStates.put(i, new QuestionState(userAnswers, false, false));
            }
        }

        // 检查是否有空题
        List<Integer> emptyQuestionIndices = new ArrayList<>();
        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                Question question = questions.get(i);
                QuestionState state = questionStates.get(i);
                List<String> userAnswers = new ArrayList<>();

                if (state != null) {
                    userAnswers = state.userAnswers;
                } else {
                    userAnswers = getUserAnswers(question);
                }

                // 判断答案是否为空
                boolean isEmpty = true;
                if (userAnswers != null && !userAnswers.isEmpty()) {
                    isEmpty = false;
                    for (String answer : userAnswers) {
                        if (answer == null || answer.trim().isEmpty()) {
                            isEmpty = true;
                            break;
                        }
                    }
                }

                if (isEmpty) {
                    emptyQuestionIndices.add(i);
                }
            }
        }

        // 如果有空题，提示用户
        if (!emptyQuestionIndices.isEmpty()) {
            StringBuilder message = new StringBuilder("您有以下题目未作答：\n");
            for (int index : emptyQuestionIndices) {
                message.append("第").append(index + 1).append("题\n");
            }
            message.append("\n确定要提交吗？未作答题目将按错误处理。");

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("发现未作答题目")
                    .setMessage(message.toString())
                    .setPositiveButton("继续提交", (dialog, which) -> {
                        processAllAnswers();
                        // 标记考试已完成
                        isExamCompleted = true;
                        showCompletionDialog();
                    })
                    .setNegativeButton("返回作答", null)
                    .show();
            return;
        }

        // 没有空题，直接处理答案
        processAllAnswers();
        // 标记考试已完成
        isExamCompleted = true;
        showCompletionDialog();
    }

    // 处理所有答案的判断和评分
    private void processAllAnswers() {
        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                QuestionState state = questionStates.get(i);
                if (state != null && !state.isSubmitted) {
                    checkSingleAnswer(i);
                } else if (state == null) {
                    List<String> emptyAnswers = new ArrayList<>();
                    questionStates.put(i, new QuestionState(emptyAnswers, true, false));
                }
            }
        }
    }

    private void updateAnswerSheetData() {
        if (questions == null) return;

        int answeredCount = 0;
        int correctCount = 0;
        int savedCount = 0;

        for (int i = 0; i < questions.size(); i++) {
            QuestionState state = questionStates.get(i);
            if (state != null) {
                if (state.isSubmitted) {
                    answeredCount++;
                    if (state.isCorrect) {
                        correctCount++;
                    }
                } else if (state.userAnswers != null && !state.userAnswers.isEmpty()) {
                    savedCount++;
                }
            }
        }

        int unansweredCount = questions.size() - answeredCount - savedCount;

        if (tvAnsweredCount != null) {
            tvAnsweredCount.setText(String.valueOf(answeredCount));
        }
        if (tvUnansweredCount != null) {
            tvUnansweredCount.setText(String.valueOf(unansweredCount));
        }
        if (tvCorrectCount != null) {
            tvCorrectCount.setText(String.valueOf(correctCount));
        }

        if (gvQuestions != null) {
            AnswerSheetAdapter adapter = new AnswerSheetAdapter(questions, questionStates, currentQuestionIndex);
            gvQuestions.setAdapter(adapter);

            gvQuestions.setOnItemClickListener((parent, view, position, id) -> {
                if (isValidQuestionIndex(position) && !isExamCompleted) {
                    currentQuestionIndex = position;
                    displayQuestion();
                    if (answerSheetDialog != null) {
                        answerSheetDialog.dismiss();
                    }
                }
            });
        }
    }

    private class AnswerSheetAdapter extends BaseAdapter {
        private List<Question> questions;
        private Map<Integer, QuestionState> questionStates;
        private int currentIndex;

        public AnswerSheetAdapter(List<Question> questions, Map<Integer, QuestionState> questionStates, int currentIndex) {
            this.questions = questions != null ? questions : new ArrayList<>();
            this.questionStates = questionStates != null ? questionStates : new HashMap<>();
            this.currentIndex = currentIndex;
        }

        @Override
        public int getCount() {
            return questions.size();
        }

        @Override
        public Object getItem(int position) {
            return questions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_answer_sheet, parent, false);
                holder = new ViewHolder();
                holder.tvIndex = convertView.findViewById(R.id.tv_question_index);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.tvIndex.setText(String.valueOf(position + 1));

            QuestionState state = questionStates.get(position);
            int backgroundResId;

            if (position == currentIndex) {
                backgroundResId = R.drawable.bg_answer_status_current;
            } else if (state != null && state.isSubmitted) {
                if (state.isCorrect) {
                    backgroundResId = R.drawable.bg_answer_status_correct;
                } else {
                    backgroundResId = R.drawable.bg_answer_status_wrong;
                }
            } else if (state != null && state.userAnswers != null && !state.userAnswers.isEmpty()) {
                backgroundResId = R.drawable.bg_answer_status_saved;
            } else {
                backgroundResId = R.drawable.bg_answer_status_default;
            }

            holder.tvIndex.setBackgroundResource(backgroundResId);

            return convertView;
        }

        class ViewHolder {
            TextView tvIndex;
        }
    }

    private void showCompletionDialog() {
        if (questions == null || questions.isEmpty()) return;

        int totalQuestions = questions.size();
        int correctCount = 0;
        int answeredCount = 0;

        for (int i = 0; i < totalQuestions; i++) {
            QuestionState state = questionStates.get(i);
            if (state != null && state.isSubmitted) {
                answeredCount++;
                if (state.isCorrect) {
                    correctCount++;
                }
            }
        }

        double score = totalQuestions > 0 ? (double) correctCount / totalQuestions * 100 : 0;
        String scoreText = String.format("%.1f", score);

        String message;
        String positiveButtonText = "确定";

        if (isSingleQuestionMode()) {
            if (!isValidQuestionIndex(currentQuestionIndex)) return;

            boolean isCorrect = false;
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
        }  else {
            message = String.format("练习完成！\n\n总题数：%d\n已答题：%d\n正确题数：%d\n得分：%s分",
                    totalQuestions, answeredCount, correctCount, scoreText);
            positiveButtonText = "完成";
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("练习完成")
                .setMessage(message)
                .setPositiveButton(positiveButtonText, (dialog, which) -> {
                    // 仅关闭对话框，不执行页面跳转
                    if (isSingleQuestionFromWrongBook() && isAnswerCorrectForCurrentQuestion()) {
                        setResult(RESULT_OK);
                    }
                    // 刷新当前题目显示，应用禁用状态
                    displayQuestion();
                });

        if (!isSingleQuestionMode()) {
            // 第一个次要按钮：重新练习（NegativeButton）
            builder.setNegativeButton("重新练习", (dialog, which) -> {
                isExamCompleted = false;
                questionStates.clear();
                currentQuestionIndex = 0;
                displayQuestion();
            });

            // 第二个次要按钮：查看错题（NeutralButton）- 仅当有错题时显示
            if (correctCount < answeredCount) {
                builder.setNeutralButton("查看错题", (dialog, which) -> {
                    showWrongQuestionsReview();
                });
            }

            // 第三个按钮：结束练习（PositiveButton 或新增一个按钮，此处用 PositiveButton 替换原“完成”）
            // 注意：AlertDialog 最多支持 3 个按钮（Positive/Negative/Neutral），需调整按钮优先级
            builder.setPositiveButton("结束练习", (dialog, which) -> {
                startCountdownAndExit(); // 点击直接倒计时退出
            });

            // 原“完成”按钮改为“返回查看”（如果需要保留）
            builder.setNegativeButton("返回查看", (dialog, which) -> {
                displayQuestion(); // 仅关闭对话框，返回题目页面
            });
        }



        builder.setCancelable(false).show();
    }

    /**
     * 开始倒计时并退出练习页面
     */
    private void startCountdownAndExit() {
        AlertDialog countdownDialog = new AlertDialog.Builder(this)
                .setTitle("结束练习")
                .setMessage("5秒后自动退出练习页面...")
                .setCancelable(false)
                .create();

        countdownDialog.show();

        // 修复：确保找到 messageView（避免空指针）
        TextView messageView = (TextView) countdownDialog.findViewById(android.R.id.message);
        final int[] countdown = {5};

        CountDownTimer countDownTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdown[0]--;
                if (messageView != null) {
                    messageView.setText(countdown[0] + "秒后自动退出练习页面...");
                }
            }

            @Override
            public void onFinish() {
                countdownDialog.dismiss();
                finishWithResult(); // 退出页面
            }
        };

        countDownTimer.start();
    }

    // 确保 finishWithResult() 正确执行
    private void finishWithResult() {
        if (isSingleQuestionFromWrongBook() && isAnswerCorrectForCurrentQuestion()) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish(); // 关键：关闭当前 Activity
        overridePendingTransition(0, 0); // 可选：关闭过渡动画，提升体验
    }



    private void showWrongQuestionsReview() {
        if (questions == null) return;

        List<Question> wrongQuestions = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            QuestionState state = questionStates.get(i);
            if (state != null && state.isSubmitted && !state.isCorrect) {
                wrongQuestions.add(questions.get(i));
            }
        }

        if (wrongQuestions.isEmpty()) {
            Toast.makeText(this, "没有错题", Toast.LENGTH_SHORT).show();
            return;
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("错题回顾")
                .setMessage("共有 " + wrongQuestions.size() + " 道错题，是否重新练习？")
                .setPositiveButton("重新练习", (dialog, which) -> {
                    // 重置考试状态
                    isExamCompleted = false;
                    questions = wrongQuestions;
                    questionStates.clear();
                    currentQuestionIndex = 0;
                    displayQuestion();
                    Toast.makeText(this, "开始错题练习", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isAnswerCorrectForCurrentQuestion() {
        if (!isValidQuestionIndex(currentQuestionIndex)) return false;
        QuestionState state = questionStates.get(currentQuestionIndex);
        return state != null && state.isCorrect;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
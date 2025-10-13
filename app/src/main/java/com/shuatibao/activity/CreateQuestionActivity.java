package com.shuatibao.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.shuatibao.R;
import com.shuatibao.database.DatabaseHelper;
import com.shuatibao.model.ExamPaper;
import com.shuatibao.model.Question;

import java.util.ArrayList;
import java.util.List;

public class CreateQuestionActivity extends AppCompatActivity {

    private LinearLayout llFirstStep, llQuestionCreation;
    private EditText etPaperName;
    private Button btnNextStep, btnContinueCreate, btnFinishCreate;
    private TextView tvPaperTitle, tvQuestionNumber, tvCurrentQuestionType;
    private Spinner spQuestionType;
    private LinearLayout llQuestionContainer;

    private DatabaseHelper databaseHelper;
    private String currentPaperName;
    private List<Question> createdQuestions;
    private int currentQuestionIndex = 1;

    // 题型选项
    private String[] questionTypes = {"单选题", "多选题", "判断题", "填空题", "简答题"};
    private String[] questionTypeValues = {"single_choice", "multiple_choice", "judgment", "fill_blank", "essay"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_question);

        initViews();
        initData();
        setupSpinner();
        setupClickListeners();
    }

    private void initViews() {
        llFirstStep = findViewById(R.id.ll_first_step);
        llQuestionCreation = findViewById(R.id.ll_question_creation);
        etPaperName = findViewById(R.id.et_paper_name);
        btnNextStep = findViewById(R.id.btn_next_step);
        btnContinueCreate = findViewById(R.id.btn_continue_create);
        btnFinishCreate = findViewById(R.id.btn_finish_create);
        tvPaperTitle = findViewById(R.id.tv_paper_title);
        tvQuestionNumber = findViewById(R.id.tv_question_number);
        tvCurrentQuestionType = findViewById(R.id.tv_current_question_type);
        spQuestionType = findViewById(R.id.sp_question_type);
        llQuestionContainer = findViewById(R.id.ll_question_container);

        databaseHelper = new DatabaseHelper(this);
        createdQuestions = new ArrayList<>();
    }

    private void initData() {
        // 设置默认试卷名称
        String defaultName = "新建试题_" + System.currentTimeMillis();
        etPaperName.setText(defaultName);
        etPaperName.setSelection(defaultName.length());
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, questionTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spQuestionType.setAdapter(adapter);

        // 题型选择监听
        spQuestionType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedType = questionTypeValues[position];
                tvCurrentQuestionType.setText(questionTypes[position]);
                updateQuestionTemplate(selectedType);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 默认显示单选题模板
                updateQuestionTemplate("single_choice");
            }
        });
    }

    private void setupClickListeners() {
        // 下一步按钮
        btnNextStep.setOnClickListener(v -> {
            currentPaperName = etPaperName.getText().toString().trim();
            if (TextUtils.isEmpty(currentPaperName)) {
                Toast.makeText(this, "请输入试题名称", Toast.LENGTH_SHORT).show();
                return;
            }

            // 切换到题目创建步骤
            llFirstStep.setVisibility(View.GONE);
            llQuestionCreation.setVisibility(View.VISIBLE);
            tvPaperTitle.setText(currentPaperName);
            updateQuestionNumber();

            // 初始化第一个题目的模板
            updateQuestionTemplate("single_choice");
        });

        // 继续创建下一题
        btnContinueCreate.setOnClickListener(v -> {
            if (saveCurrentQuestion()) {
                currentQuestionIndex++;
                updateQuestionNumber();
                clearQuestionInputs();
                Toast.makeText(this, "第" + (currentQuestionIndex-1) + "题保存成功", Toast.LENGTH_SHORT).show();
            }
        });

        // 完成创建
        btnFinishCreate.setOnClickListener(v -> {
            if (saveCurrentQuestion()) {
                createExamPaper();
            }
        });
    }

    private void updateQuestionNumber() {
        tvQuestionNumber.setText("第" + currentQuestionIndex + "题");
    }

    private void updateQuestionTemplate(String questionType) {
        llQuestionContainer.removeAllViews();

        switch (questionType) {
            case "single_choice":
                createSingleChoiceTemplate();
                break;
            case "multiple_choice":
                createMultipleChoiceTemplate();
                break;
            case "judgment":
                createJudgmentTemplate();
                break;
            case "fill_blank":
                createFillBlankTemplate();
                break;
            case "essay":
                createEssayTemplate();
                break;
        }
    }

    // 单选题模板
    private void createSingleChoiceTemplate() {
        // 题目内容
        EditText etContent = createEditText("请输入试题内容");
        llQuestionContainer.addView(etContent);

        // 选项标题
        TextView tvOptionsTitle = new TextView(this);
        tvOptionsTitle.setText("选项");
        tvOptionsTitle.setTextColor(getColor(R.color.text_primary));
        tvOptionsTitle.setTextSize(16);
        tvOptionsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOptionsTitle.setPadding(0, 16, 0, 8);
        llQuestionContainer.addView(tvOptionsTitle);

        // 选项A - 格式：A. [输入内容]
        LinearLayout optionALayout = createOptionLayout("A");
        llQuestionContainer.addView(optionALayout);

        // 选项B - 格式：B. [输入内容]
        LinearLayout optionBLayout = createOptionLayout("B");
        llQuestionContainer.addView(optionBLayout);

        // 选项C - 格式：C. [输入内容]
        LinearLayout optionCLayout = createOptionLayout("C");
        llQuestionContainer.addView(optionCLayout);

        // 选项D - 格式：D. [输入内容]
        LinearLayout optionDLayout = createOptionLayout("D");
        llQuestionContainer.addView(optionDLayout);

        // 添加选项按钮
        Button btnAddOption = new Button(this);
        btnAddOption.setText("+添加选项");
        btnAddOption.setOnClickListener(v -> addNewOption());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.bottomMargin = dpToPx(16);
        btnAddOption.setLayoutParams(btnParams);
        llQuestionContainer.addView(btnAddOption);

        // 正确答案
        TextView tvCorrectAnswer = new TextView(this);
        tvCorrectAnswer.setText("正确答案");
        tvCorrectAnswer.setTextColor(getColor(R.color.text_primary));
        tvCorrectAnswer.setTextSize(16);
        tvCorrectAnswer.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCorrectAnswer.setPadding(0, 16, 0, 8);
        llQuestionContainer.addView(tvCorrectAnswer);

        Spinner answerSpinner = new Spinner(this);
        String[] answers = {"A", "B", "C", "D"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, answers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        answerSpinner.setAdapter(adapter);
        answerSpinner.setTag("answer_spinner");
        llQuestionContainer.addView(answerSpinner);

        // 解析
        EditText etAnalysis = createEditText("题目解析（可选）");
        llQuestionContainer.addView(etAnalysis);
    }

    // 多选题模板
    private void createMultipleChoiceTemplate() {
        // 题目内容
        EditText etContent = createEditText("请输入题目内容");
        llQuestionContainer.addView(etContent);

        // 选项标题
        TextView tvOptionsTitle = new TextView(this);
        tvOptionsTitle.setText("选项");
        tvOptionsTitle.setTextColor(getColor(R.color.text_primary));
        tvOptionsTitle.setTextSize(16);
        tvOptionsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOptionsTitle.setPadding(0, 16, 0, 8);
        llQuestionContainer.addView(tvOptionsTitle);

        // 选项A - 格式：A. [输入内容]
        LinearLayout optionALayout = createOptionLayout("A");
        llQuestionContainer.addView(optionALayout);

        // 选项B - 格式：B. [输入内容]
        LinearLayout optionBLayout = createOptionLayout("B");
        llQuestionContainer.addView(optionBLayout);

        // 选项C - 格式：C. [输入内容]
        LinearLayout optionCLayout = createOptionLayout("C");
        llQuestionContainer.addView(optionCLayout);

        // 选项D - 格式：D. [输入内容]
        LinearLayout optionDLayout = createOptionLayout("D");
        llQuestionContainer.addView(optionDLayout);

        // 添加选项按钮
        Button btnAddOption = new Button(this);
        btnAddOption.setText("+添加选项");
        btnAddOption.setOnClickListener(v -> addNewOption());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.bottomMargin = dpToPx(16);
        btnAddOption.setLayoutParams(btnParams);
        llQuestionContainer.addView(btnAddOption);

        // 正确答案提示
        TextView tvCorrectAnswerHint = new TextView(this);
        tvCorrectAnswerHint.setText("正确答案（可多选，用逗号分隔，如：A,B,C）");
        tvCorrectAnswerHint.setTextColor(getColor(R.color.text_primary));
        tvCorrectAnswerHint.setTextSize(16);
        tvCorrectAnswerHint.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCorrectAnswerHint.setPadding(0, 16, 0, 8);
        llQuestionContainer.addView(tvCorrectAnswerHint);

        EditText etCorrectAnswers = createEditText("例如：A,B");
        etCorrectAnswers.setTag("correct_answers");
        llQuestionContainer.addView(etCorrectAnswers);

        // 解析
        EditText etAnalysis = createEditText("题目解析（可选）");
        llQuestionContainer.addView(etAnalysis);
    }

    // 创建选项布局（格式：A. [输入框]）
    private LinearLayout createOptionLayout(String optionLabel) {
        LinearLayout optionLayout = new LinearLayout(this);
        optionLayout.setOrientation(LinearLayout.HORIZONTAL);
        optionLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // 选项标签（A. B. C. D. 等）
        TextView tvOptionLabel = new TextView(this);
        tvOptionLabel.setText(optionLabel + ".");
        tvOptionLabel.setTextColor(getColor(R.color.text_primary));
        tvOptionLabel.setTextSize(16);
        tvOptionLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOptionLabel.setWidth(dpToPx(30));
        optionLayout.addView(tvOptionLabel);

        // 选项内容输入框
        EditText etOption = new EditText(this);
        etOption.setHint("请输入选项内容");
        etOption.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        etOption.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12));
        etOption.setTextSize(16);
        etOption.setBackgroundResource(android.R.drawable.edit_text);
        etOption.setTag("option_" + optionLabel); // 设置tag以便识别
        optionLayout.addView(etOption);

        return optionLayout;
    }

    // 添加新选项
    private void addNewOption() {
        // 计算当前选项数量
        int optionCount = 0;
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getChildCount() == 2) { // 选项布局有两个子view：标签和输入框
                    optionCount++;
                }
            }
        }

        // 生成新的选项标签（E, F, G...）
        String newOptionLabel = String.valueOf((char) ('A' + optionCount));

        // 在添加按钮前插入新选项
        int addButtonIndex = findAddButtonIndex();
        if (addButtonIndex != -1) {
            LinearLayout newOptionLayout = createOptionLayout(newOptionLabel);
            llQuestionContainer.addView(newOptionLayout, addButtonIndex);

            // 更新答案选择器的选项
            updateAnswerSpinnerOptions();
        }
    }

    // 查找添加按钮的索引位置
    private int findAddButtonIndex() {
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof Button) {
                Button button = (Button) child;
                if ("+添加选项".equals(button.getText().toString())) {
                    return i;
                }
            }
        }
        return -1;
    }

    // 更新答案选择器的选项
    private void updateAnswerSpinnerOptions() {
        // 收集所有选项标签
        List<String> optionLabels = new ArrayList<>();
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getChildCount() == 2) {
                    TextView labelView = (TextView) layout.getChildAt(0);
                    String label = labelView.getText().toString().replace(".", "");
                    optionLabels.add(label);
                }
            }
        }

        // 更新单选题的答案选择器
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof Spinner && "answer_spinner".equals(child.getTag())) {
                Spinner spinner = (Spinner) child;
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, optionLabels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                break;
            }
        }
    }

    // 判断题模板（保持不变）
    private void createJudgmentTemplate() {
        // 题目内容
        EditText etContent = createEditText("请输入题目内容");
        llQuestionContainer.addView(etContent);

        // 正确答案
        TextView tvCorrectAnswer = new TextView(this);
        tvCorrectAnswer.setText("正确答案");
        tvCorrectAnswer.setTextColor(getColor(R.color.text_primary));
        tvCorrectAnswer.setTextSize(16);
        tvCorrectAnswer.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCorrectAnswer.setPadding(0, 16, 0, 8);
        llQuestionContainer.addView(tvCorrectAnswer);

        Spinner answerSpinner = new Spinner(this);
        String[] answers = {"正确", "错误"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, answers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        answerSpinner.setAdapter(adapter);
        answerSpinner.setTag("answer_spinner");
        llQuestionContainer.addView(answerSpinner);

        // 解析
        EditText etAnalysis = createEditText("题目解析（可选）");
        llQuestionContainer.addView(etAnalysis);
    }

    // 填空题模板（保持不变）
    private void createFillBlankTemplate() {
        // 题目内容（包含下划线表示填空位置）
        TextView tvContentHint = new TextView(this);
        tvContentHint.setText("题目内容（用下划线_表示填空位置，例如：中国的首都是______）");
        tvContentHint.setTextColor(getColor(R.color.text_secondary));
        tvContentHint.setTextSize(14);
        tvContentHint.setPadding(0, 0, 0, 8);
        llQuestionContainer.addView(tvContentHint);

        EditText etContent = createEditText("请输入题目内容，用_表示填空");
        llQuestionContainer.addView(etContent);

        // 正确答案
        EditText etCorrectAnswer = createEditText("正确答案");
        etCorrectAnswer.setTag("correct_answer");
        llQuestionContainer.addView(etCorrectAnswer);

        // 解析
        EditText etAnalysis = createEditText("题目解析（可选）");
        llQuestionContainer.addView(etAnalysis);
    }

    // 简答题模板（保持不变）
    private void createEssayTemplate() {
        // 题目内容
        EditText etContent = createEditText("请输入题目内容");
        llQuestionContainer.addView(etContent);

        // 参考答案
        TextView tvAnswerHint = new TextView(this);
        tvAnswerHint.setText("参考答案");
        tvAnswerHint.setTextColor(getColor(R.color.text_primary));
        tvAnswerHint.setTextSize(16);
        tvAnswerHint.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAnswerHint.setPadding(0, 16, 0, 8);
        llQuestionContainer.addView(tvAnswerHint);

        EditText etReferenceAnswer = createEditText("请输入参考答案");
        etReferenceAnswer.setMinLines(3);
        etReferenceAnswer.setTag("reference_answer");
        llQuestionContainer.addView(etReferenceAnswer);

        // 解析
        EditText etAnalysis = createEditText("题目解析（可选）");
        etAnalysis.setMinLines(2);
        llQuestionContainer.addView(etAnalysis);
    }

    private EditText createEditText(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setBackgroundResource(android.R.drawable.edit_text);
        editText.setPadding(16, 12, 16, 12);
        editText.setTextSize(16);
        editText.setMinHeight(dpToPx(48));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dpToPx(12);
        editText.setLayoutParams(params);

        return editText;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private boolean saveCurrentQuestion() {
        String questionType = questionTypeValues[spQuestionType.getSelectedItemPosition()];
        Question question = createQuestionFromInputs(questionType);

        if (question != null) {
            createdQuestions.add(question);
            return true;
        }
        return false;
    }

    private Question createQuestionFromInputs(String questionType) {
        // 获取题目内容（第一个EditText）
        EditText etContent = (EditText) llQuestionContainer.getChildAt(0);
        String content = etContent.getText().toString().trim();

        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "请输入题目内容", Toast.LENGTH_SHORT).show();
            return null;
        }

        List<String> options = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        String analysis = "";

        switch (questionType) {
            case "single_choice":
                // 获取所有选项（格式：A. 内容）
                options = collectOptions();
                if (options.isEmpty()) {
                    Toast.makeText(this, "请完善所有选项", Toast.LENGTH_SHORT).show();
                    return null;
                }
                // 获取正确答案
                Spinner answerSpinner = findAnswerSpinner();
                if (answerSpinner != null) {
                    String selectedAnswer = (String) answerSpinner.getSelectedItem();
                    answers.add(selectedAnswer);
                }
                // 获取解析（最后一个EditText）
                analysis = getAnalysisFromLastEditText();
                break;

            case "multiple_choice":
                // 获取所有选项（格式：A. 内容）
                options = collectOptions();
                if (options.isEmpty()) {
                    Toast.makeText(this, "请完善所有选项", Toast.LENGTH_SHORT).show();
                    return null;
                }
                // 获取正确答案
                EditText etCorrectAnswers = findCorrectAnswersEditText();
                if (etCorrectAnswers != null) {
                    String correctAnswers = etCorrectAnswers.getText().toString().trim();
                    if (TextUtils.isEmpty(correctAnswers)) {
                        Toast.makeText(this, "请输入正确答案", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                    String[] answerArray = correctAnswers.split(",");
                    for (String answer : answerArray) {
                        answers.add(answer.trim());
                    }
                }
                // 获取解析（最后一个EditText）
                analysis = getAnalysisFromLastEditText();
                break;

            case "judgment":
                // 获取正确答案
                Spinner judgmentSpinner = findAnswerSpinner();
                if (judgmentSpinner != null) {
                    String judgmentAnswer = (String) judgmentSpinner.getSelectedItem();
                    answers.add(judgmentAnswer);
                }
                // 获取解析（最后一个EditText）
                analysis = getAnalysisFromLastEditText();
                break;

            case "fill_blank":
                // 获取正确答案
                EditText etCorrectAnswer = findCorrectAnswerEditText();
                if (etCorrectAnswer != null) {
                    String fillAnswer = etCorrectAnswer.getText().toString().trim();
                    if (TextUtils.isEmpty(fillAnswer)) {
                        Toast.makeText(this, "请输入正确答案", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                    answers.add(fillAnswer);
                }
                // 获取解析（最后一个EditText）
                analysis = getAnalysisFromLastEditText();
                break;

            case "essay":
                // 获取参考答案
                EditText etReferenceAnswer = findReferenceAnswerEditText();
                if (etReferenceAnswer != null) {
                    String referenceAnswer = etReferenceAnswer.getText().toString().trim();
                    if (TextUtils.isEmpty(referenceAnswer)) {
                        Toast.makeText(this, "请输入参考答案", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                    answers.add(referenceAnswer);
                }
                // 获取解析（最后一个EditText）
                analysis = getAnalysisFromLastEditText();
                break;
        }

        // 创建题目对象
        Question question = new Question(questionType, content, options, answers, analysis);
        question.setId(System.currentTimeMillis() + "_custom_" + currentQuestionIndex);

        return question;
    }

    // 收集所有选项（格式：A. 内容）
    private List<String> collectOptions() {
        List<String> options = new ArrayList<>();
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getChildCount() == 2) {
                    TextView labelView = (TextView) layout.getChildAt(0);
                    EditText contentView = (EditText) layout.getChildAt(1);

                    String label = labelView.getText().toString().replace(".", "");
                    String content = contentView.getText().toString().trim();

                    if (!TextUtils.isEmpty(content)) {
                        options.add(label + ". " + content);
                    }
                }
            }
        }
        return options;
    }

    // 查找答案选择器
    private Spinner findAnswerSpinner() {
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof Spinner && "answer_spinner".equals(child.getTag())) {
                return (Spinner) child;
            }
        }
        return null;
    }

    // 查找多选题答案输入框
    private EditText findCorrectAnswersEditText() {
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof EditText && "correct_answers".equals(child.getTag())) {
                return (EditText) child;
            }
        }
        return null;
    }

    // 查找填空题答案输入框
    private EditText findCorrectAnswerEditText() {
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof EditText && "correct_answer".equals(child.getTag())) {
                return (EditText) child;
            }
        }
        return null;
    }

    // 查找简答题参考答案输入框
    private EditText findReferenceAnswerEditText() {
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof EditText && "reference_answer".equals(child.getTag())) {
                return (EditText) child;
            }
        }
        return null;
    }

    // 从最后一个EditText获取解析
    private String getAnalysisFromLastEditText() {
        for (int i = llQuestionContainer.getChildCount() - 1; i >= 0; i--) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof EditText) {
                EditText etAnalysis = (EditText) child;
                // 排除答案输入框
                if (!"correct_answers".equals(etAnalysis.getTag()) &&
                        !"correct_answer".equals(etAnalysis.getTag()) &&
                        !"reference_answer".equals(etAnalysis.getTag())) {
                    return etAnalysis.getText().toString().trim();
                }
            }
        }
        return "";
    }

    private void clearQuestionInputs() {
        for (int i = 0; i < llQuestionContainer.getChildCount(); i++) {
            View child = llQuestionContainer.getChildAt(i);
            if (child instanceof EditText) {
                ((EditText) child).setText("");
            } else if (child instanceof Spinner) {
                ((Spinner) child).setSelection(0);
            }
        }
    }

    private void createExamPaper() {
        if (createdQuestions.isEmpty()) {
            Toast.makeText(this, "请至少创建一道题目", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建试卷
        ExamPaper examPaper = new ExamPaper(currentPaperName, createdQuestions, "手动创建");
        examPaper.setId("custom_paper_" + System.currentTimeMillis());

        long result = databaseHelper.saveExamPaper(examPaper);

        if (result != -1) {
            Toast.makeText(this, "成功创建作业，共" + createdQuestions.size() + "道题目", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "创建失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }
}
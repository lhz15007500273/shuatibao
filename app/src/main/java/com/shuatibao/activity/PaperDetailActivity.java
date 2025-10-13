package com.shuatibao.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.shuatibao.R;
import com.shuatibao.adapter.QuestionAdapter;
import com.shuatibao.database.DatabaseHelper;
import com.shuatibao.model.ExamPaper;
import com.shuatibao.model.Question;

import java.util.ArrayList;
import java.util.List;

public class PaperDetailActivity extends AppCompatActivity {

    private TextView tvPaperTitle, tvQuestionCount, tvSourceFile;
    private RecyclerView rvQuestions;
    private QuestionAdapter adapter;
    private List<Question> questions = new ArrayList<>();
    private DatabaseHelper databaseHelper;
    private String paperId;

    private static final String TAG = "PaperDetailActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paper_detail);

        databaseHelper = new DatabaseHelper(this);
        paperId = getIntent().getStringExtra("paper_id");

        Log.d(TAG, "paperId: " + paperId);

        initViews();
        loadPaperData();
    }

    private void initViews() {
        tvPaperTitle = findViewById(R.id.tv_paper_title);
        tvQuestionCount = findViewById(R.id.tv_question_count);
        tvSourceFile = findViewById(R.id.tv_source_file);
        rvQuestions = findViewById(R.id.rv_questions);

        // 设置返回按钮
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        // 设置题目列表
        adapter = new QuestionAdapter(questions, false); // false表示不显示答案和解析
        rvQuestions.setLayoutManager(new LinearLayoutManager(this));
        rvQuestions.setAdapter(adapter);

        // 添加开始练习按钮
        Button btnStartPractice = findViewById(R.id.btn_start_practice);
        btnStartPractice.setOnClickListener(v -> startPractice());
    }

    private void startPractice() {
        Intent intent = new Intent(this, PracticeActivity.class);
        intent.putExtra("paper_id", paperId);
        startActivity(intent);
    }

    private void loadPaperData() {
        if (paperId == null) {
            Log.e(TAG, "paperId is null");
            return;
        }

        // 从数据库加载试卷数据
        List<ExamPaper> allPapers = databaseHelper.getAllExamPapers();
        Log.d(TAG, "Total papers in database: " + allPapers.size());

        for (ExamPaper paper : allPapers) {
            Log.d(TAG, "Paper ID: " + paper.getId() + ", Title: " + paper.getTitle() + ", Question count: " + paper.getQuestionCount());
        }

        boolean found = false;
        for (ExamPaper paper : allPapers) {
            if (paper.getId().equals(paperId)) {
                Log.d(TAG, "Found matching paper: " + paper.getTitle() + " with " + paper.getQuestionCount() + " questions");
                if (paper.getQuestions() != null) {
                    Log.d(TAG, "Questions list size: " + paper.getQuestions().size());
                    for (Question q : paper.getQuestions()) {
                        Log.d(TAG, "Question: " + q.getContent());
                    }
                } else {
                    Log.d(TAG, "Questions list is null");
                }
                displayPaperData(paper);
                found = true;
                break;
            }
        }

        if (!found) {
            Log.e(TAG, "No paper found with ID: " + paperId);
        }
    }

    private void displayPaperData(ExamPaper paper) {
        tvPaperTitle.setText(paper.getTitle());
        tvQuestionCount.setText("共 " + paper.getQuestionCount() + " 题");
        tvSourceFile.setText("来源: " + paper.getSourceFile());

        if (paper.getQuestions() != null) {
            questions.clear();
            questions.addAll(paper.getQuestions());
            Log.d(TAG, "Displaying " + questions.size() + " questions in adapter");
            adapter.notifyDataSetChanged();
        } else {
            Log.e(TAG, "Paper questions is null");
        }
    }
}
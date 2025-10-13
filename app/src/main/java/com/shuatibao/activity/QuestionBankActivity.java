package com.shuatibao.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.shuatibao.R;
import com.shuatibao.adapter.ExamPaperAdapter;
import com.shuatibao.database.DatabaseHelper;
import com.shuatibao.model.ExamPaper;

import java.util.ArrayList;
import java.util.List;

public class QuestionBankActivity extends AppCompatActivity {

    private RecyclerView rvExamPapers;
    private TextView tvEmpty;
    private ExamPaperAdapter adapter;
    private List<ExamPaper> examPapers = new ArrayList<>();
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question_bank);

        databaseHelper = new DatabaseHelper(this);

        // 初始化示例数据（只在第一次运行时创建）
        databaseHelper.initializeSampleData();

        initViews();
        setupRecyclerView();
        loadExamPapers();
    }

    private void initViews() {
        rvExamPapers = findViewById(R.id.rv_exam_papers);
        tvEmpty = findViewById(R.id.tv_empty);

        // 设置返回按钮
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        // 添加刷新按钮
        findViewById(R.id.iv_refresh).setOnClickListener(v -> refreshData());
    }

    private void setupRecyclerView() {
        adapter = new ExamPaperAdapter(examPapers, this);

        // 设置点击事件
        adapter.setOnItemClickListener((paper, position) -> {
            // 点击试卷项，跳转到试卷详情页面
            Intent intent = new Intent(this, PaperDetailActivity.class);
            intent.putExtra("paper_id", paper.getId());
            startActivity(intent);
        });

        // 设置长按事件
        adapter.setOnItemLongClickListener((paper, position) -> {
            showDeleteDialog(paper, position);
            return true;
        });

        rvExamPapers.setLayoutManager(new LinearLayoutManager(this));
        rvExamPapers.setAdapter(adapter);
    }

    private void showDeleteDialog(ExamPaper paper, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("删除试卷")
                .setMessage("确定要删除试卷《" + paper.getTitle() + "》吗？\n此操作不可恢复。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteExamPaper(paper, position);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteExamPaper(ExamPaper paper, int position) {
        // 从数据库中删除
        databaseHelper.deleteExamPaper(paper.getId());

        // 从列表中移除
        examPapers.remove(position);
        adapter.notifyItemRemoved(position);

        // 更新空状态显示
        updateEmptyView();

        // 显示删除成功提示
        showToast("试卷删除成功");
    }

    private void loadExamPapers() {
        // 从数据库加载试卷数据
        examPapers.clear();
        examPapers.addAll(databaseHelper.getAllExamPapers());

        updateEmptyView();
        adapter.updateData(examPapers);
    }

    private void refreshData() {
        loadExamPapers();
        if (examPapers.isEmpty()) {
            // 如果还是空的，重新初始化示例数据
            databaseHelper.initializeSampleData();
            loadExamPapers();
        }
    }

    private void updateEmptyView() {
        if (examPapers.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvExamPapers.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvExamPapers.setVisibility(View.VISIBLE);
        }
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新数据
        loadExamPapers();
    }
}
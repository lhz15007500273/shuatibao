package com.shuatibao.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shuatibao.R;
import com.shuatibao.database.DatabaseHelper;
import com.shuatibao.model.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WrongQuestionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty, tvStats;
    private TextView tvClearAll; // 改为 TextView
    private Button btnSortByTime, btnSortByCount;
    private EditText etSearch;
    private ImageView ivClearSearch;
    private WrongQuestionAdapter adapter;
    private DatabaseHelper databaseHelper;
    private List<Question> wrongQuestions;
    private List<Question> filteredQuestions;

    private static final String TAG = "WrongQuestionsActivity";
    private static final int SORT_BY_TIME = 0;
    private static final int SORT_BY_COUNT = 1;
    private int currentSortMode = SORT_BY_TIME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_questions);

        Log.d(TAG, "onCreate: 初始化错题本界面");

        databaseHelper = new DatabaseHelper(this);
        initViews();
        loadWrongQuestions();
        updateStats();
    }

    @SuppressLint("WrongViewCast")
    private void initViews() {
        try {
            // 设置返回按钮
            View ivBack = findViewById(R.id.iv_back);
            if (ivBack != null) {
                ivBack.setOnClickListener(v -> finish());
            }

            // 搜索框
            etSearch = findViewById(R.id.et_search);
            ivClearSearch = findViewById(R.id.iv_clear_search);

            if (etSearch != null) {
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        searchQuestions(s.toString());
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
            }

            if (ivClearSearch != null) {
                ivClearSearch.setOnClickListener(v -> {
                    if (etSearch != null) {
                        etSearch.setText("");
                    }
                });
            }

            if (btnSortByTime != null) {
                btnSortByTime.setOnClickListener(v -> {
                    currentSortMode = SORT_BY_TIME;
                    updateSortButtons();
                    loadWrongQuestions();
                });
            }

            if (btnSortByCount != null) {
                btnSortByCount.setOnClickListener(v -> {
                    currentSortMode = SORT_BY_COUNT;
                    updateSortButtons();
                    loadWrongQuestions();
                });
            }

            // 清空错题本按钮 - 改为 TextView
            tvClearAll = findViewById(R.id.tv_clear_all); // ID 需要对应修改
            if (tvClearAll != null) {
                tvClearAll.setOnClickListener(v -> showClearAllDialog());
            }

            recyclerView = findViewById(R.id.rv_wrong_questions);
            tvEmpty = findViewById(R.id.tv_empty);
            tvStats = findViewById(R.id.tv_stats);

            if (recyclerView == null) {
                Log.e(TAG, "RecyclerView未找到");
                return;
            }

            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            wrongQuestions = new ArrayList<>();
            filteredQuestions = new ArrayList<>();
            adapter = new WrongQuestionAdapter(filteredQuestions);
            recyclerView.setAdapter(adapter);

            updateSortButtons();
            Log.d(TAG, "界面初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "初始化界面失败: " + e.getMessage(), e);
            Toast.makeText(this, "初始化界面失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSortButtons() {
        if (btnSortByTime != null) {
            btnSortByTime.setSelected(currentSortMode == SORT_BY_TIME);
        }
        if (btnSortByCount != null) {
            btnSortByCount.setSelected(currentSortMode == SORT_BY_COUNT);
        }
    }

    private void loadWrongQuestions() {
        try {
            Log.d(TAG, "开始加载错题数据，排序模式: " + currentSortMode);

            wrongQuestions.clear();
            List<Question> questions;

            if (currentSortMode == SORT_BY_TIME) {
                questions = databaseHelper.getAllWrongQuestions(); // 按时间倒序
            } else {
                questions = databaseHelper.getAllWrongQuestionsByWrongCount(); // 按错误次数倒序
            }

            wrongQuestions.addAll(questions);
            filteredQuestions.clear();
            filteredQuestions.addAll(wrongQuestions);

            Log.d(TAG, "加载到错题数量: " + wrongQuestions.size());

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            updateEmptyState();
            updateStats();

        } catch (Exception e) {
            Log.e(TAG, "加载错题数据失败: " + e.getMessage(), e);
            Toast.makeText(this, "加载错题失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void searchQuestions(String keyword) {
        try {
            filteredQuestions.clear();

            if (keyword.isEmpty()) {
                filteredQuestions.addAll(wrongQuestions);
            } else {
                List<Question> searchResults = databaseHelper.searchWrongQuestions(keyword);
                filteredQuestions.addAll(searchResults);
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            updateEmptyState();

        } catch (Exception e) {
            Log.e(TAG, "搜索错题失败: " + e.getMessage(), e);
        }
    }

    private void updateStats() {
        if (tvStats != null) {
            Map<String, Integer> stats = databaseHelper.getWrongQuestionStats();
            int totalQuestions = stats.getOrDefault("total", 0);
            int totalWrongCount = stats.getOrDefault("totalWrongCount", 0);

            String statsText = String.format("共%d道错题 • 累计错误%d次", totalQuestions, totalWrongCount);
            tvStats.setText(statsText);
        }
    }

    private void updateEmptyState() {
        boolean isEmpty = filteredQuestions.isEmpty();

        if (tvEmpty != null) {
            if (isEmpty) {
                String searchText = etSearch != null ? etSearch.getText().toString() : "";
                if (!searchText.isEmpty()) {
                    tvEmpty.setText("未找到包含 \"" + searchText + "\" 的错题");
                } else {
                    tvEmpty.setText("暂无错题\n答错的题目会自动添加到错题本");
                }
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                tvEmpty.setVisibility(View.GONE);
            }
        }

        if (recyclerView != null) {
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        if (tvClearAll != null) {
            tvClearAll.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void showClearAllDialog() {
        if (wrongQuestions.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("清空错题本")
                .setMessage("确定要清空所有错题吗？此操作不可恢复。")
                .setPositiveButton("确定", (dialog, which) -> {
                    databaseHelper.clearWrongQuestions();
                    loadWrongQuestions();
                    Toast.makeText(this, "已清空错题本", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 错题列表适配器
    private class WrongQuestionAdapter extends RecyclerView.Adapter<WrongQuestionAdapter.ViewHolder> {
        private List<Question> questions;

        public WrongQuestionAdapter(List<Question> questions) {
            this.questions = questions != null ? questions : new ArrayList<>();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            try {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_wrong_question, parent, false);
                return new ViewHolder(view);
            } catch (Exception e) {
                Log.e(TAG, "创建ViewHolder失败: " + e.getMessage(), e);
                TextView textView = new TextView(parent.getContext());
                textView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return new ViewHolder(textView);
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            try {
                if (questions == null || position >= questions.size()) {
                    return;
                }

                Question question = questions.get(position);
                if (question == null) {
                    return;
                }

                // 设置题目内容
                if (holder.tvQuestionContent != null) {
                    String content = question.getContent();
                    if (content != null && content.length() > 100) {
                        content = content.substring(0, 100) + "...";
                    }
                    holder.tvQuestionContent.setText(content != null ? content : "题目内容为空");
                }

                // 设置题目类型和错误信息
                if (holder.tvQuestionType != null) {
                    String typeInfo = question.getTypeName() + " • 错误" + question.getWrongCount() + "次";
                    holder.tvQuestionType.setText(typeInfo);
                }

                // 显示错误时间
                if (holder.tvWrongTime != null) {
                    holder.tvWrongTime.setText(question.getFormattedWrongTime());
                }

                // 显示正确答案
                if (holder.tvCorrectAnswer != null) {
                    StringBuilder correctAnswer = new StringBuilder("正确答案：");
                    if (question.getAnswers() != null && !question.getAnswers().isEmpty()) {
                        for (String answer : question.getAnswers()) {
                            correctAnswer.append(answer);
                        }
                    } else {
                        correctAnswer.append("无答案");
                    }
                    holder.tvCorrectAnswer.setText(correctAnswer.toString());
                }

                // 显示解析（如果有）
                if (holder.tvAnalysis != null) {
                    if (question.getAnalysis() != null && !question.getAnalysis().isEmpty()) {
                        String analysis = question.getAnalysis();
                        if (analysis.length() > 50) {
                            analysis = analysis.substring(0, 50) + "...";
                        }
                        holder.tvAnalysis.setText("解析：" + analysis);
                        holder.tvAnalysis.setVisibility(View.VISIBLE);
                    } else {
                        holder.tvAnalysis.setVisibility(View.GONE);
                    }
                }

                // 点击重做题目
                holder.itemView.setOnClickListener(v -> {
                    startPracticeWithSingleQuestion(question);
                });

                // 长按删除
                holder.itemView.setOnLongClickListener(v -> {
                    showDeleteDialog(question, position);
                    return true;
                });
            } catch (Exception e) {
                Log.e(TAG, "绑定ViewHolder失败: " + e.getMessage(), e);
            }
        }

        @Override
        public int getItemCount() {
            return questions != null ? questions.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvQuestionContent, tvQuestionType, tvCorrectAnswer, tvAnalysis, tvWrongTime;

            ViewHolder(View itemView) {
                super(itemView);
                try {
                    tvQuestionContent = itemView.findViewById(R.id.tv_question_content);
                    tvQuestionType = itemView.findViewById(R.id.tv_question_type);
                    tvCorrectAnswer = itemView.findViewById(R.id.tv_correct_answer);
                    tvAnalysis = itemView.findViewById(R.id.tv_analysis);
                    tvWrongTime = itemView.findViewById(R.id.tv_wrong_time);
                } catch (Exception e) {
                    Log.e(TAG, "初始化ViewHolder视图失败: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 启动练习页面，只练习单个题目
     */
    private void startPracticeWithSingleQuestion(Question question) {
        try {
            Log.d(TAG, "启动单个题目练习");

            ArrayList<Question> singleQuestionList = new ArrayList<>();
            singleQuestionList.add(question);

            Intent intent = new Intent(WrongQuestionsActivity.this, PracticeActivity.class);
            intent.putExtra("question_list", singleQuestionList);
            intent.putExtra("from_wrong_questions", true);
            intent.putExtra("single_question_mode", true);

            startActivityForResult(intent, REQUEST_CODE_PRACTICE);
            Log.d(TAG, "成功启动练习页面");

        } catch (Exception e) {
            Log.e(TAG, "启动练习页面失败: " + e.getMessage(), e);
            Toast.makeText(this, "启动练习失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static final int REQUEST_CODE_PRACTICE = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PRACTICE) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "检测到题目已从错题本移除，刷新列表");
                loadWrongQuestions();
                Toast.makeText(this, "题目已从错题本移除", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showDeleteDialog(Question question, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除错题")
                .setMessage("确定要从错题本中删除这道题吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        int result = databaseHelper.removeWrongQuestion(question.getId());
                        if (result > 0) {
                            // 从两个列表中都要删除
                            wrongQuestions.remove(question);
                            filteredQuestions.remove(question);
                            adapter.notifyItemRemoved(position);
                            updateEmptyState();
                            updateStats();
                            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "删除错题失败: " + e.getMessage(), e);
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWrongQuestions();
    }
}
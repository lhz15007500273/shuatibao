package com.shuatibao.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

public class WrongQuestionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private Button btnClearAll;
    private WrongQuestionAdapter adapter;
    private DatabaseHelper databaseHelper;
    private List<Question> wrongQuestions;

    private static final String TAG = "WrongQuestionsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_questions);

        Log.d(TAG, "onCreate: 初始化错题本界面");

        databaseHelper = new DatabaseHelper(this);
        initViews();
        loadWrongQuestions();
    }

    private void initViews() {
        try {
            // 设置返回按钮
            View ivBack = findViewById(R.id.iv_back);
            if (ivBack != null) {
                ivBack.setOnClickListener(v -> finish());
            } else {
                Log.e(TAG, "返回按钮未找到");
            }

            // 清空错题本按钮
            btnClearAll = findViewById(R.id.btn_clear_all);
            if (btnClearAll != null) {
                btnClearAll.setOnClickListener(v -> showClearAllDialog());
            } else {
                Log.e(TAG, "清空按钮未找到");
            }

            recyclerView = findViewById(R.id.rv_wrong_questions);
            tvEmpty = findViewById(R.id.tv_empty);

            if (recyclerView == null) {
                Log.e(TAG, "RecyclerView未找到");
                return;
            }

            if (tvEmpty == null) {
                Log.e(TAG, "空状态TextView未找到");
                return;
            }

            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            wrongQuestions = new ArrayList<>();
            adapter = new WrongQuestionAdapter(wrongQuestions);
            recyclerView.setAdapter(adapter);

            Log.d(TAG, "界面初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "初始化界面失败: " + e.getMessage(), e);
            Toast.makeText(this, "初始化界面失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadWrongQuestions() {
        try {
            Log.d(TAG, "开始加载错题数据");
            wrongQuestions.clear();
            List<Question> questions = databaseHelper.getAllWrongQuestions();
            wrongQuestions.addAll(questions);

            Log.d(TAG, "加载到错题数量: " + wrongQuestions.size());

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            // 显示/隐藏空状态
            if (wrongQuestions.isEmpty()) {
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(View.VISIBLE);
                }
                if (recyclerView != null) {
                    recyclerView.setVisibility(View.GONE);
                }
                if (btnClearAll != null) {
                    btnClearAll.setVisibility(View.GONE);
                }
            } else {
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(View.GONE);
                }
                if (recyclerView != null) {
                    recyclerView.setVisibility(View.VISIBLE);
                }
                if (btnClearAll != null) {
                    btnClearAll.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载错题数据失败: " + e.getMessage(), e);
            Toast.makeText(this, "加载错题失败", Toast.LENGTH_SHORT).show();
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
                // 创建一个简单的备选布局
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

                // 设置题目类型
                if (holder.tvQuestionType != null) {
                    holder.tvQuestionType.setText(question.getTypeName());
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
                    // 跳转到练习页面，只练习这道错题
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
            TextView tvQuestionContent, tvQuestionType, tvCorrectAnswer, tvAnalysis;

            ViewHolder(View itemView) {
                super(itemView);
                try {
                    tvQuestionContent = itemView.findViewById(R.id.tv_question_content);
                    tvQuestionType = itemView.findViewById(R.id.tv_question_type);
                    tvCorrectAnswer = itemView.findViewById(R.id.tv_correct_answer);
                    tvAnalysis = itemView.findViewById(R.id.tv_analysis);
                } catch (Exception e) {
                    Log.e(TAG, "初始化ViewHolder视图失败: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 启动练习页面，只练习单个题目 - 修复版
     */
    // 在 WrongQuestionsActivity.java 中修改启动方法
    /**
     * 启动练习页面，只练习单个题目 - 使用 startActivityForResult
     */
    private void startPracticeWithSingleQuestion(Question question) {
        try {
            Log.d(TAG, "启动单个题目练习");

            // 创建只包含这道题目的列表
            ArrayList<Question> singleQuestionList = new ArrayList<>();
            singleQuestionList.add(question);

            Intent intent = new Intent(WrongQuestionsActivity.this, PracticeActivity.class);

            // 使用 Serializable 传递题目列表
            intent.putExtra("question_list", singleQuestionList);
            intent.putExtra("from_wrong_questions", true);
            intent.putExtra("single_question_mode", true);

            // 使用 startActivityForResult 来接收练习结果
            startActivityForResult(intent, REQUEST_CODE_PRACTICE);
            Log.d(TAG, "成功启动练习页面");

        } catch (Exception e) {
            Log.e(TAG, "启动练习页面失败: " + e.getMessage(), e);
            Toast.makeText(this, "启动练习失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 添加请求码常量
    private static final int REQUEST_CODE_PRACTICE = 1001;

    // 添加 onActivityResult 方法处理返回结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PRACTICE) {
            // 如果练习页面返回 RESULT_OK，说明有题目被移出错题本，需要刷新列表
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "检测到题目已从错题本移除，刷新列表");
                loadWrongQuestions();

                // 显示提示信息
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
                            wrongQuestions.remove(position);
                            adapter.notifyItemRemoved(position);
                            loadWrongQuestions(); // 重新检查空状态
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
        // 重新加载数据，确保显示最新状态
        loadWrongQuestions();
    }
}
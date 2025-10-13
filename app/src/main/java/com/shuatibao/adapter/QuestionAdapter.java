package com.shuatibao.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.shuatibao.R;
import com.shuatibao.model.Question;

import java.util.List;

public class QuestionAdapter extends RecyclerView.Adapter<QuestionAdapter.ViewHolder> {

    private List<Question> questions;
    private boolean showAnswerAndAnalysis; // 控制是否显示答案和解析

    public QuestionAdapter(List<Question> questions, boolean showAnswerAndAnalysis) {
        this.questions = questions;
        this.showAnswerAndAnalysis = showAnswerAndAnalysis;

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_question, parent, false);
        return new ViewHolder(view, showAnswerAndAnalysis);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Question question = questions.get(position);

        holder.tvQuestionNumber.setText((position + 1) + ".");
        holder.tvQuestionContent.setText(question.getContent());
        holder.tvQuestionType.setText(question.getTypeName());

        // 显示选项（如果是选择题）
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            StringBuilder optionsText = new StringBuilder();
            for (String option : question.getOptions()) {
                optionsText.append(option).append("\n");
            }
            holder.tvOptions.setText(optionsText.toString());
            holder.tvOptions.setVisibility(View.VISIBLE);
        } else {
            holder.tvOptions.setVisibility(View.GONE);
        }

        // 根据标志决定是否显示答案和解析
        if (showAnswerAndAnalysis) {
            // 显示答案和解析
            if (question.getAnswers() != null && !question.getAnswers().isEmpty()) {
                holder.tvAnswer.setText("答案: " + String.join(", ", question.getAnswers()));
                holder.tvAnswer.setVisibility(View.VISIBLE);
            } else {
                holder.tvAnswer.setVisibility(View.GONE);
            }

            if (question.getAnalysis() != null && !question.getAnalysis().isEmpty()) {
                holder.tvAnalysis.setText("解析: " + question.getAnalysis());
                holder.tvAnalysis.setVisibility(View.VISIBLE);
            } else {
                holder.tvAnalysis.setVisibility(View.GONE);
            }
        } else {
            // 隐藏答案和解析
            holder.tvAnswer.setVisibility(View.GONE);
            holder.tvAnalysis.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return questions != null ? questions.size() : 0;
    }

    public void updateData(List<Question> newQuestions) {
        this.questions = newQuestions;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuestionNumber, tvQuestionContent, tvQuestionType, tvOptions, tvAnswer, tvAnalysis;

        public ViewHolder(@NonNull View itemView, boolean showAnswerAndAnalysis) {
            super(itemView);
            tvQuestionNumber = itemView.findViewById(R.id.tv_question_number);
            tvQuestionContent = itemView.findViewById(R.id.tv_question_content);
            tvQuestionType = itemView.findViewById(R.id.tv_question_type);
            tvOptions = itemView.findViewById(R.id.tv_options);
            tvAnswer = itemView.findViewById(R.id.tv_answer);
            tvAnalysis = itemView.findViewById(R.id.tv_analysis);
        }
    }
}
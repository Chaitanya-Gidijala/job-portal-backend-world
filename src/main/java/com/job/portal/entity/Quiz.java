package com.job.portal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "quizzes")
public class Quiz extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private Integer duration; // in minutes

    private Integer totalQuestions;

    @ElementCollection
    @CollectionTable(name = "quiz_tags", joinColumns = @JoinColumn(name = "quiz_id"))
    @Column(name = "tag")
    private List<String> tags;

    private Long jobId;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<QuizQuestion> questions = new ArrayList<>();

    public void addQuestion(QuizQuestion question) {
        questions.add(question);
        question.setQuiz(this);
    }

    public void removeQuestion(QuizQuestion question) {
        questions.remove(question);
        question.setQuiz(null);
    }
}

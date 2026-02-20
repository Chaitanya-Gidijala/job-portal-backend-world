package com.job.portal.repository;

import com.job.portal.entity.InterviewQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    Page<InterviewQuestion> findByDifficulty(InterviewQuestion.Difficulty difficulty, Pageable pageable);

    Page<InterviewQuestion> findByJobId(Long jobId, Pageable pageable);

    Page<InterviewQuestion> findByTagsContaining(String tag, Pageable pageable);

    Page<InterviewQuestion> findByTagsContainingAndDifficulty(String tag, InterviewQuestion.Difficulty difficulty,
            Pageable pageable);
}

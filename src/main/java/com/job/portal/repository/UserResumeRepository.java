package com.job.portal.repository;

import com.job.portal.entity.UserResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserResumeRepository extends JpaRepository<UserResume, Long> {
    List<UserResume> findByUserEmailOrderByCreatedAtDesc(String userEmail);
}

package com.job.portal.service;

import com.job.portal.dto.JobDTO;

import com.job.portal.dto.JobSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface JobService {
    List<JobDTO> getAllJobs();

    JobDTO getJobById(Long id);

    long getJobCount();

    JobDTO saveJob(JobDTO job);

    List<JobDTO> getJobsByExperience(String experience);

    List<JobDTO> getLatestJobs();

    JobDTO getJobByTitle(String jobTitle);

    JobDTO getJobByTitleAndId(String jobTitle, Long id);

    Page<JobDTO> searchJobs(JobSearchCriteria criteria, Pageable pageable);

    JobDTO updateJob(Long id, JobDTO jobDTO);

    void deleteJob(Long id);

    List<JobDTO> getJobsByCompany(String company);

    Map<String, Long> getJobStatsByType();

    List<JobDTO> saveAllJobs(List<JobDTO> jobs);
}

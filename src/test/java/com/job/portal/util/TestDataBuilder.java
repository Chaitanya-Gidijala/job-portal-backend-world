package com.job.portal.util;

import com.job.portal.dto.JobDTO;
import com.job.portal.entity.Job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating test data consistently across all tests.
 * Provides factory methods for creating test Job entities and DTOs.
 */
public class TestDataBuilder {

    /**
     * Creates a test JobDTO with default values
     */
    public static JobDTO createTestJobDTO() {
        JobDTO job = new JobDTO();
        job.setJobTitle("Software Engineer");
        job.setCompany("Test Company");
        job.setLocation("Remote");
        job.setJobDetails("Test job details for software engineer position");
        job.setExperience("Fresher");
        job.setSalary("50000-70000");
        job.setJobType("Full-time");
        job.setApplyLink("https://test.com/apply");
        job.setCompanyLogo("https://test.com/logo.png");
        job.setCreatedAt(LocalDateTime.now());
        return job;
    }

    /**
     * Creates a test Job entity with default values
     */
    public static Job createTestJob() {
        Job job = new Job();
        job.setJobTitle("Software Engineer");
        job.setCompany("Test Company");
        job.setLocation("Remote");
        job.setJobDetails("Test job details for software engineer position");
        job.setExperience("Fresher");
        job.setSalary("50000-70000");
        job.setJobType("Full-time");
        job.setApplyLink("https://test.com/apply");
        job.setCompanyLogo("https://test.com/logo.png");
        job.setCreatedDate(LocalDate.now());
        job.setCreatedTime(LocalTime.now());
        return job;
    }

    /**
     * Creates a test Job entity with specific ID
     */
    public static Job createTestJobWithId(Long id) {
        Job job = createTestJob();
        job.setId(id);
        return job;
    }

    /**
     * Creates a test JobDTO with specific ID
     */
    public static JobDTO createTestJobDTOWithId(Long id) {
        JobDTO job = createTestJobDTO();
        job.setId(id);
        return job;
    }

    /**
     * Creates a list of test JobDTOs
     */
    public static List<JobDTO> createTestJobDTOList(int count) {
        List<JobDTO> jobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JobDTO job = createTestJobDTO();
            job.setJobTitle("Job " + (i + 1));
            job.setCompany("Company " + (i + 1));
            jobs.add(job);
        }
        return jobs;
    }

    /**
     * Creates a list of test Job entities
     */
    public static List<Job> createTestJobList(int count) {
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Job job = createTestJob();
            job.setId((long) (i + 1));
            job.setJobTitle("Job " + (i + 1));
            job.setCompany("Company " + (i + 1));
            jobs.add(job);
        }
        return jobs;
    }

    /**
     * Creates a JobDTO with custom values
     */
    public static JobDTO createCustomJobDTO(String title, String company, String experience) {
        JobDTO job = createTestJobDTO();
        job.setJobTitle(title);
        job.setCompany(company);
        job.setExperience(experience);
        return job;
    }

    /**
     * Creates a Job entity with custom values
     */
    public static Job createCustomJob(String title, String company, String experience) {
        Job job = createTestJob();
        job.setJobTitle(title);
        job.setCompany(company);
        job.setExperience(experience);
        return job;
    }
}

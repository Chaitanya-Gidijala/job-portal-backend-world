package com.job.portal.controller;

import com.job.portal.dto.ApiResponse;
import com.job.portal.dto.JobDTO;
import com.job.portal.dto.JobSearchCriteria;
import com.job.portal.service.JobService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    @Autowired
    private JobService jobService;

    // Endpoint to get all jobs
    @GetMapping
    public ResponseEntity<ApiResponse<List<JobDTO>>> getJobs() {
        List<JobDTO> jobs = jobService.getAllJobs();
        if (jobs.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No jobs found", Collections.emptyList()));
        }
        return ResponseEntity.ok(ApiResponse.success("Jobs fetched successfully", jobs));
    }

    @GetMapping("/experience/{experience}")
    public ResponseEntity<ApiResponse<List<JobDTO>>> getJobsByExperience(@PathVariable String experience) {
        List<JobDTO> jobs = jobService.getJobsByExperience(experience);
        if (jobs == null || jobs.isEmpty()) {
            return ResponseEntity
                    .ok(ApiResponse.success("No jobs found for experience: " + experience, Collections.emptyList()));
        }
        return ResponseEntity.ok(ApiResponse.success("Jobs fetched successfully", jobs));
    }

    // Endpoint to get the latest jobs sorted by the most recent posting time
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<List<JobDTO>>> getLatestJobs() {
        List<JobDTO> jobs = jobService.getLatestJobs();
        if (jobs.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No latest jobs found", Collections.emptyList()));
        }
        return ResponseEntity.ok(ApiResponse.success("Latest jobs fetched successfully", jobs));
    }

    // Endpoint to get a specific job by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobDTO>> getJob(@PathVariable Long id) {
        JobDTO jobDTO = jobService.getJobById(id);
        if (jobDTO == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Job not found with id: " + id, null));
        }
        return ResponseEntity.ok(ApiResponse.success("Job fetched successfully", jobDTO));
    }

    @GetMapping("/title/{jobTitle}")
    public ResponseEntity<ApiResponse<JobDTO>> getJobByTitle(@PathVariable String jobTitle) {
        // Replace hyphens with spaces before passing to the service
        jobTitle = jobTitle.replace('-', ' ');

        JobDTO jobDTO = jobService.getJobByTitle(jobTitle); // Fetch the job by job title
        if (jobDTO == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Job not found with title: " + jobTitle, null));
        }
        return ResponseEntity.ok(ApiResponse.success("Job fetched successfully", jobDTO));
    }

    // New endpoint: Get job by both title and ID for uniqueness
    @GetMapping("/title/{jobTitle}/id/{id}")
    public ResponseEntity<ApiResponse<JobDTO>> getJobByTitleAndId(
            @PathVariable String jobTitle,
            @PathVariable Long id) {
        // Replace hyphens with spaces before passing to the service
        jobTitle = jobTitle.replace('-', ' ');

        JobDTO jobDTO = jobService.getJobByTitleAndId(jobTitle, id);
        if (jobDTO == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Job not found with title: " + jobTitle + " and id: " + id, null));
        }
        return ResponseEntity.ok(ApiResponse.success("Job fetched successfully", jobDTO));
    }

    // Endpoint to get the job count
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> getJobCount() {
        long count = jobService.getJobCount();
        return ResponseEntity.ok(ApiResponse.success("Job count fetched successfully", count));
    }

    @GetMapping("/company/{company}")
    public ResponseEntity<ApiResponse<List<JobDTO>>> getJobsByCompany(@PathVariable String company) {
        List<JobDTO> jobs = jobService.getJobsByCompany(company);
        if (jobs.isEmpty()) {
            return ResponseEntity
                    .ok(ApiResponse.success("No jobs found for company: " + company, Collections.emptyList()));
        }
        return ResponseEntity.ok(ApiResponse.success("Jobs fetched successfully", jobs));
    }

    @GetMapping("/stats/type")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getJobStatsByType() {
        return ResponseEntity.ok(ApiResponse.success("Job stats fetched successfully", jobService.getJobStatsByType()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<JobDTO>>> searchJobs(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String minSalary,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate postedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate postedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setKeyword(keyword);
        criteria.setLocation(location);
        criteria.setJobType(jobType);
        criteria.setCompany(company);
        criteria.setMinSalary(minSalary);
        criteria.setPostedFrom(postedFrom);
        criteria.setPostedTo(postedTo);

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<JobDTO> result = jobService.searchJobs(criteria, pageable);

        if (result.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No jobs found matching criteria", result));
        }
        return ResponseEntity.ok(ApiResponse.success("Jobs found successfully", result));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<ApiResponse<JobDTO>> createJob(@Valid @RequestBody JobDTO jobDTO) {
        JobDTO savedJob = jobService.saveJob(jobDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Job created successfully", savedJob));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<ApiResponse<List<JobDTO>>> createJobsBatch(@RequestBody List<@Valid JobDTO> jobDTOs) {
        List<JobDTO> savedJobs = jobService.saveAllJobs(jobDTOs);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Batch jobs created successfully", savedJobs));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<ApiResponse<JobDTO>> updateJob(@PathVariable Long id, @Valid @RequestBody JobDTO jobDTO) {
        JobDTO updatedJob = jobService.updateJob(id, jobDTO);
        return ResponseEntity.ok(ApiResponse.success("Job updated successfully", updatedJob));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.success("Job deleted successfully", null));
    }
}

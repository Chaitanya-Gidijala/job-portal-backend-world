package com.job.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

public class JobDTO {

    private Long id;

    @NotBlank(message = "Job title is required")
    @Size(min = 3, max = 100, message = "Job title must be between 3 and 100 characters")
    private String jobTitle;

    @NotBlank(message = "Company name is required")
    @Size(min = 3, max = 100, message = "Company name must be between 3 and 100 characters")
    private String company;

    @NotBlank(message = "Location is required")
    @Size(min = 3, max = 100, message = "Location must be between 3 and 100 characters")
    private String location;

    @NotBlank(message = "Job details are required")
    private String jobDetails;

    @NotBlank(message = "Experience required is required")
    @Size(min = 1, max = 50, message = "Experience required must be between 1 and 50 characters")
    private String experienceRequired;

    private String experience;

    private LocalDateTime createdAt;

    @URL(message = "Apply link must be a valid URL")
    private String applyLink;

    @NotBlank(message = "Salary is required")
    private String salary;

    private String companyLogo; // You may add @URL validation if you expect this to be a URL

    @NotBlank(message = "Job type is required")
    @Size(min = 3, max = 50, message = "Job type must be between 3 and 50 characters")
    private String jobType;

    // Default constructor
    public JobDTO() {
    }

    // Getter and Setter Methods

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getJobDetails() {
        return jobDetails;
    }

    public void setJobDetails(String jobDetails) {
        this.jobDetails = jobDetails;
    }

    public String getExperienceRequired() {
        return experienceRequired;
    }

    public void setExperienceRequired(String experienceRequired) {
        this.experienceRequired = experienceRequired;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getApplyLink() {
        return applyLink;
    }

    public void setApplyLink(String applyLink) {
        this.applyLink = applyLink;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public String getCompanyLogo() {
        return companyLogo;
    }

    public void setCompanyLogo(String companyLogo) {
        this.companyLogo = companyLogo;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    @Override
    public String toString() {
        return "JobDTO{" +
                ", jobTitle='" + jobTitle + '\'' +
                ", company='" + company + '\'' +
                ", location='" + location + '\'' +
                ", jobDetails='" + jobDetails + '\'' +
                ", experienceRequired='" + experienceRequired + '\'' +
                ", experience='" + experience + '\'' +
                ", createdAt=" + createdAt +
                ", applyLink='" + applyLink + '\'' +
                ", salary='" + salary + '\'' +
                ", companyLogo='" + companyLogo + '\'' +
                ", jobType='" + jobType + '\'' +
                '}';
    }
}

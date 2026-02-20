package com.job.portal.dto;

import java.time.LocalDate;

public class JobSearchCriteria {
    private String keyword;
    private String location;
    private String jobType;
    private String company;
    private String minSalary;
    private LocalDate postedFrom;
    private LocalDate postedTo;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getMinSalary() {
        return minSalary;
    }

    public void setMinSalary(String minSalary) {
        this.minSalary = minSalary;
    }

    public LocalDate getPostedFrom() {
        return postedFrom;
    }

    public void setPostedFrom(LocalDate postedFrom) {
        this.postedFrom = postedFrom;
    }

    public LocalDate getPostedTo() {
        return postedTo;
    }

    public void setPostedTo(LocalDate postedTo) {
        this.postedTo = postedTo;
    }

    @Override
    public String toString() {
        return "JobSearchCriteria{" +
                "keyword='" + keyword + '\'' +
                ", location='" + location + '\'' +
                ", jobType='" + jobType + '\'' +
                ", company='" + company + '\'' +
                ", minSalary='" + minSalary + '\'' +
                ", postedFrom=" + postedFrom +
                ", postedTo=" + postedTo +
                '}';
    }
}


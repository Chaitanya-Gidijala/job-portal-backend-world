package com.job.portal.service;

import com.job.portal.dto.ContactRequest;

import com.job.portal.dto.ContactResponse;
import java.util.List;

public interface ContactService {
    void submitContactForm(ContactRequest request);
    List<ContactResponse> getAllInquiries();
    ContactResponse getInquiryById(Long id);
}

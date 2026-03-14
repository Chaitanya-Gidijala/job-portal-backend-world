package com.job.portal.service;

import com.job.portal.dto.ContactRequest;

public interface ContactService {
    void submitContactForm(ContactRequest request);
}

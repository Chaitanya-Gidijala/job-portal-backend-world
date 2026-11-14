package com.job.portal.repository;

import com.job.portal.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactRepository extends JpaRepository<ContactMessage, Long> {

    /** Unread inquiries (legacy rows with a NULL flag count as unread). */
    @Query("select count(c) from ContactMessage c where c.isRead is null or c.isRead = false")
    long countUnread();
}

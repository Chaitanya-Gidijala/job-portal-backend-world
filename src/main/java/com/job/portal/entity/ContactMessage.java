package com.job.portal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "contact_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;
    private String serviceType;
    private Integer budget;
    private String message;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Nullable for legacy rows; read through isRead() which treats NULL as unread. */
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private Boolean isRead = false;

    public boolean isRead() {
        return Boolean.TRUE.equals(isRead);
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }
}

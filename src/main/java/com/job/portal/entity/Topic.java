package com.job.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "topics")
public class Topic extends BaseEntity {

    @Id
    @Column(nullable = false, unique = true)
    private String id; // e.g., "java", "react"

    @Column(nullable = false)
    private String name;

    private String icon;
}

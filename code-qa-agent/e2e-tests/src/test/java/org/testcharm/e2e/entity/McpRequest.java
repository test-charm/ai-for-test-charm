package org.testcharm.e2e.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mcp_requests")
@Getter
@Setter
public class McpRequest {

    @Id
    private String id;

    @Column(name = "created_at")
    private String createdAt;

    private String question, answer, provider, model;

}

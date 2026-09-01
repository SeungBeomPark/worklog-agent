package com.example.worklogagent.repository.jpa;

import com.example.worklogagent.model.WorkLogEntry;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * 업무일지 JPA 엔티티 (DB 버전).
 * 도메인 모델(WorkLogEntry record)과 분리해 둔다.
 * work_date 에 유니크 제약을 둬서 하루 1건을 보장한다.
 */
@Entity
@Table(name = "work_log")
public class WorkLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_date", nullable = false, unique = true)
    private LocalDate workDate;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "project", length = 255)
    private String project;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    protected WorkLogJpaEntity() {
        // JPA 기본 생성자
    }

    public WorkLogJpaEntity(LocalDate workDate, String type, String project, String content) {
        this.workDate = workDate;
        this.type = type;
        this.project = project;
        this.content = content;
    }

    /** 엔티티 → 도메인 모델 */
    public WorkLogEntry toDomain() {
        return new WorkLogEntry(workDate, type, project, content);
    }

    /** 기존 엔티티에 값 반영 (수정용) */
    public void update(String type, String project, String content) {
        this.type = type;
        this.project = project;
        this.content = content;
    }

    public Long getId() { return id; }
    public LocalDate getWorkDate() { return workDate; }
    public String getType() { return type; }
    public String getProject() { return project; }
    public String getContent() { return content; }
}

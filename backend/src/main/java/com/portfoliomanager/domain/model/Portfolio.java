package com.portfoliomanager.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "portfolio")
public class Portfolio {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency = "USD";

    @Column(name = "is_archived", nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Portfolio() {}

    public Portfolio(
            String id,
            AppUser user,
            String name,
            String description,
            String baseCurrency) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.description = description;
        this.baseCurrency = baseCurrency;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public boolean isArchived() {
        return archived;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 返回所属用户 ID，用于所有权校验（避免直接暴露 user 代理对象） */
    public String getUserId() {
        return user != null ? user.getId() : null;
    }

    /** 更新组合名称（PATCH 接口使用） */
    public void setName(String name) {
        this.name = name;
    }

    /** 更新组合描述（PATCH 接口使用，传 null 则不修改） */
    public void setDescription(String description) {
        this.description = description;
    }

    /** 归档/取消归档（归档后不出现在默认列表） */
    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}

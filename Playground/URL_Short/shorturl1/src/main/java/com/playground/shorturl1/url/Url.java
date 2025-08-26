package com.playground.shorturl1.url;

import com.playground.shorturl1.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Table(
        name = "urls",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_urls_code", columnNames = "code")
        }
)
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Url {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("유저 ID")
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "owner_id",
            foreignKey = @ForeignKey(name = "fk_urls_owner")
    )
    private User owner;

    @Comment("실제 URL")
    @Column(name = "origin_url", columnDefinition = "TEXT", nullable = false)
    private String originUrl;

    @Comment("코드")
    @Column(name = "code", nullable = false, length = 8)
    private String code;

    @Comment("생성일시")
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}


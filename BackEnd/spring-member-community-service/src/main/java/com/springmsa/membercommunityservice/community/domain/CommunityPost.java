package com.springmsa.membercommunityservice.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "community_posts")
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @Column(name = "owner_sub", nullable = false, length = 100)
    private String ownerSub;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CommunityPost() {
    }

    public static CommunityPost create(String ownerSub, String author, String title, String content) {
        CommunityPost post = new CommunityPost();
        post.ownerSub = ownerSub;
        post.author = author;
        post.title = title.trim();
        post.content = content.trim();
        post.createdAt = Instant.now();
        post.updatedAt = post.createdAt;
        return post;
    }

    public void update(String title, String content) {
        this.title = title.trim();
        this.content = content.trim();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOwnerSub() { return ownerSub; }
    public String getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

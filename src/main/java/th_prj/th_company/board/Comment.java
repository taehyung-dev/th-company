package th_prj.th_company.board;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import th_prj.th_company.member.Member;

@Entity
@Table(name = "post_comment")
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private Post post;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private Member author;

	@Column(nullable = false, length = 1000)
	private String content;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Comment() {
	}

	public Comment(Post post, Member author, String content) {
		this.post = post;
		this.author = author;
		this.content = content;
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public Post getPost() { return post; }
	public Member getAuthor() { return author; }
	public String getContent() { return content; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}

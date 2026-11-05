package th_prj.th_company.board;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import th_prj.th_company.member.Member;

/**
 * 게시글. 본문은 일반 텍스트(줄바꿈 유지)로 저장/표시한다.
 */
@Entity
@Table(name = "post", indexes = @Index(name = "idx_post_board", columnList = "board_id, pinned, id"))
public class Post {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private Board board;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private Member author;

	@Column(nullable = false, length = 200)
	private String title;

	// MariaDB MEDIUMTEXT (최대 16MB). 길이를 안 주면 TINYTEXT(255바이트)로 만들어진다
	@Lob
	@Column(nullable = false, length = 16_777_215)
	private String content;

	@Column(nullable = false)
	private int viewCount;

	/** 목록 상단 고정 (관리자만 지정) */
	@Column(nullable = false)
	private boolean pinned;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Post() {
	}

	public Post(Board board, Member author, String title, String content, boolean pinned) {
		this.board = board;
		this.author = author;
		this.title = title;
		this.content = content;
		this.pinned = pinned;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public void update(String title, String content, boolean pinned) {
		this.title = title;
		this.content = content;
		this.pinned = pinned;
	}

	public Long getId() { return id; }
	public Board getBoard() { return board; }
	public Member getAuthor() { return author; }
	public String getTitle() { return title; }
	public String getContent() { return content; }
	public int getViewCount() { return viewCount; }
	public boolean isPinned() { return pinned; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}

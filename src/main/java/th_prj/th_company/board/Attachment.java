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

/**
 * 게시글 첨부파일. 실제 파일은 FileStorage 에 storedName 으로 저장된다.
 */
@Entity
@Table(name = "post_attachment")
public class Attachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private Post post;

	@Column(nullable = false, length = 255)
	private String originalName;

	@Column(nullable = false, unique = true, length = 100)
	private String storedName;

	@Column(nullable = false)
	private long size;

	@Column(length = 100)
	private String contentType;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Attachment() {
	}

	public Attachment(Post post, String originalName, String storedName, long size, String contentType) {
		this.post = post;
		this.originalName = originalName;
		this.storedName = storedName;
		this.size = size;
		this.contentType = contentType;
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public Post getPost() { return post; }
	public String getOriginalName() { return originalName; }
	public String getStoredName() { return storedName; }
	public long getSize() { return size; }
	public String getContentType() { return contentType; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}

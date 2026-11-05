package th_prj.th_company.file;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 전자결재, 일정, 예약의 첨부파일. 실제 파일은 FileStorage 에 storedName 으로 저장된다.
 */
@Entity
@Table(name = "file_attachment", indexes = @Index(name = "idx_file_owner", columnList = "ownerType, ownerId"))
public class FileAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private FileOwner ownerType;

	@Column(nullable = false)
	private Long ownerId;

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

	protected FileAttachment() {
	}

	FileAttachment(FileOwner ownerType, Long ownerId, String originalName, String storedName, long size, String contentType) {
		this.ownerType = ownerType;
		this.ownerId = ownerId;
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
	public FileOwner getOwnerType() { return ownerType; }
	public Long getOwnerId() { return ownerId; }
	public String getOriginalName() { return originalName; }
	public String getStoredName() { return storedName; }
	public long getSize() { return size; }
	public String getContentType() { return contentType; }
}

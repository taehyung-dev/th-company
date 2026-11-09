package th_prj.th_company.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import th_prj.th_company.member.Member;

/**
 * 상단 종 아이콘의 알림 한 건. link 는 서버가 만든 앱 안 경로다.
 */
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_recipient", columnList = "recipient_id, readAt"))
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id", nullable = false)
	private Member recipient;

	/** 결재, 휴가, 예약, 일정, 게시판 */
	@Column(nullable = false, length = 20)
	private String category;

	@Column(nullable = false, length = 300)
	private String message;

	@Column(nullable = false, length = 200)
	private String link;

	private LocalDateTime readAt;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Notification() {
	}

	Notification(Member recipient, String category, String message, String link, LocalDateTime createdAt) {
		this.recipient = recipient;
		this.category = category;
		this.message = message;
		this.link = link;
		this.createdAt = createdAt;
	}

	void read(LocalDateTime at) {
		if (readAt == null) {
			readAt = at;
		}
	}

	public Long getId() { return id; }
	public Member getRecipient() { return recipient; }
	public String getCategory() { return category; }
	public String getMessage() { return message; }
	public String getLink() { return link; }
	public LocalDateTime getReadAt() { return readAt; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}

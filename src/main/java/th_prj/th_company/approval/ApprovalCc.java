package th_prj.th_company.approval;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import th_prj.th_company.member.Member;

/**
 * 참조자. 결재선에는 들어가지 않고 상신된 문서를 볼 수 있다.
 */
@Entity
@Table(name = "approval_cc", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "member_id"}))
public class ApprovalCc {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false)
	private ApprovalDocument document;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	private LocalDateTime readAt;

	protected ApprovalCc() {
	}

	public ApprovalCc(ApprovalDocument document, Member member) {
		this.document = document;
		this.member = member;
	}

	void read() {
		if (readAt == null) {
			readAt = LocalDateTime.now();
		}
	}

	public ApprovalDocument getDocument() { return document; }
	public Member getMember() { return member; }
	public LocalDateTime getReadAt() { return readAt; }
}

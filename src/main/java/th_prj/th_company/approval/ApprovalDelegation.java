package th_prj.th_company.approval;

import java.time.LocalDate;
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

/**
 * 대결 설정: 기간 동안 delegate(대결자)가 delegator(원 결재자) 차례의 문서를 대신 결재할 수 있다.
 */
@Entity
@Table(name = "approval_delegation")
public class ApprovalDelegation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "delegator_id", nullable = false)
	private Member delegator;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "delegate_id", nullable = false)
	private Member delegate;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	@Column(length = 200)
	private String reason;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ApprovalDelegation() {
	}

	public ApprovalDelegation(Member delegator, Member delegate, LocalDate startDate, LocalDate endDate, String reason) {
		this.delegator = delegator;
		this.delegate = delegate;
		this.startDate = startDate;
		this.endDate = endDate;
		this.reason = reason;
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public Member getDelegator() { return delegator; }
	public Member getDelegate() { return delegate; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public String getReason() { return reason; }
}

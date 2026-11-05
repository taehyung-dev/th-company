package th_prj.th_company.leave;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import th_prj.th_company.member.Member;

/**
 * 휴가 신청 한 건. 신청하면 휴가신청서가 전자결재로 올라가고, 그 문서의 결과가 여기에 반영된다.
 */
@Entity
@Table(name = "leave_request", indexes = {
	@Index(name = "idx_leave_member_period", columnList = "member_id, startDate, endDate"),
	@Index(name = "idx_leave_document", columnList = "documentId")})
public class LeaveRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LeaveType type;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	/** 주말과 공휴일을 뺀 일수 (신청 시점 기준) */
	@Column(nullable = false)
	private double days;

	@Column(length = 500)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LeaveStatus status = LeaveStatus.PENDING;

	/** 휴가신청서 문서 번호(id) */
	private Long documentId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected LeaveRequest() {
	}

	public LeaveRequest(Member member, LeaveType type, LocalDate startDate, LocalDate endDate, double days, String reason) {
		this.member = member;
		this.type = type;
		this.startDate = startDate;
		this.endDate = endDate;
		this.days = days;
		this.reason = reason;
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

	void linkDocument(Long documentId) {
		this.documentId = documentId;
	}

	void changeStatus(LeaveStatus status) {
		this.status = status;
	}

	public Long getId() { return id; }
	public Member getMember() { return member; }
	public LeaveType getType() { return type; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }
	public double getDays() { return days; }
	public String getReason() { return reason; }
	public LeaveStatus getStatus() { return status; }
	public Long getDocumentId() { return documentId; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}

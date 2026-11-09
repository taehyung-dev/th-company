package th_prj.th_company.approval;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 결재선의 한 사람. step 은 1 부터 매긴 순번이고, 실제로 처리하는 차례는 stage 다.
 * 같은 stage 의 사람들(병렬 합의)은 동시에 차례가 되고, 모두 합의해야 다음 stage 로 넘어간다.
 * kind 와 stage 는 합의 기능 전에 만든 문서에는 비어 있어서, 결재와 step 으로 읽는다.
 */
@Entity
@Table(name = "approval_line", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "step"}))
public class ApprovalLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false)
	private ApprovalDocument document;

	@Column(nullable = false)
	private int step;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private LineKind kind;

	private Integer stage;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "approver_id", nullable = false)
	private Member approver;

	/** 상신 시점의 결재자 부서/직급 */
	@Column(length = 50)
	private String approverDeptName;

	@Column(length = 50)
	private String approverGradeName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LineStatus status = LineStatus.WAITING;

	@Column(length = 500)
	private String comment;

	private LocalDateTime actedAt;

	/** 대결: 원 결재자 대신 결재한 사람 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "acted_by_id")
	private Member actedBy;

	/** 전결: 이 단계에서 문서를 끝냈다 */
	@Column(nullable = false)
	private boolean finalApproval;

	protected ApprovalLine() {
	}

	public ApprovalLine(ApprovalDocument document, int step, Member approver) {
		this(document, step, approver, LineKind.APPROVAL, step);
	}

	public ApprovalLine(ApprovalDocument document, int step, Member approver, LineKind kind, int stage) {
		this.document = document;
		this.step = step;
		this.approver = approver;
		this.kind = kind;
		this.stage = stage;
	}

	void snapshot(String deptName, String gradeName) {
		this.approverDeptName = deptName;
		this.approverGradeName = gradeName;
	}

	void activate() {
		this.status = LineStatus.PENDING;
	}

	void reset() {
		this.status = LineStatus.WAITING;
		this.comment = null;
		this.actedAt = null;
		this.actedBy = null;
		this.finalApproval = false;
	}

	/** @param actedBy 대결이면 대신 결재한 사람, 아니면 null */
	void decide(LineStatus result, String comment, Member actedBy, boolean finalApproval) {
		this.status = result;
		this.comment = comment;
		this.actedAt = LocalDateTime.now();
		this.actedBy = actedBy;
		this.finalApproval = finalApproval;
	}

	void skip() {
		this.status = LineStatus.SKIPPED;
	}

	public Long getId() { return id; }
	public ApprovalDocument getDocument() { return document; }
	public int getStep() { return step; }
	public LineKind getKind() { return kind == null ? LineKind.APPROVAL : kind; }
	public int getStage() { return stage == null ? step : stage; }
	public boolean isAgreement() { return getKind() == LineKind.AGREEMENT; }
	public Member getApprover() { return approver; }
	public String getApproverDeptName() { return approverDeptName; }
	public String getApproverGradeName() { return approverGradeName; }
	public LineStatus getStatus() { return status; }
	public String getComment() { return comment; }
	public LocalDateTime getActedAt() { return actedAt; }
	public Member getActedBy() { return actedBy; }
	public boolean isFinalApproval() { return finalApproval; }
}

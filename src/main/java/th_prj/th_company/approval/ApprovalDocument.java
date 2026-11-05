package th_prj.th_company.approval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import th_prj.th_company.member.Member;

/**
 * 결재 문서. 기안자의 부서/직급은 상신 시점 값으로 남긴다.
 */
@Entity
@Table(name = "approval_document")
public class ApprovalDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "template_id", nullable = false)
	private ApprovalTemplate template;

	/** 상신할 때 부여 (예: 품의-2026-0001) */
	@Column(unique = true, length = 30)
	private String docNo;

	@Column(nullable = false, length = 200)
	private String title;

	// MariaDB MEDIUMTEXT (최대 16MB). 길이를 안 주면 TINYTEXT(255바이트)로 만들어진다
	@Lob
	@Column(nullable = false, length = 16_777_215)
	private String content;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "drafter_id", nullable = false)
	private Member drafter;

	@Column(length = 50)
	private String drafterDeptName;

	@Column(length = 50)
	private String drafterGradeName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DocStatus status = DocStatus.DRAFT;

	private LocalDateTime submittedAt;

	private LocalDateTime completedAt;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	/** 다른 기능(휴가)이 만든 문서. 회수·수정 대신 그 기능에서 취소한다 */
	@Column(nullable = false)
	private boolean linked;

	/** 양식 입력 항목에 넣은 값 (항목 이름·종류를 함께 남긴다) */
	@ElementCollection
	@CollectionTable(name = "approval_document_field", joinColumns = @JoinColumn(name = "document_id"))
	@OrderColumn(name = "field_order")
	private List<FieldValue> fields = new ArrayList<>();

	protected ApprovalDocument() {
	}

	public ApprovalDocument(ApprovalTemplate template, Member drafter, String title, String content) {
		this.template = template;
		this.drafter = drafter;
		this.title = title;
		this.content = content;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	void changeFields(List<FieldValue> values) {
		fields.clear();
		fields.addAll(values);
	}

	public void edit(String title, String content) {
		this.title = title;
		this.content = content;
	}

	void submit(String docNo, String deptName, String gradeName) {
		if (this.docNo == null) {
			this.docNo = docNo;
		}
		this.drafterDeptName = deptName;
		this.drafterGradeName = gradeName;
		this.status = DocStatus.IN_PROGRESS;
		this.submittedAt = LocalDateTime.now();
	}

	void complete(DocStatus result) {
		this.status = result;
		this.completedAt = LocalDateTime.now();
	}

	/** 회수: 보관함으로 되돌린다 (문서번호는 유지) */
	void withdraw() {
		this.status = DocStatus.DRAFT;
		this.submittedAt = null;
	}

	void markLinked() {
		this.linked = true;
	}

	public boolean isLinked() { return linked; }
	public Long getId() { return id; }
	public ApprovalTemplate getTemplate() { return template; }
	public String getDocNo() { return docNo; }
	public String getTitle() { return title; }
	public String getContent() { return content; }
	public List<FieldValue> getFields() { return List.copyOf(fields); }
	public Member getDrafter() { return drafter; }
	public String getDrafterDeptName() { return drafterDeptName; }
	public String getDrafterGradeName() { return drafterGradeName; }
	public DocStatus getStatus() { return status; }
	public LocalDateTime getSubmittedAt() { return submittedAt; }
	public LocalDateTime getCompletedAt() { return completedAt; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}

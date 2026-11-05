package th_prj.th_company.approval;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * 결재 양식 (예: 품의서). code 는 문서번호 앞자리로 쓰인다 (품의-2026-0001).
 * 관리자(또는 결재양식관리자)가 관리자 화면에서 만들고 고친다.
 */
@Entity
@Table(name = "approval_template")
public class ApprovalTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String code;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(length = 200)
	private String description;

	/** 새 문서 작성 시 본문에 미리 채워 넣는 내용 */
	@Lob
	@Column(length = 16_777_215)
	private String contentTemplate;

	/** 기안할 때 본문 위에 나오는 입력 항목 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "approval_template_field", joinColumns = @JoinColumn(name = "template_id"))
	@OrderColumn(name = "field_order")
	private List<TemplateField> fields = new ArrayList<>();

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean enabled = true;

	protected ApprovalTemplate() {
	}

	public ApprovalTemplate(String code, String name, String description, String contentTemplate, int sortOrder) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.contentTemplate = contentTemplate;
		this.sortOrder = sortOrder;
	}

	void update(String code, String name, String description, String contentTemplate, int sortOrder, boolean enabled,
			List<TemplateField> fields) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.contentTemplate = contentTemplate;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
		this.fields.clear();
		this.fields.addAll(fields);
	}

	public Long getId() { return id; }
	public String getCode() { return code; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public String getContentTemplate() { return contentTemplate; }
	public List<TemplateField> getFields() { return List.copyOf(fields); }
	public int getSortOrder() { return sortOrder; }
	public boolean isEnabled() { return enabled; }
}

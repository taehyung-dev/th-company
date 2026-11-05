package th_prj.th_company.approval;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 관리자 > 결재 양식 등록/수정 */
public class ApprovalTemplateForm {

	private Long id;

	@NotBlank(message = "양식 이름을 입력하세요.")
	@Size(max = 50, message = "양식 이름은 50자 이하로 입력하세요.")
	private String name;

	/** 문서번호 앞자리 (품의-2026-0001 의 "품의") */
	@NotBlank(message = "문서번호 앞자리를 입력하세요.")
	@Pattern(regexp = "[가-힣A-Za-z0-9]{1,10}", message = "문서번호 앞자리는 한글, 영문, 숫자로 10자 이하입니다. (- 와 공백은 쓸 수 없습니다)")
	private String code;

	@Size(max = 200, message = "설명은 200자 이하로 입력하세요.")
	private String description;

	@Size(max = 100_000, message = "본문 서식이 너무 깁니다.")
	private String contentTemplate;

	@Min(value = 0, message = "순서는 0 이상입니다.")
	@Max(value = 9999, message = "순서는 9999 이하입니다.")
	private int sortOrder = 100;

	private boolean enabled = true;

	@Valid
	private List<FieldForm> fields = new ArrayList<>();

	public static class FieldForm {

		@Size(max = 50, message = "항목 이름은 50자 이하로 입력하세요.")
		private String label;

		private FieldType type = FieldType.TEXT;

		private boolean required;

		@Size(max = 500, message = "선택지는 500자 이하로 입력하세요.")
		private String options;

		public FieldForm() {
		}

		FieldForm(TemplateField f) {
			this.label = f.getLabel();
			this.type = f.getType();
			this.required = f.isRequired();
			this.options = f.getOptions();
		}

		public String getLabel() { return label; }
		public void setLabel(String label) { this.label = label; }
		public FieldType getType() { return type; }
		public void setType(FieldType type) { this.type = type; }
		public boolean isRequired() { return required; }
		public void setRequired(boolean required) { this.required = required; }
		public String getOptions() { return options; }
		public void setOptions(String options) { this.options = options; }
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getContentTemplate() { return contentTemplate; }
	public void setContentTemplate(String contentTemplate) { this.contentTemplate = contentTemplate; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public List<FieldForm> getFields() { return fields; }
	public void setFields(List<FieldForm> fields) { this.fields = fields; }
}

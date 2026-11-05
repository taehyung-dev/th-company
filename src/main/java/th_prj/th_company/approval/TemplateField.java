package th_prj.th_company.approval;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 결재 양식의 입력 항목 (예: 휴가 종류, 시작일, 금액). 기안할 때 이 순서대로 입력칸이 나온다.
 */
@Embeddable
public class TemplateField {

	@Column(nullable = false, length = 50)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FieldType type;

	@Column(nullable = false)
	private boolean required;

	/** SELECT 의 선택지. 쉼표로 구분 */
	@Column(length = 500)
	private String options;

	protected TemplateField() {
	}

	public TemplateField(String label, FieldType type, boolean required, String options) {
		this.label = label;
		this.type = type;
		this.required = required;
		this.options = type == FieldType.SELECT ? options : null;
	}

	public List<String> optionList() {
		if (options == null || options.isBlank()) {
			return List.of();
		}
		return Arrays.stream(options.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	public String getLabel() { return label; }
	public FieldType getType() { return type; }
	public boolean isRequired() { return required; }
	public String getOptions() { return options; }
}

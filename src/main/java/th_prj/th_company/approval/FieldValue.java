package th_prj.th_company.approval;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 문서에 입력한 항목 값. 양식을 나중에 바꿔도 문서 내용이 바뀌지 않도록 항목 이름과 종류를 함께 남긴다.
 */
@Embeddable
public class FieldValue {

	@Column(nullable = false, length = 50)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FieldType type;

	@Column(name = "field_value", length = 2000)
	private String value;

	protected FieldValue() {
	}

	public FieldValue(String label, FieldType type, String value) {
		this.label = label;
		this.type = type;
		this.value = value;
	}

	/** 화면 표시용. 금액은 천 단위 쉼표와 "원" */
	public String display() {
		if (value == null || value.isBlank()) {
			return "-";
		}
		if (type == FieldType.MONEY || type == FieldType.NUMBER) {
			try {
				String formatted = new DecimalFormat("#,##0.##").format(new BigDecimal(value));
				return type == FieldType.MONEY ? formatted + "원" : formatted;
			} catch (NumberFormatException e) {
				return value;
			}
		}
		return value;
	}

	public String getLabel() { return label; }
	public FieldType getType() { return type; }
	public String getValue() { return value; }
}

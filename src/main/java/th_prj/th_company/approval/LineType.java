package th_prj.th_company.approval;

/**
 * 결재선 지정 화면에서 고르는 방식. 저장할 때 LineKind 와 단계(stage)로 바뀐다.
 * 합의(병렬)가 이어져 있으면 그 사람들은 같은 단계라서 동시에 합의하고, 모두 합의해야 다음 단계로 넘어간다.
 */
public enum LineType {
	APPROVAL("결재", LineKind.APPROVAL),
	AGREE_SEQ("합의(순차)", LineKind.AGREEMENT),
	AGREE_PAR("합의(병렬)", LineKind.AGREEMENT);

	private final String label;
	private final LineKind kind;

	LineType(String label, LineKind kind) {
		this.label = label;
		this.kind = kind;
	}

	public String getLabel() { return label; }
	public LineKind getKind() { return kind; }

	/** 값이 없거나 모르는 값이면 결재 */
	public static LineType parse(String value) {
		if (value != null) {
			for (LineType t : values()) {
				if (t.name().equals(value)) {
					return t;
				}
			}
		}
		return APPROVAL;
	}
}

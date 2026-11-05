package th_prj.th_company.board;

/**
 * FOLDER 는 게시판을 묶는 상위 항목(글 작성 불가), BOARD 는 실제 게시판.
 */
public enum BoardType {
	FOLDER("폴더"),
	BOARD("게시판");

	private final String label;

	BoardType(String label) {
		this.label = label;
	}

	public String getLabel() { return label; }
}

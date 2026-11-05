package th_prj.th_company.board;

/**
 * 게시판 트리를 위에서부터 펼친 한 줄. depth 0 이 최상위.
 */
public record BoardRow(Long id, BoardType type, String name, String description, Long parentId, int depth,
		int sortOrder, boolean enabled, boolean adminOnlyWrite, long postCount) {

	public boolean isFolder() {
		return type == BoardType.FOLDER;
	}

	public boolean canWrite(boolean isAdmin) {
		return !isFolder() && (isAdmin || !adminOnlyWrite);
	}
}

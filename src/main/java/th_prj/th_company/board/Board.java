package th_prj.th_company.board;

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

/**
 * 게시판 트리의 한 항목 (폴더 또는 게시판). parent 는 항상 폴더이며 null 이면 최상위.
 */
@Entity
@Table(name = "board")
public class Board {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BoardType type;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(length = 200)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Board parent;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean enabled = true;

	/** 관리자만 글쓰기 (공지용). 이 게시판의 글은 홈 화면 공지사항에 나온다. */
	@Column(nullable = false)
	private boolean adminOnlyWrite;

	protected Board() {
	}

	public Board(BoardType type, String name, Board parent, int sortOrder) {
		this.type = type;
		this.name = name;
		this.parent = parent;
		this.sortOrder = sortOrder;
	}

	public void update(String name, String description, Board parent, int sortOrder,
			boolean enabled, boolean adminOnlyWrite) {
		this.name = name;
		this.description = description;
		this.parent = parent;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
		this.adminOnlyWrite = adminOnlyWrite;
	}

	public boolean isFolder() { return type == BoardType.FOLDER; }

	public Long getId() { return id; }
	public BoardType getType() { return type; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public Board getParent() { return parent; }
	public int getSortOrder() { return sortOrder; }
	public boolean isEnabled() { return enabled; }
	public boolean isAdminOnlyWrite() { return adminOnlyWrite; }
}

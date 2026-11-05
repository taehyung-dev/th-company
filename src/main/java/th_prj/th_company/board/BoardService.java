package th_prj.th_company.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.common.BusinessException;

/**
 * 게시판 트리 (관리자 설정 + 사용자 화면 좌측 목록).
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

	private final BoardRepository boardRepository;
	private final PostRepository postRepository;

	public BoardService(BoardRepository boardRepository, PostRepository postRepository) {
		this.boardRepository = boardRepository;
		this.postRepository = postRepository;
	}

	/** 전체 트리 (관리자용, 사용 안 함 포함) */
	public List<BoardRow> tree() {
		List<Board> all = boardRepository.findAllByOrderBySortOrderAscNameAsc();

		Map<Long, Long> postCounts = new HashMap<>();
		for (Object[] row : postRepository.countGroupByBoard()) {
			postCounts.put((Long) row[0], (Long) row[1]);
		}

		Map<Long, List<Board>> childrenByParent = new HashMap<>();
		for (Board b : all) {
			childrenByParent.computeIfAbsent(parentIdOf(b), k -> new ArrayList<>()).add(b);
		}

		List<BoardRow> rows = new ArrayList<>();
		appendChildren(null, 0, childrenByParent, postCounts, rows);
		return rows;
	}

	private void appendChildren(Long parentId, int depth, Map<Long, List<Board>> childrenByParent,
			Map<Long, Long> postCounts, List<BoardRow> rows) {
		for (Board b : childrenByParent.getOrDefault(parentId, List.of())) {
			rows.add(new BoardRow(b.getId(), b.getType(), b.getName(), b.getDescription(), parentId, depth,
				b.getSortOrder(), b.isEnabled(), b.isAdminOnlyWrite(), postCounts.getOrDefault(b.getId(), 0L)));
			appendChildren(b.getId(), depth + 1, childrenByParent, postCounts, rows);
		}
	}

	/** 사용자 화면 트리: 사용 안 함 항목과 그 하위는 뺀다. */
	public List<BoardRow> visibleTree() {
		List<BoardRow> visible = new ArrayList<>();
		int hiddenDepth = Integer.MAX_VALUE;
		for (BoardRow r : tree()) {
			if (r.depth() > hiddenDepth) {
				continue;
			}
			hiddenDepth = Integer.MAX_VALUE;
			if (!r.enabled()) {
				hiddenDepth = r.depth();
				continue;
			}
			visible.add(r);
		}
		return visible;
	}

	/** 사용자에게 보이는 게시판(폴더 아님). 없으면 404. */
	public BoardRow visibleBoard(Long boardId) {
		return visibleTree().stream()
			.filter(r -> r.id().equals(boardId) && !r.isFolder())
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** 연관관계 지정용 참조 (조회 쿼리 없음) */
	Board reference(Long id) {
		return boardRepository.getReferenceById(id);
	}

	public BoardForm getForm(Long id) {
		Board b = find(id);
		BoardForm form = new BoardForm();
		form.setId(b.getId());
		form.setType(b.getType());
		form.setName(b.getName());
		form.setDescription(b.getDescription());
		form.setParentId(parentIdOf(b));
		form.setSortOrder(b.getSortOrder());
		form.setEnabled(b.isEnabled());
		form.setAdminOnlyWrite(b.isAdminOnlyWrite());
		return form;
	}

	@Transactional
	public void save(BoardForm form) {
		Board parent = form.getParentId() == null ? null : find(form.getParentId());
		if (parent != null && !parent.isFolder()) {
			throw new BusinessException("상위 항목은 폴더만 지정할 수 있습니다.");
		}
		String name = form.getName().trim();
		String description = form.getDescription() == null || form.getDescription().isBlank()
			? null : form.getDescription().trim();

		Board b;
		if (form.getId() == null) {
			b = boardRepository.save(new Board(form.getType(), name, parent, form.getSortOrder()));
		} else {
			b = find(form.getId());
			if (parent != null && isSelfOrDescendant(parent, b.getId())) {
				throw new BusinessException("자기 자신이나 하위 폴더를 상위 항목으로 지정할 수 없습니다.");
			}
		}
		boolean adminOnlyWrite = !b.isFolder() && form.isAdminOnlyWrite();
		b.update(name, description, parent, form.getSortOrder(), form.isEnabled(), adminOnlyWrite);
	}

	@Transactional
	public void delete(Long id) {
		Board b = find(id);
		if (boardRepository.existsByParentId(id)) {
			throw new BusinessException("하위 항목이 있는 폴더는 삭제할 수 없습니다.");
		}
		if (postRepository.countByBoardId(id) > 0) {
			throw new BusinessException("글이 있는 게시판은 삭제할 수 없습니다. 사용 안 함으로 변경하세요.");
		}
		boardRepository.delete(b);
	}

	private boolean isSelfOrDescendant(Board candidate, Long targetId) {
		for (Board cur = candidate; cur != null; cur = cur.getParent()) {
			if (cur.getId().equals(targetId)) {
				return true;
			}
		}
		return false;
	}

	private Board find(Long id) {
		return boardRepository.findById(id)
			.orElseThrow(() -> new BusinessException("게시판을 찾을 수 없습니다."));
	}

	private static Long parentIdOf(Board b) {
		return b.getParent() == null ? null : b.getParent().getId();
	}
}

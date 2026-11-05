package th_prj.th_company.board;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

	String SUMMARY = """
		select new th_prj.th_company.board.PostSummary(p.id, p.title, a.name, p.createdAt, p.viewCount, p.pinned,
			(select count(c) from Comment c where c.post = p),
			(select count(f) from Attachment f where f.post = p))
		from Post p join p.author a
		""";

	/**
	 * 게시판 글 목록 (고정글 제외, 검색 시에는 고정글 포함).
	 * field: title / content / author / all(제목+내용). keyword 가 null 이면 검색하지 않는다.
	 */
	String SEARCH_WHERE = """
		where p.board.id = :boardId
			and (p.pinned = false or :keyword is not null)
			and (:keyword is null
				or ((:field = 'title' or :field = 'all') and p.title like concat('%', :keyword, '%'))
				or ((:field = 'content' or :field = 'all') and p.content like concat('%', :keyword, '%'))
				or (:field = 'author' and a.name like concat('%', :keyword, '%')))
		""";

	@Query(value = SUMMARY + SEARCH_WHERE + " order by p.id desc",
		countQuery = "select count(p) from Post p join p.author a " + SEARCH_WHERE)
	Page<PostSummary> search(@Param("boardId") Long boardId, @Param("field") String field,
			@Param("keyword") String keyword, Pageable pageable);

	@Query(SUMMARY + " where p.board.id = :boardId and p.pinned = true order by p.id desc")
	List<PostSummary> findPinned(@Param("boardId") Long boardId);

	@Query("""
		select p from Post p
		join fetch p.board
		join fetch p.author a
		left join fetch a.department
		left join fetch a.grade
		where p.id = :id
		""")
	Optional<Post> findDetail(@Param("id") Long id);

	@Modifying
	@Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
	void increaseViewCount(@Param("id") Long id);

	/** 홈 화면용 최근 글 (지정한 게시판들) */
	@Query("select p from Post p join fetch p.board where p.board.id in :boardIds order by p.id desc")
	List<Post> findLatest(@Param("boardIds") List<Long> boardIds, Pageable pageable);

	long countByBoardId(Long boardId);

	/** [게시판 id, 글 수] 목록 */
	@Query("select p.board.id, count(p) from Post p group by p.board.id")
	List<Object[]> countGroupByBoard();
}

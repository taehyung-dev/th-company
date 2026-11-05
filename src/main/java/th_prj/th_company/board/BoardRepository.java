package th_prj.th_company.board;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, Long> {

	List<Board> findAllByOrderBySortOrderAscNameAsc();

	boolean existsByParentId(Long parentId);
}

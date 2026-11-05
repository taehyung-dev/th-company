package th_prj.th_company.board;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@Query("""
		select c from Comment c
		join fetch c.author a
		left join fetch a.department
		where c.post.id = :postId
		order by c.id
		""")
	List<Comment> findByPostId(@Param("postId") Long postId);

	@Modifying
	@Query("delete from Comment c where c.post.id = :postId")
	void deleteByPostId(@Param("postId") Long postId);
}

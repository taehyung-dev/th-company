package th_prj.th_company.board;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	List<Attachment> findByPostIdOrderById(Long postId);

	@Query("select f from Attachment f join fetch f.post p join fetch p.board where f.id = :id")
	Optional<Attachment> findWithBoard(@Param("id") Long id);
}

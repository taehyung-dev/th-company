package th_prj.th_company.approval;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {

	@Query("""
		select l from ApprovalLine l
		join fetch l.approver a
		left join fetch a.department
		left join fetch a.grade
		left join fetch l.actedBy
		where l.document.id = :documentId
		order by l.step
		""")
	List<ApprovalLine> findByDocument(@Param("documentId") Long documentId);

	@Query("select l from ApprovalLine l join fetch l.approver where l.document.id in :documentIds order by l.step")
	List<ApprovalLine> findByDocuments(@Param("documentIds") Collection<Long> documentIds);

	@Modifying
	@Query("delete from ApprovalLine l where l.document.id = :documentId")
	void deleteByDocument(@Param("documentId") Long documentId);
}

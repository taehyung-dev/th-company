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

	/** 이 사람이 앞으로 결재해야 할 진행 중 문서 번호 */
	@Query("""
		select distinct d.docNo from ApprovalLine l
		join l.document d
		where l.approver.id = :memberId and d.status = :status and l.status in :waiting
		order by d.docNo
		""")
	List<String> findWaitingDocNos(@Param("memberId") Long memberId, @Param("status") DocStatus status,
			@Param("waiting") Collection<LineStatus> waiting);

	@Modifying
	@Query("delete from ApprovalLine l where l.document.id = :documentId")
	void deleteByDocument(@Param("documentId") Long documentId);
}

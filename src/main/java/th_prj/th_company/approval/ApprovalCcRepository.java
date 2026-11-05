package th_prj.th_company.approval;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalCcRepository extends JpaRepository<ApprovalCc, Long> {

	@Query("""
		select c from ApprovalCc c
		join fetch c.member m
		left join fetch m.department
		left join fetch m.grade
		where c.document.id = :documentId
		order by c.id
		""")
	List<ApprovalCc> findByDocument(@Param("documentId") Long documentId);

	Optional<ApprovalCc> findByDocumentIdAndMemberLoginId(Long documentId, String loginId);

	/** 아직 안 읽은 참조 문서 수 (회수된 문서 제외) */
	@Query("""
		select count(c) from ApprovalCc c
		where c.member.id = :memberId and c.readAt is null and c.document.status <> :draft
		""")
	long countUnread(@Param("memberId") Long memberId, @Param("draft") DocStatus draft);

	@Modifying
	@Query("delete from ApprovalCc c where c.document.id = :documentId")
	void deleteByDocument(@Param("documentId") Long documentId);
}

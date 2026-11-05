package th_prj.th_company.approval;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 결재함별 문서 조회. 함 구분 규칙은 ApprovalBox 참고.
 */
public interface ApprovalDocumentRepository extends JpaRepository<ApprovalDocument, Long> {

	String SELECT = "select d from ApprovalDocument d join fetch d.template join fetch d.drafter ";
	String COUNT = "select count(d) from ApprovalDocument d ";

	/** 미결: 지금 내가 결재할 차례. 대결을 맡긴 사람(delegators) 차례도 포함하되, 내가 기안한 문서는 대결할 수 없다 */
	String PENDING_WHERE = """
		where d.status = :inProgress
			and exists (select l from ApprovalLine l where l.document = d and l.status = :pending
				and (l.approver.id = :memberId or (l.approver.id in :delegators and d.drafter.id <> :memberId)))
		""";

	/** 진행: 진행중인 문서 중 내가 기안했거나 결재선에 있는 문서 (내 차례인 것은 미결로) */
	String PROGRESS_WHERE = """
		where d.status = :inProgress
			and (d.drafter.id = :memberId
				or exists (select l from ApprovalLine l where l.document = d
					and ((l.approver.id = :memberId and l.status <> :pending) or l.actedBy.id = :memberId)))
		""";

	/** 완결: 승인 또는 반려로 끝난 문서 중 내가 기안했거나 결재선에 있는 문서 */
	String COMPLETED_WHERE = """
		where d.status in :completed
			and (d.drafter.id = :memberId
				or exists (select l from ApprovalLine l where l.document = d
					and (l.approver.id = :memberId or l.actedBy.id = :memberId)))
		""";

	/** 참조: 상신된 문서 중 내가 참조자인 문서 */
	String CC_WHERE = """
		where d.status <> :draft
			and exists (select c from ApprovalCc c where c.document = d and c.member.id = :memberId)
		""";

	/** 보관함: 내 임시저장/회수 문서 */
	String DRAFT_WHERE = "where d.status = :draft and d.drafter.id = :memberId ";

	@Query(value = SELECT + PENDING_WHERE + "order by d.submittedAt desc", countQuery = COUNT + PENDING_WHERE)
	Page<ApprovalDocument> findPending(@Param("memberId") Long memberId, @Param("delegators") Collection<Long> delegators,
			@Param("inProgress") DocStatus inProgress, @Param("pending") LineStatus pending, Pageable pageable);

	@Query(value = SELECT + PROGRESS_WHERE + "order by d.submittedAt desc", countQuery = COUNT + PROGRESS_WHERE)
	Page<ApprovalDocument> findInProgress(@Param("memberId") Long memberId, @Param("inProgress") DocStatus inProgress,
			@Param("pending") LineStatus pending, Pageable pageable);

	@Query(value = SELECT + COMPLETED_WHERE + "order by d.completedAt desc", countQuery = COUNT + COMPLETED_WHERE)
	Page<ApprovalDocument> findCompleted(@Param("memberId") Long memberId,
			@Param("completed") Collection<DocStatus> completed, Pageable pageable);

	@Query(value = SELECT + DRAFT_WHERE + "order by d.updatedAt desc", countQuery = COUNT + DRAFT_WHERE)
	Page<ApprovalDocument> findDrafts(@Param("memberId") Long memberId, @Param("draft") DocStatus draft,
			Pageable pageable);

	@Query(COUNT + PENDING_WHERE)
	long countPending(@Param("memberId") Long memberId, @Param("delegators") Collection<Long> delegators,
			@Param("inProgress") DocStatus inProgress, @Param("pending") LineStatus pending);

	@Query(value = SELECT + CC_WHERE + "order by d.submittedAt desc", countQuery = COUNT + CC_WHERE)
	Page<ApprovalDocument> findCc(@Param("memberId") Long memberId, @Param("draft") DocStatus draft, Pageable pageable);

	@Query(COUNT + DRAFT_WHERE)
	long countDrafts(@Param("memberId") Long memberId, @Param("draft") DocStatus draft);

	@Query("""
		select d from ApprovalDocument d
		join fetch d.template
		join fetch d.drafter m
		left join fetch m.department
		left join fetch m.grade
		where d.id = :id
		""")
	Optional<ApprovalDocument> findDetail(@Param("id") Long id);

	long countByDocNoStartingWith(String prefix);

	long countByTemplateId(Long templateId);
}

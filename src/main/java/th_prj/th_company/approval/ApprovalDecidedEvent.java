package th_prj.th_company.approval;

/**
 * 결재가 끝났을 때 (승인 또는 반려) 같은 트랜잭션 안에서 알리는 이벤트.
 */
public record ApprovalDecidedEvent(Long documentId, DocStatus status) {
}

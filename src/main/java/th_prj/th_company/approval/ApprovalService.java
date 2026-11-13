package th_prj.th_company.approval;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.board.PostDetail.FileInfo;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.file.FileAttachmentService;
import th_prj.th_company.file.FileOwner;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.notification.NotificationService;
import th_prj.th_company.org.DepartmentRow;
import th_prj.th_company.org.DepartmentService;

/**
 * 전자결재 기본 프로세스.
 * <pre>
 * 임시저장(DRAFT) → 상신(IN_PROGRESS, 1단계 PENDING) → 단계별 승인 → 마지막 승인 시 APPROVED
 *                                                 ↘ 누구든 반려 시 REJECTED
 * 아무도 승인하기 전이면 기안자가 회수 → DRAFT (보관함)
 * 반려되면 기안자가 재기안 → 내용을 복사한 새 DRAFT (보관함). 원래 문서는 그대로 남는다
 * </pre>
 * <ul>
 *   <li>참조자: 결재하지 않고 상신된 문서를 볼 수 있다 (참조문서함).</li>
 *   <li>대결: 결재자가 지정해 둔 대결자가 그 기간에 대신 승인·반려한다. 원 결재자도 그대로 결재할 수 있다.
 *       자기가 기안한 문서는 대결할 수 없다.</li>
 *   <li>전결: 결재자 본인이 자기 단계에서 문서를 최종 승인한다. 뒤 단계는 SKIPPED. 대결자와 합의자는 전결할 수 없다.</li>
 *   <li>합의: 결재선 중간에 합의자를 둔다. 순차 합의는 한 사람씩, 병렬 합의는 이어진 합의자들이 동시에 합의하고
 *       모두 합의해야 다음 단계로 넘어간다. 합의자가 반려하면 문서가 반려된다. 최종 결재자는 합의자일 수 없다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ApprovalService {

	public static final int PAGE_SIZE = 15;
	public static final int MAX_APPROVERS = 10;
	public static final int MAX_CC = 20;

	private final ApprovalTemplateRepository templateRepository;
	private final ApprovalDocumentRepository documentRepository;
	private final ApprovalLineRepository lineRepository;
	private final MemberRepository memberRepository;
	private final DepartmentService departmentService;
	private final ApprovalCcRepository ccRepository;
	private final ApprovalDelegationRepository delegationRepository;
	private final FileAttachmentService fileService;
	private final NotificationService notificationService;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public ApprovalService(ApprovalTemplateRepository templateRepository,
			ApprovalDocumentRepository documentRepository, ApprovalLineRepository lineRepository,
			MemberRepository memberRepository, DepartmentService departmentService,
			ApprovalCcRepository ccRepository, ApprovalDelegationRepository delegationRepository,
			FileAttachmentService fileService, NotificationService notificationService,
			ApplicationEventPublisher events, Clock clock) {
		this.templateRepository = templateRepository;
		this.documentRepository = documentRepository;
		this.lineRepository = lineRepository;
		this.memberRepository = memberRepository;
		this.departmentService = departmentService;
		this.ccRepository = ccRepository;
		this.delegationRepository = delegationRepository;
		this.fileService = fileService;
		this.notificationService = notificationService;
		this.events = events;
		this.clock = clock;
	}

	// ---------- 문서함 ----------

	public Page<DocRow> box(ApprovalBox box, String loginId, int page) {
		Long me = member(loginId).getId();
		Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
		Page<ApprovalDocument> docs = switch (box) {
			case PENDING -> documentRepository.findPending(me, delegators(me), DocStatus.IN_PROGRESS, LineStatus.PENDING, pageable);
			case PROGRESS -> documentRepository.findInProgress(me, DocStatus.IN_PROGRESS, LineStatus.PENDING, pageable);
			case COMPLETED -> documentRepository.findCompleted(me,
				List.of(DocStatus.APPROVED, DocStatus.REJECTED, DocStatus.CANCELED), pageable);
			case CC -> documentRepository.findCc(me, DocStatus.DRAFT, pageable);
			case DRAFTS -> documentRepository.findDrafts(me, DocStatus.DRAFT, pageable);
		};

		// 병렬 합의 중이면 차례인 사람이 여럿이다
		Map<Long, String> currentApprovers = docs.isEmpty() ? Map.of()
			: lineRepository.findByDocuments(docs.map(ApprovalDocument::getId).toList()).stream()
				.filter(l -> l.getStatus() == LineStatus.PENDING)
				.collect(Collectors.groupingBy(l -> l.getDocument().getId(),
					Collectors.mapping(l -> l.getApprover().getName(), Collectors.joining(", "))));

		return docs.map(d -> new DocRow(d.getId(), d.getDocNo(), d.getTemplate().getName(), d.getTitle(),
			d.getDrafter().getName(), dateFor(box, d), d.getStatus(), currentApprovers.get(d.getId())));
	}

	private static java.time.LocalDateTime dateFor(ApprovalBox box, ApprovalDocument d) {
		return switch (box) {
			case COMPLETED -> d.getCompletedAt();
			case DRAFTS -> d.getUpdatedAt();
			default -> d.getSubmittedAt();
		};
	}

	/** 좌측 메뉴 숫자 (문서함 path → 건수): 미결, 안 읽은 참조, 보관함 */
	public Map<String, Long> counts(String loginId) {
		Long me = member(loginId).getId();
		return Map.of(
			ApprovalBox.PENDING.getPath(), documentRepository.countPending(me, delegators(me), DocStatus.IN_PROGRESS, LineStatus.PENDING),
			ApprovalBox.CC.getPath(), ccRepository.countUnread(me, DocStatus.DRAFT),
			ApprovalBox.DRAFTS.getPath(), documentRepository.countDrafts(me, DocStatus.DRAFT));
	}

	/** 홈 화면: 미결 문서 수와 최근 몇 건 */
	public Page<DocRow> pendingForHome(String loginId, int limit) {
		Long me = member(loginId).getId();
		return documentRepository.findPending(me, delegators(me), DocStatus.IN_PROGRESS, LineStatus.PENDING, PageRequest.of(0, limit))
			.map(d -> new DocRow(d.getId(), d.getDocNo(), d.getTemplate().getName(), d.getTitle(),
				d.getDrafter().getName(), d.getSubmittedAt(), d.getStatus(), null));
	}

	/** 이 사람의 결재를 기다리는 진행 중 문서 (지금 차례이거나 앞 단계 뒤에 대기 중) */
	public List<String> waitingDocNos(Long memberId) {
		return lineRepository.findWaitingDocNos(memberId, DocStatus.IN_PROGRESS, List.of(LineStatus.PENDING, LineStatus.WAITING));
	}

	// ---------- 양식 / 결재선 후보 ----------

	public List<ApprovalTemplate> templates() {
		return templateRepository.findByEnabledTrueOrderBySortOrderAscNameAsc();
	}

	/** 새 문서를 쓸 수 있는 (사용 중인) 양식 */
	public ApprovalTemplate template(Long id) {
		return templateRepository.findById(id)
			.filter(ApprovalTemplate::isEnabled)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** 작성 화면 표시용. 보관함 문서는 양식이 사용 중지되어도 고칠 수 있다 */
	public ApprovalTemplate templateForForm(DraftForm form) {
		return form.getId() == null ? template(form.getTemplateId())
			: templateRepository.findById(form.getTemplateId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** 결재선 팝업의 조직도: 부서 트리 순서, 부서 안에서는 직책→직급→이름. 본인은 제외. */
	public List<ApproverGroup> approverGroups(String loginId) {
		Map<Long, List<Member>> byDept = memberRepository.findActiveWithDepartment().stream()
			.filter(m -> !m.getLoginId().equals(loginId))
			.collect(Collectors.groupingBy(m -> m.getDepartment().getId()));

		Comparator<Member> order = Comparator
			.comparingInt((Member m) -> m.getDuty() == null ? Integer.MAX_VALUE : m.getDuty().getSortOrder())
			.thenComparingInt(m -> m.getGrade() == null ? Integer.MAX_VALUE : m.getGrade().getSortOrder())
			.thenComparing(Member::getName);

		List<ApproverGroup> groups = new ArrayList<>();
		int hiddenDepth = Integer.MAX_VALUE;
		for (DepartmentRow d : departmentService.tree()) {
			// 사용 안 함 부서는 하위 부서까지 숨긴다
			if (d.depth() > hiddenDepth) {
				continue;
			}
			hiddenDepth = Integer.MAX_VALUE;
			if (!d.enabled()) {
				hiddenDepth = d.depth();
				continue;
			}
			groups.add(new ApproverGroup(d.id(), d.name(), d.depth(),
				byDept.getOrDefault(d.id(), List.of()).stream().sorted(order)
					.map(ApprovalService::option)
					.toList()));
		}
		return groups;
	}

	/** 결재선 화면 표시용 (지정한 순서 유지) */
	public List<ApproverGroup.Option> approverLabels(List<Long> memberIds) {
		Map<Long, Member> found = memberRepository.findAllById(memberIds).stream()
			.collect(Collectors.toMap(Member::getId, m -> m));
		return memberIds.stream()
			.filter(found::containsKey)
			.map(id -> option(found.get(id)))
			.toList();
	}

	/** 미리보기의 기안자 정보 */
	public ApproverGroup.Option drafterInfo(String loginId) {
		return option(member(loginId));
	}

	private static ApproverGroup.Option option(Member m) {
		return new ApproverGroup.Option(m.getId(), label(m), m.getName(), gradeName(m), deptName(m));
	}

	private static String label(Member m) {
		StringBuilder sb = new StringBuilder(m.getName());
		if (m.getGrade() != null) {
			sb.append(' ').append(m.getGrade().getName());
		}
		if (m.getDepartment() != null) {
			sb.append(" (").append(m.getDepartment().getName()).append(')');
		}
		return sb.toString();
	}

	// ---------- 기안 작성 ----------

	public DraftForm newDraft(Long templateId) {
		ApprovalTemplate t = template(templateId);
		DraftForm form = new DraftForm();
		form.setTemplateId(t.getId());
		form.setContent(t.getContentTemplate());
		form.setFieldValues(new ArrayList<>(t.getFields().stream().map(f -> "").toList()));
		return form;
	}

	/** 보관함 문서 수정용 */
	public DraftForm getDraft(Long docId, String loginId) {
		ApprovalDocument d = editableDraft(docId, loginId);
		DraftForm form = new DraftForm();
		form.setId(d.getId());
		form.setTemplateId(d.getTemplate().getId());
		form.setTitle(d.getTitle());
		form.setContent(d.getContent());
		// 저장 후 양식 항목이 바뀌었을 수 있으므로 항목 이름으로 맞춘다
		Map<String, String> saved = new HashMap<>();
		d.getFields().forEach(v -> saved.putIfAbsent(v.getLabel(), v.getValue()));
		form.setFieldValues(new ArrayList<>(d.getTemplate().getFields().stream()
			.map(f -> saved.getOrDefault(f.getLabel(), "")).toList()));
		List<ApprovalLine> lines = lineRepository.findByDocument(docId);
		form.setApproverIds(lines.stream().map(l -> l.getApprover().getId()).toList());
		form.setLineTypes(lineTypes(lines));
		form.setCcIds(ccRepository.findByDocument(docId).stream().map(c -> c.getMember().getId()).toList());
		return form;
	}

	/** 임시저장 (보관함). 저장된 문서 id 반환. */
	@Transactional
	public Long saveDraft(DraftForm form, String loginId) {
		return saveDraft(form, loginId, List.of());
	}

	@Transactional
	public Long saveDraft(DraftForm form, String loginId, List<MultipartFile> uploads) {
		ApprovalDocument doc = save(form, loginId, false, null);
		fileService.sync(FileOwner.APPROVAL, doc.getId(), uploads, form.getDeleteFileIds());
		return doc.getId();
	}

	/** 상신. 결재선 1단계가 결재할 차례가 된다. */
	@Transactional
	public Long submit(DraftForm form, String loginId) {
		return submit(form, loginId, List.of());
	}

	@Transactional
	public Long submit(DraftForm form, String loginId, List<MultipartFile> uploads) {
		ApprovalDocument doc = save(form, loginId, true, null);
		fileService.sync(FileOwner.APPROVAL, doc.getId(), uploads, form.getDeleteFileIds());
		return start(doc);
	}

	/** 다른 기능(휴가)이 자기 양식으로 바로 상신한다. 양식이 사용 안 함이어도 된다 */
	@Transactional
	public Long submitLinked(String templateCode, String loginId, String title, String content, List<Long> approverIds) {
		ApprovalTemplate template = templateRepository.findByCode(templateCode)
			.orElseThrow(() -> new IllegalStateException("결재 양식이 없습니다: " + templateCode));
		DraftForm form = new DraftForm();
		form.setTitle(title);
		form.setContent(content);
		form.setApproverIds(approverIds);
		ApprovalDocument doc = save(form, loginId, true, template);
		doc.markLinked();
		return start(doc);
	}

	/** 다른 기능이 신청을 취소할 때 진행 중인 문서를 멈춘다 */
	@Transactional
	public void cancelLinked(Long docId) {
		ApprovalDocument d = lock(docId);
		if (!d.isLinked() || d.getStatus() != DocStatus.IN_PROGRESS) {
			throw new BusinessException("진행 중인 문서가 아닙니다.");
		}
		lineRepository.findByDocument(docId).stream()
			.filter(l -> l.getStatus() == LineStatus.PENDING)
			.forEach(ApprovalLine::reset);
		d.complete(DocStatus.CANCELED);
	}

	/** 저장된 결재선을 화면의 방식으로 되돌린다. 같은 단계에 합의자가 둘 이상이면 병렬 */
	private static List<String> lineTypes(List<ApprovalLine> lines) {
		return lines.stream().map(l -> {
			if (!l.isAgreement()) {
				return LineType.APPROVAL.name();
			}
			boolean parallel = lines.stream().anyMatch(o -> o != l && o.getStage() == l.getStage());
			return (parallel ? LineType.AGREE_PAR : LineType.AGREE_SEQ).name();
		}).toList();
	}

	/** 결재선 화면 표시용: 사람과 방식을 함께 (지정한 순서 유지) */
	public List<SelectedLine> selectedLine(List<Long> memberIds, List<String> types) {
		Map<Long, Member> found = memberRepository.findAllById(memberIds).stream()
			.collect(Collectors.toMap(Member::getId, m -> m));
		List<SelectedLine> result = new ArrayList<>();
		for (int i = 0; i < memberIds.size(); i++) {
			Member m = found.get(memberIds.get(i));
			if (m != null) {
				result.add(new SelectedLine(option(m), typeAt(types, i)));
			}
		}
		return result;
	}

	public record SelectedLine(ApproverGroup.Option person, LineType type) {
	}

	private static LineType typeAt(List<String> types, int i) {
		return LineType.parse(types != null && i < types.size() ? types.get(i) : null);
	}

	/** 문서번호를 매기고 결재선 첫 단계(병렬 합의면 여러 명)를 결재 차례로 만든다 */
	private Long start(ApprovalDocument doc) {
		Member drafter = doc.getDrafter();
		String prefix = doc.getTemplate().getCode() + "-" + Year.now().getValue() + "-";
		String docNo = prefix + "%04d".formatted(documentRepository.countByDocNoStartingWith(prefix) + 1);
		doc.submit(docNo, deptName(drafter), gradeName(drafter));

		List<ApprovalLine> lines = lineRepository.findByDocument(doc.getId());
		for (ApprovalLine l : lines) {
			l.reset();
			l.snapshot(deptName(l.getApprover()), gradeName(l.getApprover()));
		}
		int first = lines.get(0).getStage();
		lines.stream().filter(l -> l.getStage() == first).forEach(l -> {
			l.activate();
			notifyTurn(l);
		});
		for (ApprovalCc cc : ccRepository.findByDocument(doc.getId())) {
			notify(cc.getMember(), doc, drafter.getName() + " 님이 '" + doc.getTitle() + "' 문서의 참조자로 지정했습니다.");
		}
		return doc.getId();
	}

	/** forced: 양식을 직접 정할 때 (사용 안 함 양식도 된다). 보통은 null */
	private ApprovalDocument save(DraftForm form, String loginId, boolean submitting, ApprovalTemplate forced) {
		Member me = member(loginId);
		String title = trimToEmpty(form.getTitle());
		String content = form.getContent() == null ? "" : form.getContent();
		List<Member> approvers = approvers(form.getApproverIds(), me);
		List<LineType> types = new ArrayList<>();
		for (int i = 0; i < approvers.size(); i++) {
			types.add(typeAt(form.getLineTypes(), i));
		}
		List<Member> ccMembers = ccMembers(form.getCcIds(), me, approvers);
		ApprovalDocument existing = form.getId() == null ? null : editableDraft(form.getId(), loginId);
		ApprovalTemplate template = forced != null ? forced
			: existing == null ? template(form.getTemplateId()) : existing.getTemplate();
		List<FieldValue> values = fieldValues(template, form.getFieldValues(), submitting);

		if (submitting) {
			if (title.isEmpty()) {
				throw new BusinessException("제목을 입력하세요.");
			}
			// 입력 항목이 있는 양식은 본문을 비워도 된다
			if (content.isBlank() && template.getFields().isEmpty()) {
				throw new BusinessException("내용을 입력하세요.");
			}
			if (approvers.isEmpty()) {
				throw new BusinessException("결재자를 한 명 이상 지정하세요.");
			}
			if (types.get(types.size() - 1) != LineType.APPROVAL) {
				throw new BusinessException("최종 결재자는 합의가 아닌 결재로 지정하세요.");
			}
		}
		if (title.isEmpty()) {
			title = "(제목 없음)";
		}

		ApprovalDocument doc;
		if (existing == null) {
			doc = documentRepository.save(new ApprovalDocument(template, me, title, content));
		} else {
			doc = existing;
			doc.edit(title, content);
			lineRepository.deleteByDocument(doc.getId());
			ccRepository.deleteByDocument(doc.getId());
			lineRepository.flush();
		}
		doc.changeFields(values);
		// 이어진 병렬 합의는 같은 단계, 나머지는 한 사람씩 다음 단계
		int stage = 0;
		LineType prev = null;
		for (int i = 0; i < approvers.size(); i++) {
			LineType t = types.get(i);
			if (!(t == LineType.AGREE_PAR && prev == LineType.AGREE_PAR)) {
				stage++;
			}
			lineRepository.save(new ApprovalLine(doc, i + 1, approvers.get(i), t.getKind(), stage));
			prev = t;
		}
		ccMembers.forEach(m -> ccRepository.save(new ApprovalCc(doc, m)));
		return doc;
	}

	/**
	 * 양식 항목 순서대로 값을 맞춘다. 상신할 때는 필수 항목과 형식(숫자, 날짜, 선택지)을 검사한다.
	 * 임시저장은 비어 있거나 형식이 틀려도 그대로 저장한다.
	 */
	private static List<FieldValue> fieldValues(ApprovalTemplate template, List<String> input, boolean submitting) {
		List<TemplateField> fields = template.getFields();
		List<FieldValue> result = new ArrayList<>();
		for (int i = 0; i < fields.size(); i++) {
			TemplateField f = fields.get(i);
			String raw = input != null && i < input.size() && input.get(i) != null ? input.get(i).trim() : "";
			if (raw.length() > 2000) {
				throw new BusinessException("'" + f.getLabel() + "' 항목은 2000자 이하로 입력하세요.");
			}
			String value = raw;
			if (!raw.isEmpty() && (f.getType() == FieldType.NUMBER || f.getType() == FieldType.MONEY)) {
				value = raw.replace(",", "");
			}
			if (submitting) {
				if (f.isRequired() && value.isEmpty()) {
					throw new BusinessException("'" + f.getLabel() + "' 항목을 입력하세요.");
				}
				if (!value.isEmpty()) {
					checkFormat(f, value);
				}
			}
			result.add(new FieldValue(f.getLabel(), f.getType(), value.isEmpty() ? null : value));
		}
		return result;
	}

	private static void checkFormat(TemplateField f, String value) {
		switch (f.getType()) {
			case NUMBER, MONEY -> {
				try {
					new BigDecimal(value);
				} catch (NumberFormatException e) {
					throw new BusinessException("'" + f.getLabel() + "' 항목은 숫자로 입력하세요.");
				}
			}
			case DATE -> {
				try {
					LocalDate.parse(value);
				} catch (DateTimeParseException e) {
					throw new BusinessException("'" + f.getLabel() + "' 항목의 날짜가 올바르지 않습니다.");
				}
			}
			case SELECT -> {
				if (!f.optionList().contains(value)) {
					throw new BusinessException("'" + f.getLabel() + "' 항목은 목록에서 선택하세요.");
				}
			}
			default -> {
			}
		}
	}

	private List<Member> approvers(List<Long> ids, Member me) {
		if (ids == null) {
			return List.of();
		}
		if (ids.size() > MAX_APPROVERS) {
			throw new BusinessException("결재자는 " + MAX_APPROVERS + "명까지 지정할 수 있습니다.");
		}
		Set<Long> seen = new HashSet<>();
		List<Member> result = new ArrayList<>();
		for (Long id : ids) {
			if (id.equals(me.getId())) {
				throw new BusinessException("본인을 결재자로 지정할 수 없습니다.");
			}
			if (!seen.add(id)) {
				throw new BusinessException("같은 사람을 결재선에 두 번 넣을 수 없습니다.");
			}
			Member m = memberRepository.findById(id)
				.filter(Member::isEnabled)
				.orElseThrow(() -> new BusinessException("결재자로 지정할 수 없는 사용자가 있습니다."));
			result.add(m);
		}
		return result;
	}

	private List<Member> ccMembers(List<Long> ids, Member me, List<Member> approvers) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		if (ids.size() > MAX_CC) {
			throw new BusinessException("참조자는 " + MAX_CC + "명까지 지정할 수 있습니다.");
		}
		Set<Long> line = approvers.stream().map(Member::getId).collect(Collectors.toSet());
		Set<Long> seen = new HashSet<>();
		List<Member> result = new ArrayList<>();
		for (Long id : ids) {
			if (id.equals(me.getId())) {
				throw new BusinessException("본인을 참조자로 지정할 수 없습니다.");
			}
			if (line.contains(id)) {
				throw new BusinessException("결재선에 있는 사람은 참조자로 지정할 수 없습니다.");
			}
			if (seen.add(id)) {
				result.add(memberRepository.findById(id)
					.filter(Member::isEnabled)
					.orElseThrow(() -> new BusinessException("참조자로 지정할 수 없는 사용자가 있습니다.")));
			}
		}
		return result;
	}

	@Transactional
	public void deleteDraft(Long docId, String loginId) {
		ApprovalDocument d = editableDraft(docId, loginId);
		if (d.getDocNo() != null) {
			throw new BusinessException("한 번 상신했던 문서는 삭제할 수 없습니다.");
		}
		lineRepository.deleteByDocument(docId);
		ccRepository.deleteByDocument(docId);
		fileService.deleteAll(FileOwner.APPROVAL, docId);
		documentRepository.delete(d);
	}

	/**
	 * 재기안: 반려 문서의 내용, 결재선, 참조자, 첨부파일을 복사해 보관함에 새 문서를 만든다.
	 * 사용 중지된 결재자와 참조자는 뺀다. 이미 재기안해 보관함에 있으면 그 문서를 돌려준다.
	 */
	@Transactional
	public Long redraft(Long docId, String loginId) {
		ApprovalDocument origin = documentRepository.findDetail(docId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!origin.getDrafter().getLoginId().equals(loginId)) {
			throw new AccessDeniedException("기안자만 재기안할 수 있습니다.");
		}
		if (origin.getStatus() != DocStatus.REJECTED) {
			throw new BusinessException("반려된 문서만 재기안할 수 있습니다.");
		}
		if (origin.isLinked()) {
			throw new BusinessException("휴가 문서는 근태관리 > 내 휴가에서 다시 신청하세요.");
		}
		Optional<ApprovalDocument> existing = documentRepository.findFirstByRedraftedFromIdAndStatus(docId, DocStatus.DRAFT);
		if (existing.isPresent()) {
			return existing.get().getId();
		}

		ApprovalDocument doc = new ApprovalDocument(origin.getTemplate(), origin.getDrafter(), origin.getTitle(), origin.getContent());
		doc.redraftOf(origin);
		doc.changeFields(origin.getFields().stream()
			.map(f -> new FieldValue(f.getLabel(), f.getType(), f.getValue())).toList());
		documentRepository.save(doc);
		int step = 1;
		for (ApprovalLine l : lineRepository.findByDocument(docId)) {
			if (l.getApprover().isEnabled()) {
				lineRepository.save(new ApprovalLine(doc, step++, l.getApprover(), l.getKind(), l.getStage()));
			}
		}
		for (ApprovalCc cc : ccRepository.findByDocument(docId)) {
			if (cc.getMember().isEnabled()) {
				ccRepository.save(new ApprovalCc(doc, cc.getMember()));
			}
		}
		fileService.copyAll(FileOwner.APPROVAL, docId, doc.getId());
		return doc.getId();
	}

	// ---------- 상세 / 결재 ----------

	public DocView view(Long docId, String loginId) {
		ApprovalDocument d = documentRepository.findDetail(docId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		List<ApprovalLine> lines = lineRepository.findByDocument(docId);
		boolean isDrafter = d.getDrafter().getLoginId().equals(loginId);
		Member me = member(loginId);
		Optional<ApprovalLine> pending = d.getStatus() == DocStatus.IN_PROGRESS ? actionableLine(lines, me) : Optional.empty();
		boolean canDecide = pending.isPresent();
		boolean inLine = lines.stream().anyMatch(l -> l.getApprover().getId().equals(me.getId())
			|| (l.getActedBy() != null && l.getActedBy().getId().equals(me.getId())));
		List<ApprovalCc> ccs = ccRepository.findByDocument(docId);
		boolean isCc = ccs.stream().anyMatch(c -> c.getMember().getId().equals(me.getId()));
		if (!isDrafter && ((!inLine && !isCc && !canDecide) || d.getStatus() == DocStatus.DRAFT)) {
			throw new AccessDeniedException("이 문서를 볼 권한이 없습니다.");
		}

		boolean deputy = canDecide && !pending.get().getApprover().getId().equals(me.getId());
		boolean canFinal = canDecide && !deputy && !pending.get().isAgreement()
			&& lines.stream().anyMatch(l -> l.getStage() > pending.get().getStage());
		boolean canWithdraw = isDrafter && !d.isLinked() && d.getStatus() == DocStatus.IN_PROGRESS
			&& lines.stream().noneMatch(l -> l.getStatus() == LineStatus.APPROVED);
		boolean canEdit = isDrafter && d.getStatus() == DocStatus.DRAFT;
		boolean canRedraft = isDrafter && !d.isLinked() && d.getStatus() == DocStatus.REJECTED;
		ApprovalDocument origin = d.getRedraftedFrom();

		Member drafter = d.getDrafter();
		boolean draft = d.getStatus() == DocStatus.DRAFT;
		List<DocView.LineView> lineViews = lines.stream()
			.map(l -> new DocView.LineView(l.getStep(), l.getApprover().getName(),
				draft ? deptName(l.getApprover()) : l.getApproverDeptName(),
				draft ? gradeName(l.getApprover()) : l.getApproverGradeName(),
				l.getStatus(), l.getComment(), l.getActedAt(),
				l.getActedBy() == null ? null : l.getActedBy().getName(), l.isFinalApproval(),
				l.getKind(), l.getStage(), lines.stream().anyMatch(o -> o != l && o.getStage() == l.getStage())))
			.toList();

		return new DocView(d.getId(), d.getDocNo(), d.getTemplate().getName(), d.getTitle(), d.getContent(), d.getFields(),
			drafter.getName(),
			draft ? deptName(drafter) : d.getDrafterDeptName(),
			draft ? gradeName(drafter) : d.getDrafterGradeName(),
			d.getStatus(), d.getSubmittedAt(), d.getCompletedAt(),
			lineViews, canDecide, canWithdraw, canEdit, canFinal,
			deputy ? pending.get().getApprover().getName() : null,
			ccs.stream().map(c -> label(c.getMember())).toList(),
			canRedraft, origin == null ? null : origin.getDocNo(), origin != null && isDrafter ? origin.getId() : null,
			canDecide && pending.get().isAgreement());
	}

	/** 목록의 [결재상태 보기]. 볼 권한이 없는 문서가 섞이면 거절한다 */
	public List<DocView> statuses(List<Long> docIds, String loginId) {
		return docIds.stream().map(id -> view(id, loginId)).toList();
	}

	public List<FileInfo> files(Long docId) {
		return fileService.list(FileOwner.APPROVAL, docId);
	}

	/** 참조자가 문서를 열면 읽음으로 표시한다 */
	@Transactional
	public void markCcRead(Long docId, String loginId) {
		ccRepository.findByDocumentIdAndMemberLoginId(docId, loginId).ifPresent(ApprovalCc::read);
	}

	@Transactional
	public void approve(Long docId, String loginId, String comment) {
		ApprovalDocument doc = lock(docId);
		Member me = member(loginId);
		List<ApprovalLine> lines = myTurn(doc, me);
		ApprovalLine current = actionableLine(lines, me).orElseThrow();
		current.decide(LineStatus.APPROVED, blankToNull(comment), deputyOf(current, me), false);

		// 병렬 합의는 같은 단계의 모두가 합의해야 넘어간다
		boolean stageDone = lines.stream().filter(l -> l.getStage() == current.getStage())
			.allMatch(l -> l.getStatus() == LineStatus.APPROVED);
		if (!stageDone) {
			return;
		}
		OptionalInt next = lines.stream().mapToInt(ApprovalLine::getStage).filter(s -> s > current.getStage()).min();
		if (next.isPresent()) {
			lines.stream().filter(l -> l.getStage() == next.getAsInt()).forEach(l -> {
				l.activate();
				notifyTurn(l);
			});
		} else {
			finish(current.getDocument(), DocStatus.APPROVED);
		}
	}

	/** 전결: 본인 단계에서 최종 승인하고 뒤 단계는 건너뛴다 */
	@Transactional
	public void approveFinal(Long docId, String loginId, String comment) {
		ApprovalDocument doc = lock(docId);
		Member me = member(loginId);
		List<ApprovalLine> lines = myTurn(doc, me);
		ApprovalLine current = actionableLine(lines, me).orElseThrow();
		if (deputyOf(current, me) != null) {
			throw new BusinessException("대결자는 전결할 수 없습니다.");
		}
		if (current.isAgreement()) {
			throw new BusinessException("합의자는 전결할 수 없습니다.");
		}
		List<ApprovalLine> later = lines.stream().filter(l -> l.getStage() > current.getStage()).toList();
		if (later.isEmpty()) {
			throw new BusinessException("마지막 결재자는 [승인]으로 결재하세요.");
		}
		current.decide(LineStatus.APPROVED, blankToNull(comment), null, true);
		for (ApprovalLine l : later) {
			l.skip();
			notify(l.getApprover(), doc, me.getName() + " 님이 '" + doc.getTitle() + "' 문서를 전결했습니다.");
		}
		finish(doc, DocStatus.APPROVED);
	}

	@Transactional
	public void reject(Long docId, String loginId, String comment) {
		if (comment == null || comment.isBlank()) {
			throw new BusinessException("반려 사유를 입력하세요.");
		}
		ApprovalDocument doc = lock(docId);
		Member me = member(loginId);
		List<ApprovalLine> lines = myTurn(doc, me);
		ApprovalLine current = actionableLine(lines, me).orElseThrow();
		current.decide(LineStatus.REJECTED, comment.trim(), deputyOf(current, me), false);
		// 함께 합의하던 사람은 더 처리할 필요가 없다
		lines.stream().filter(l -> l.getStatus() == LineStatus.PENDING).forEach(ApprovalLine::reset);
		finish(current.getDocument(), DocStatus.REJECTED);
	}

	@Transactional
	public void withdraw(Long docId, String loginId) {
		ApprovalDocument d = lock(docId);
		if (!d.getDrafter().getLoginId().equals(loginId)) {
			throw new AccessDeniedException("기안자만 회수할 수 있습니다.");
		}
		if (d.isLinked()) {
			throw new BusinessException("휴가 신청 문서는 근태관리 > 내 휴가에서 취소하세요.");
		}
		List<ApprovalLine> lines = lineRepository.findByDocument(docId);
		if (d.getStatus() != DocStatus.IN_PROGRESS
				|| lines.stream().anyMatch(l -> l.getStatus() == LineStatus.APPROVED)) {
			throw new BusinessException("이미 결재가 진행된 문서는 회수할 수 없습니다.");
		}
		lines.forEach(ApprovalLine::reset);
		d.withdraw();
	}

	// ---------- 내부 ----------

	/** 결재가 끝났다: 기안자와 참조자에게 알리고, 연결된 기능에 결과를 넘긴다 */
	private void finish(ApprovalDocument doc, DocStatus result) {
		doc.complete(result);
		String message = "'" + doc.getTitle() + "' 문서가 " + (result == DocStatus.APPROVED ? "승인" : "반려") + "되었습니다.";
		notify(doc.getDrafter(), doc, message);
		ccRepository.findByDocument(doc.getId()).forEach(c -> notify(c.getMember(), doc, message));
		events.publishEvent(new ApprovalDecidedEvent(doc.getId(), result));
	}

	/** 결재 차례가 된 사람과 그 대결자에게 알린다 */
	private void notifyTurn(ApprovalLine line) {
		ApprovalDocument doc = line.getDocument();
		String title = doc.getDrafter().getName() + " 님의 '" + doc.getTitle() + "' 문서";
		String verb = line.getKind().getLabel();
		notify(line.getApprover(), doc, title + "를 " + verb + "할 차례입니다.");
		for (ApprovalDelegation d : delegationRepository.findActiveByDelegator(line.getApprover().getId(), LocalDate.now(clock))) {
			if (!d.getDelegate().getId().equals(doc.getDrafter().getId())) {
				notify(d.getDelegate(), doc, title + "를 " + line.getApprover().getName() + " 님 대신 " + verb + "할 수 있습니다.");
			}
		}
	}

	private void notify(Member to, ApprovalDocument doc, String message) {
		notificationService.send(to, "결재", message, "/approval/docs/" + doc.getId());
	}

	/** 오늘 나에게 대결을 맡긴 사람들. 쿼리의 in 절이 비지 않도록 없으면 0 을 넣는다 */
	private List<Long> delegators(Long memberId) {
		List<Long> ids = delegationRepository.findActiveDelegatorIds(memberId, LocalDate.now(clock));
		return ids.isEmpty() ? List.of(0L) : ids;
	}

	/** 결재자 본인이거나, 오늘 그 사람의 대결자 (자기가 기안한 문서는 제외) */
	private boolean canAct(ApprovalLine line, Member me) {
		if (line.getApprover().getId().equals(me.getId())) {
			return true;
		}
		return !line.getDocument().getDrafter().getId().equals(me.getId())
			&& delegators(me.getId()).contains(line.getApprover().getId());
	}

	/** 대결이면 대신 결재하는 나, 본인 결재면 null */
	private static Member deputyOf(ApprovalLine line, Member me) {
		return line.getApprover().getId().equals(me.getId()) ? null : me;
	}

	/**
	 * 문서 행을 잠근다 (select ... for update). 승인·전결·반려·회수·취소는 모두 이것부터 하므로,
	 * 같은 문서에 동시에 들어온 처리는 앞의 것이 커밋될 때까지 기다렸다가 차례로 처리된다.
	 * 병렬 합의자 두 명이 동시에 승인해도 뒤에 처리되는 쪽이 앞사람의 승인을 보고 다음 단계를 연다.
	 * <p>
	 * 트랜잭션의 첫 조회여야 한다. MariaDB(REPEATABLE READ)는 첫 일반 조회 시점의 스냅샷으로 이후를 읽으므로,
	 * 잠금보다 먼저 다른 조회를 하면 잠금을 얻은 뒤에도 앞사람의 승인이 안 보일 수 있다.
	 */
	private ApprovalDocument lock(Long docId) {
		return documentRepository.findForUpdate(docId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** 잠근 문서에서 지금 내 차례인지 확인하고 결재선을 돌려준다 */
	private List<ApprovalLine> myTurn(ApprovalDocument d, Member me) {
		List<ApprovalLine> lines = lineRepository.findByDocument(d.getId());
		boolean mine = d.getStatus() == DocStatus.IN_PROGRESS && actionableLine(lines, me).isPresent();
		if (!mine) {
			throw new BusinessException("지금 결재할 차례가 아닙니다.");
		}
		return lines;
	}

	/**
	 * 지금 차례인 줄 중 내가 처리할 수 있는 줄. 병렬 합의면 차례인 줄이 여럿이므로,
	 * 내 줄이 있으면 그것을, 없으면 대결로 맡은 줄을 고른다.
	 */
	private Optional<ApprovalLine> actionableLine(List<ApprovalLine> lines, Member me) {
		List<ApprovalLine> pending = lines.stream().filter(l -> l.getStatus() == LineStatus.PENDING).toList();
		return pending.stream().filter(l -> l.getApprover().getId().equals(me.getId())).findFirst()
			.or(() -> pending.stream().filter(l -> canAct(l, me)).findFirst());
	}

	private ApprovalDocument editableDraft(Long docId, String loginId) {
		ApprovalDocument d = documentRepository.findDetail(docId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!d.getDrafter().getLoginId().equals(loginId)) {
			throw new AccessDeniedException("기안자만 수정할 수 있습니다.");
		}
		if (d.getStatus() != DocStatus.DRAFT) {
			throw new BusinessException("상신된 문서는 수정할 수 없습니다. 먼저 회수하세요.");
		}
		return d;
	}

	private Member member(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}

	private static String deptName(Member m) {
		return m.getDepartment() == null ? null : m.getDepartment().getName();
	}

	private static String gradeName(Member m) {
		return m.getGrade() == null ? null : m.getGrade().getName();
	}

	private static String trimToEmpty(String s) {
		return s == null ? "" : s.trim();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}

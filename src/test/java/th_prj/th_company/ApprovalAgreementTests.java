package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.approval.ApprovalBox;
import th_prj.th_company.approval.ApprovalDocumentRepository;
import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.approval.DocRow;
import th_prj.th_company.approval.DocStatus;
import th_prj.th_company.approval.DocView;
import th_prj.th_company.approval.DraftForm;
import th_prj.th_company.approval.LineStatus;
import th_prj.th_company.approval.LineType;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;

/**
 * 결재선의 합의(순차, 병렬).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalAgreementTests {

	@Autowired private MockMvc mvc;
	@Autowired private ApprovalService approvalService;
	@Autowired private ApprovalDocumentRepository documentRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String drafter;
	private Member agreeA;
	private Member agreeB;
	private Member agreeC;
	private Member boss;

	@BeforeEach
	void setUp() {
		String s = UUID.randomUUID().toString().substring(0, 6);
		Department dept = departmentRepository.save(new Department("합의팀" + s, null, 0));
		drafter = member("drafter" + s, "기안자", dept).getLoginId();
		agreeA = member("agreeA" + s, "재무", dept);
		agreeB = member("agreeB" + s, "법무", dept);
		agreeC = member("agreeC" + s, "인사", dept);
		boss = member("boss" + s, "대표", dept);
	}

	private Member member(String loginId, String name, Department dept) {
		Member m = new Member(loginId, passwordEncoder.encode("pw"), name, Role.USER);
		m.updateProfile(name, null, null, null, dept, null, null, null);
		return memberRepository.save(m);
	}

	private DraftForm form(String title, List<Long> ids, List<LineType> types) {
		DraftForm form = approvalService.newDraft(approvalService.templates().get(0).getId());
		form.setTitle(title);
		form.setApproverIds(ids);
		form.setLineTypes(types.stream().map(Enum::name).toList());
		return form;
	}

	private List<Long> pending(Member m) {
		return approvalService.box(ApprovalBox.PENDING, m.getLoginId(), 0).map(DocRow::id).getContent();
	}

	private DocStatus status(Long id) {
		return documentRepository.findById(id).orElseThrow().getStatus();
	}

	@Test
	void parallelAgreementWaitsForEveryoneThenGoesToApprover() {
		Long id = approvalService.submit(form("병렬 합의", List.of(agreeA.getId(), agreeB.getId(), boss.getId()),
			List.of(LineType.AGREE_PAR, LineType.AGREE_PAR, LineType.APPROVAL)), drafter);

		// 두 합의자가 동시에 차례
		assertThat(pending(agreeA)).contains(id);
		assertThat(pending(agreeB)).contains(id);
		assertThat(pending(boss)).doesNotContain(id);
		DocRow row = approvalService.box(ApprovalBox.PROGRESS, drafter, 0).getContent().stream()
			.filter(r -> r.id().equals(id)).findFirst().orElseThrow();
		assertThat(row.currentApprover()).contains("재무").contains("법무");

		DocView view = approvalService.view(id, agreeA.getLoginId());
		assertThat(view.agreeing()).isTrue();
		assertThat(view.canFinalApprove()).isFalse();
		assertThat(view.agreementLines()).hasSize(2).allMatch(DocView.LineView::parallel);
		assertThat(view.approvalLines()).hasSize(1);

		approvalService.approve(id, agreeB.getLoginId(), "법무 검토 완료");
		assertThat(pending(boss)).doesNotContain(id);
		assertThat(pending(agreeA)).contains(id);

		approvalService.approve(id, agreeA.getLoginId(), null);
		assertThat(pending(boss)).contains(id);
		assertThat(approvalService.view(id, boss.getLoginId()).agreeing()).isFalse();

		approvalService.approve(id, boss.getLoginId(), null);
		assertThat(status(id)).isEqualTo(DocStatus.APPROVED);
		assertThat(approvalService.view(id, drafter).agreementLines())
			.extracting(DocView.LineView::statusLabel).containsOnly("합의");
	}

	@Test
	void sequentialAgreementGoesOneByOne() {
		Long id = approvalService.submit(form("순차 합의", List.of(agreeA.getId(), agreeB.getId(), boss.getId()),
			List.of(LineType.AGREE_SEQ, LineType.AGREE_SEQ, LineType.APPROVAL)), drafter);

		assertThat(pending(agreeA)).contains(id);
		assertThat(pending(agreeB)).doesNotContain(id);
		assertThatThrownBy(() -> approvalService.approve(id, agreeB.getLoginId(), null))
			.isInstanceOf(BusinessException.class);

		approvalService.approve(id, agreeA.getLoginId(), null);
		assertThat(pending(agreeB)).contains(id);
		approvalService.approve(id, agreeB.getLoginId(), null);
		assertThat(pending(boss)).contains(id);
	}

	@Test
	void approvalThenParallelThenSequentialKeepsOrder() {
		// 결재 → 병렬(A, B) → 순차(C) → 최종 결재
		Member first = agreeC;
		Member last = member("last" + UUID.randomUUID().toString().substring(0, 6), "사장",
			memberRepository.findByLoginId(drafter).orElseThrow().getDepartment());
		Long id = approvalService.submit(form("섞인 결재선",
			List.of(boss.getId(), agreeA.getId(), agreeB.getId(), first.getId(), last.getId()),
			List.of(LineType.APPROVAL, LineType.AGREE_PAR, LineType.AGREE_PAR, LineType.AGREE_SEQ, LineType.APPROVAL)), drafter);

		List<DocView.LineView> lines = approvalService.view(id, drafter).lines();
		assertThat(lines).extracting(DocView.LineView::stage).containsExactly(1, 2, 2, 3, 4);
		assertThat(lines).extracting(DocView.LineView::stageLabel).containsExactly("1차", "2차 병렬", "2차 병렬", "3차", "4차");

		approvalService.approve(id, boss.getLoginId(), null);
		assertThat(pending(agreeA)).contains(id);
		assertThat(pending(agreeB)).contains(id);
		assertThat(pending(first)).doesNotContain(id);
	}

	@Test
	void agreerRejectingRejectsDocumentAndStopsOthers() {
		Long id = approvalService.submit(form("합의 반려", List.of(agreeA.getId(), agreeB.getId(), boss.getId()),
			List.of(LineType.AGREE_PAR, LineType.AGREE_PAR, LineType.APPROVAL)), drafter);

		approvalService.reject(id, agreeA.getLoginId(), "예산 초과");
		assertThat(status(id)).isEqualTo(DocStatus.REJECTED);
		assertThat(pending(agreeB)).doesNotContain(id);
		assertThat(approvalService.view(id, drafter).lines())
			.extracting(DocView.LineView::status)
			.containsExactly(LineStatus.REJECTED, LineStatus.WAITING, LineStatus.WAITING);

		// 재기안하면 합의 방식도 그대로
		Long copy = approvalService.redraft(id, drafter);
		assertThat(approvalService.getDraft(copy, drafter).getLineTypes())
			.containsExactly("AGREE_PAR", "AGREE_PAR", "APPROVAL");
	}

	@Test
	void agreerCannotFinalApprove_andLastMustBeApprover() {
		Long id = approvalService.submit(form("전결 불가", List.of(agreeA.getId(), boss.getId()),
			List.of(LineType.AGREE_SEQ, LineType.APPROVAL)), drafter);
		assertThatThrownBy(() -> approvalService.approveFinal(id, agreeA.getLoginId(), null))
			.isInstanceOf(BusinessException.class).hasMessageContaining("합의자는 전결");

		assertThatThrownBy(() -> approvalService.submit(form("합의로 끝남", List.of(boss.getId(), agreeA.getId()),
			List.of(LineType.APPROVAL, LineType.AGREE_SEQ)), drafter))
			.isInstanceOf(BusinessException.class).hasMessageContaining("최종 결재자");

		// 방식을 안 보내면 모두 결재 (예전 화면, 휴가 신청)
		Long plain = approvalService.submit(form("방식 없음", List.of(agreeA.getId(), boss.getId()), List.of()), drafter);
		assertThat(approvalService.view(plain, drafter).agreementLines()).isEmpty();
	}

	@Test
	void documentPageShowsAgreementBoxAndAgreeButton() throws Exception {
		Long id = approvalService.submit(form("화면 확인", List.of(agreeA.getId(), boss.getId()),
			List.of(LineType.AGREE_SEQ, LineType.APPROVAL)), drafter);
		MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user(agreeA.getLoginId()).password("pw"))
			.andReturn().getRequest().getSession();
		String html = mvc.perform(get("/approval/docs/{id}", id).session(session))
			.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("합<br>의", "data-decide=\"합의\"").doesNotContain("data-decide=\"전결\"");
	}
}

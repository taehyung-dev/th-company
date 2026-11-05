package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
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
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalTests {

	@Autowired private MockMvc mvc;
	@Autowired private ApprovalService approvalService;
	@Autowired private ApprovalDocumentRepository documentRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String drafter;
	private Member first;
	private Member second;
	private String outsider;

	@BeforeEach
	void setUp() {
		String s = UUID.randomUUID().toString().substring(0, 6);
		Department dept = departmentRepository.save(new Department("결재팀" + s, null, 0));
		drafter = member("drafter" + s, "기안자", dept).getLoginId();
		first = member("first" + s, "팀장", dept);
		second = member("second" + s, "본부장", dept);
		outsider = member("outsider" + s, "외부인", dept).getLoginId();
	}

	private Member member(String loginId, String name, Department dept) {
		Member m = new Member(loginId, passwordEncoder.encode("pw"), name, Role.USER);
		m.updateProfile(name, null, null, null, dept, null, null, null);
		return memberRepository.save(m);
	}

	private Long templateId() {
		return approvalService.templates().get(0).getId();
	}

	private DraftForm form(String title, List<Long> approvers) {
		DraftForm form = approvalService.newDraft(templateId());
		form.setTitle(title);
		form.setApproverIds(approvers);
		return form;
	}

	private List<Long> box(ApprovalBox box, String loginId) {
		return approvalService.box(box, loginId, 0).map(DocRow::id).getContent();
	}

	@Test
	void proposalTemplateIsSeeded() {
		assertThat(approvalService.templates()).extracting("name").contains("품의서");
		assertThat(approvalService.newDraft(templateId()).getContent()).contains("1. 목적");
	}

	@Test
	void twoStepApproval_movesThroughBoxes() {
		Long id = approvalService.submit(form("노트북 구매 품의", List.of(first.getId(), second.getId())), drafter);

		assertThat(documentRepository.findById(id).orElseThrow().getDocNo())
			.startsWith("품의-" + Year.now().getValue() + "-");
		assertThat(box(ApprovalBox.PENDING, first.getLoginId())).contains(id);
		assertThat(box(ApprovalBox.PROGRESS, drafter)).contains(id);
		assertThat(box(ApprovalBox.PROGRESS, second.getLoginId())).contains(id);
		assertThat(box(ApprovalBox.PENDING, second.getLoginId())).doesNotContain(id);

		// 2단계는 아직 차례가 아님
		assertThatThrownBy(() -> approvalService.approve(id, second.getLoginId(), null))
			.isInstanceOf(BusinessException.class);

		approvalService.approve(id, first.getLoginId(), "확인했습니다");
		assertThat(box(ApprovalBox.PENDING, first.getLoginId())).doesNotContain(id);
		assertThat(box(ApprovalBox.PROGRESS, first.getLoginId())).contains(id);
		assertThat(box(ApprovalBox.PENDING, second.getLoginId())).contains(id);

		approvalService.approve(id, second.getLoginId(), null);
		DocView view = approvalService.view(id, drafter);
		assertThat(view.status()).isEqualTo(DocStatus.APPROVED);
		assertThat(view.completedAt()).isNotNull();
		assertThat(view.lines()).extracting(DocView.LineView::status)
			.containsExactly(LineStatus.APPROVED, LineStatus.APPROVED);
		assertThat(view.lines().get(0).comment()).isEqualTo("확인했습니다");
		for (String who : List.of(drafter, first.getLoginId(), second.getLoginId())) {
			assertThat(box(ApprovalBox.COMPLETED, who)).contains(id);
		}
	}

	@Test
	void rejectNeedsReason_andEndsDocument() {
		Long id = approvalService.submit(form("반려될 문서", List.of(first.getId(), second.getId())), drafter);

		assertThatThrownBy(() -> approvalService.reject(id, first.getLoginId(), " "))
			.isInstanceOf(BusinessException.class).hasMessageContaining("반려 사유");
		approvalService.reject(id, first.getLoginId(), "예산 초과");

		DocView view = approvalService.view(id, drafter);
		assertThat(view.status()).isEqualTo(DocStatus.REJECTED);
		assertThat(view.lines()).extracting(DocView.LineView::status)
			.containsExactly(LineStatus.REJECTED, LineStatus.WAITING);
		assertThat(box(ApprovalBox.COMPLETED, drafter)).contains(id);
	}

	@Test
	void withdrawOnlyBeforeAnyApproval_thenEditAndResubmit() {
		Long id = approvalService.submit(form("회수할 문서", List.of(first.getId(), second.getId())), drafter);
		approvalService.withdraw(id, drafter);
		assertThat(box(ApprovalBox.DRAFTS, drafter)).contains(id);
		assertThat(box(ApprovalBox.PENDING, first.getLoginId())).doesNotContain(id);

		DraftForm edit = approvalService.getDraft(id, drafter);
		assertThat(edit.getApproverIds()).containsExactly(first.getId(), second.getId());
		edit.setTitle("수정 후 재상신");
		approvalService.submit(edit, drafter);
		String docNo = documentRepository.findById(id).orElseThrow().getDocNo();

		approvalService.approve(id, first.getLoginId(), null);
		assertThatThrownBy(() -> approvalService.withdraw(id, drafter))
			.isInstanceOf(BusinessException.class);
		assertThat(documentRepository.findById(id).orElseThrow().getDocNo()).isEqualTo(docNo);
	}

	@Test
	void draftRulesAndPermissions() {
		// 결재선 없이 임시저장은 가능, 상신은 불가
		Long draftId = approvalService.saveDraft(form("임시", List.of()), drafter);
		assertThat(box(ApprovalBox.DRAFTS, drafter)).contains(draftId);
		DraftForm noLine = approvalService.getDraft(draftId, drafter);
		assertThatThrownBy(() -> approvalService.submit(noLine, drafter))
			.hasMessageContaining("결재자를 한 명 이상");

		Long me = memberRepository.findByLoginId(drafter).orElseThrow().getId();
		assertThatThrownBy(() -> approvalService.submit(form("자기결재", List.of(me)), drafter))
			.hasMessageContaining("본인을 결재자로");
		assertThatThrownBy(() -> approvalService.submit(form("중복", List.of(first.getId(), first.getId())), drafter))
			.hasMessageContaining("두 번");

		// 결재선에 없는 사람은 볼 수 없고, 남의 임시저장 문서도 볼 수 없음
		Long id = approvalService.submit(form("기밀 품의", List.of(first.getId())), drafter);
		assertThatThrownBy(() -> approvalService.view(id, outsider)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> approvalService.view(draftId, first.getLoginId())).isInstanceOf(AccessDeniedException.class);

		approvalService.deleteDraft(draftId, drafter);
		assertThat(documentRepository.findById(draftId)).isEmpty();
	}

	@Test
	void pagesRenderAndApproveThroughWeb() throws Exception {
		MockHttpSession drafterSession = (MockHttpSession) mvc.perform(formLogin("/login").user(drafter).password("pw"))
			.andReturn().getRequest().getSession();
		for (ApprovalBox b : ApprovalBox.values()) {
			mvc.perform(get("/approval/box/{p}", b.getPath()).session(drafterSession)).andExpect(status().isOk());
		}
		mvc.perform(get("/approval/new").session(drafterSession)).andExpect(status().is3xxRedirection());
		mvc.perform(get("/approval/new/{t}", templateId()).session(drafterSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("1. 목적")));

		String location = mvc.perform(post("/approval/drafts").session(drafterSession).with(csrf())
				.param("templateId", templateId().toString()).param("title", "웹 상신")
				.param("content", "내용").param("approverIds", first.getId().toString()).param("action", "submit"))
			.andExpect(status().is3xxRedirection())
			.andReturn().getResponse().getRedirectedUrl();
		Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

		MockHttpSession firstSession = (MockHttpSession) mvc.perform(formLogin("/login").user(first.getLoginId()).password("pw"))
			.andReturn().getRequest().getSession();
		String viewHtml = mvc.perform(get("/approval/docs/{id}", id).session(firstSession))
			.andExpect(content().string(containsString("승인")))
			.andExpect(content().string(containsString("웹 상신")))
			.andReturn().getResponse().getContentAsString();

		// 브라우저처럼 화면의 승인 폼에 들어 있는 CSRF 토큰으로 제출한다 (.with(csrf()) 를 쓰면 토큰 누락을 못 잡음)
		String decideForm = viewHtml.substring(viewHtml.indexOf("class=\"decide-form\""));
		decideForm = decideForm.substring(0, decideForm.indexOf("</form>"));
		java.util.regex.Matcher token = java.util.regex.Pattern
			.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(decideForm);
		assertThat(token.find()).as("승인/반려 폼에 CSRF 토큰이 있어야 함").isTrue();
		mvc.perform(post("/approval/docs/{id}/approve", id).session(firstSession).param("_csrf", token.group(1)))
			.andExpect(status().is3xxRedirection());
		assertThat(documentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(DocStatus.APPROVED);

		MockHttpSession outsiderSession = (MockHttpSession) mvc.perform(formLogin("/login").user(outsider).password("pw"))
			.andReturn().getRequest().getSession();
		mvc.perform(get("/approval/docs/{id}", id).session(outsiderSession)).andExpect(status().isForbidden());
	}
}

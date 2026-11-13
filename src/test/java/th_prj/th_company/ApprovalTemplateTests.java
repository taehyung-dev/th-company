package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import th_prj.th_company.approval.ApprovalDocument;
import th_prj.th_company.approval.ApprovalDocumentRepository;
import th_prj.th_company.approval.ApprovalTemplate;
import th_prj.th_company.approval.ApprovalTemplateAdminService;
import th_prj.th_company.approval.ApprovalTemplateForm;
import th_prj.th_company.approval.ApprovalTemplateRepository;
import th_prj.th_company.approval.FieldType;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalTemplateTests {

	@Autowired private MockMvc mvc;
	@Autowired private ApprovalTemplateRepository templateRepository;
	@Autowired private ApprovalTemplateAdminService adminService;
	@Autowired private ApprovalDocumentRepository documentRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private String suffix;
	private final List<Long> created = new ArrayList<>();

	@BeforeEach
	void setUp() {
		suffix = UUID.randomUUID().toString().substring(0, 5);
	}

	/** 다른 테스트(양식이 하나면 바로 작성 화면으로 가는지 등)에 영향을 주지 않도록 만든 양식은 사용 중지 */
	@AfterEach
	void disableCreated() {
		for (Long id : created) {
			ApprovalTemplateForm f = adminService.getForm(id);
			f.setEnabled(false);
			adminService.save(f);
		}
	}

	private MockHttpSession login(String loginId, String password) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password(password))
			.andReturn().getRequest().getSession();
	}

	private Member member(String prefix, Department dept) {
		Member m = new Member(prefix + suffix, passwordEncoder.encode("pw"), prefix, Role.USER);
		m.updateProfile(prefix, null, null, null, dept, null, null, null);
		return memberRepository.save(m);
	}

	private MockHttpServletRequestBuilder leaveTemplate(MockHttpSession admin, String code) {
		return post("/admin/approval-templates").session(admin).with(csrf())
			.param("name", "휴가신청서" + suffix).param("code", code).param("sortOrder", "900").param("enabled", "true")
			.param("contentTemplate", "위와 같이 휴가를 신청합니다.")
			.param("fields[0].label", "휴가 종류").param("fields[0].type", "SELECT").param("fields[0].required", "true")
			.param("fields[0].options", "연차, 반차 ,병가,연차")
			.param("fields[1].label", "시작일").param("fields[1].type", "DATE").param("fields[1].required", "true")
			.param("fields[2].label", "").param("fields[2].type", "TEXT")
			.param("fields[3].label", "경비").param("fields[3].type", "MONEY");
	}

	@Test
	void 관리자가_입력항목_양식을_만들고_사용자가_값을_넣어_기안하면_문서는_당시_양식을_보존한다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		String code = "휴" + suffix.replaceAll("[^a-z0-9]", "");

		mvc.perform(get("/admin/approval-templates/new").session(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("기안 화면 미리보기")));

		String location = mvc.perform(leaveTemplate(admin, code))
			.andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
		Long templateId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
		created.add(templateId);

		ApprovalTemplate t = templateRepository.findById(templateId).orElseThrow();
		// 이름이 빈 줄은 버리고, 선택지는 정리(공백·중복 제거)
		assertThat(t.getFields()).extracting("label").containsExactly("휴가 종류", "시작일", "경비");
		assertThat(t.getFields().get(0).optionList()).containsExactly("연차", "반차", "병가");
		assertThat(t.getFields().get(2).getType()).isEqualTo(FieldType.MONEY);

		// 같은 문서번호 앞자리, 선택지 없는 선택 항목, 같은 항목 이름은 거절
		mvc.perform(post("/admin/approval-templates").session(admin).with(csrf())
				.param("name", "다른양식").param("code", code).param("sortOrder", "901"))
			.andExpect(content().string(containsString("다른 양식이 쓰고 있습니다")));
		mvc.perform(post("/admin/approval-templates").session(admin).with(csrf())
				.param("name", "다른양식").param("code", "X" + suffix.substring(0, 3)).param("sortOrder", "901")
				.param("fields[0].label", "구분").param("fields[0].type", "SELECT"))
			.andExpect(content().string(containsString("선택지를 쉼표로 구분해")));
		mvc.perform(post("/admin/approval-templates").session(admin).with(csrf())
				.param("name", "다른양식").param("code", "Y" + suffix.substring(0, 3)).param("sortOrder", "901")
				.param("fields[0].label", "날짜").param("fields[1].label", "날짜"))
			.andExpect(content().string(containsString("두 번 있습니다")));
		mvc.perform(post("/admin/approval-templates").session(admin).with(csrf())
				.param("name", "다른양식").param("code", "품의-1").param("sortOrder", "901"))
			.andExpect(content().string(containsString("한글, 영문, 숫자로 10자 이하")));

		// 사용자 기안: 작성 화면에 항목이 나오고, 필수/형식 검사는 상신할 때
		Department dept = departmentRepository.save(new Department("양식팀" + suffix, null, 0));
		Member drafter = member("tdraft", dept);
		Member boss = member("tboss", dept);
		MockHttpSession ds = login(drafter.getLoginId(), "pw");
		mvc.perform(get("/approval/new/{id}", templateId).session(ds))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("fieldValues[0]")))
			.andExpect(content().string(containsString("반차")))
			.andExpect(content().string(containsString("data-money")));

		mvc.perform(post("/approval/drafts").session(ds).with(csrf())
				.param("templateId", templateId.toString()).param("title", "휴가 신청")
				.param("fieldValues[0]", "").param("fieldValues[1]", "2031-06-01").param("fieldValues[2]", "")
				.param("approverIds", boss.getId().toString()).param("action", "submit"))
			.andExpect(content().string(containsString("&#39;휴가 종류&#39; 항목을 입력하세요")));
		mvc.perform(post("/approval/drafts").session(ds).with(csrf())
				.param("templateId", templateId.toString()).param("title", "휴가 신청")
				.param("fieldValues[0]", "출장").param("fieldValues[1]", "2031-06-01").param("fieldValues[2]", "")
				.param("approverIds", boss.getId().toString()).param("action", "submit"))
			.andExpect(content().string(containsString("목록에서 선택하세요")));
		mvc.perform(post("/approval/drafts").session(ds).with(csrf())
				.param("templateId", templateId.toString()).param("title", "휴가 신청")
				.param("fieldValues[0]", "연차").param("fieldValues[1]", "6월 1일").param("fieldValues[2]", "")
				.param("approverIds", boss.getId().toString()).param("action", "submit"))
			.andExpect(content().string(containsString("날짜가 올바르지 않습니다")));

		// 본문 없이 상신 가능 (입력 항목이 있는 양식), 금액은 쉼표를 빼고 저장
		String docLocation = mvc.perform(post("/approval/drafts").session(ds).with(csrf())
				.param("templateId", templateId.toString()).param("title", "휴가 신청").param("content", "")
				.param("fieldValues[0]", "연차").param("fieldValues[1]", "2031-06-01").param("fieldValues[2]", "1,500,000")
				.param("approverIds", boss.getId().toString()).param("action", "submit"))
			.andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
		Long docId = Long.valueOf(docLocation.substring(docLocation.lastIndexOf('/') + 1));
		ApprovalDocument doc = documentRepository.findById(docId).orElseThrow();
		assertThat(doc.getDocNo()).startsWith(code + "-");

		mvc.perform(get("/approval/docs/{id}", docId).session(ds))
			.andExpect(content().string(containsString("휴가 종류")))
			.andExpect(content().string(containsString("1,500,000원")));

		// 문서가 생긴 뒤: 앞자리 변경·삭제 불가, 항목을 바꿔도 기존 문서는 그대로
		mvc.perform(post("/admin/approval-templates").session(admin).with(csrf())
				.param("id", templateId.toString()).param("name", "휴가신청서" + suffix).param("code", code + "2")
				.param("sortOrder", "900").param("enabled", "true"))
			.andExpect(content().string(containsString("문서번호 앞자리는 바꿀 수 없습니다")));
		mvc.perform(post("/admin/approval-templates/{id}/delete", templateId).session(admin).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(templateRepository.findById(templateId)).isPresent();

		mvc.perform(post("/admin/approval-templates").session(admin).with(csrf())
				.param("id", templateId.toString()).param("name", "휴가신청서" + suffix).param("code", code)
				.param("sortOrder", "900").param("enabled", "true")
				.param("fields[0].label", "사유").param("fields[0].type", "TEXTAREA"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/approval/docs/{id}", docId).session(ds))
			.andExpect(content().string(containsString("휴가 종류")))
			.andExpect(content().string(containsString("2031-06-01")));
	}

	@Test
	void 쓰지_않은_양식만_삭제되고_일반사용자는_관리할_수_없다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		String code = "삭" + suffix.replaceAll("[^a-z0-9]", "");
		String location = mvc.perform(leaveTemplate(admin, code)).andReturn().getResponse().getRedirectedUrl();
		Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

		Member plain = member("tplain", null);
		MockHttpSession ps = login(plain.getLoginId(), "pw");
		mvc.perform(get("/admin/approval-templates").session(ps)).andExpect(status().isForbidden());
		mvc.perform(post("/admin/approval-templates/{id}/delete", id).session(ps).with(csrf()))
			.andExpect(status().isForbidden());

		mvc.perform(post("/admin/approval-templates/{id}/delete", id).session(admin).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(templateRepository.findById(id)).isEmpty();
	}
}

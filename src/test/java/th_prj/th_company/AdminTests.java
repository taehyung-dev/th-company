package th_prj.th_company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRepository;
import th_prj.th_company.org.HrCode;
import th_prj.th_company.org.HrCodeRepository;
import th_prj.th_company.org.HrCodeType;

@SpringBootTest
@AutoConfigureMockMvc
class AdminTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private HrCodeRepository hrCodeRepository;

	private MockHttpSession login(String loginId, String password) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin("/login").user(loginId).password(password))
			.andExpect(authenticated())
			.andReturn().getRequest().getSession();
	}

	@Test
	void 관리자_화면이_열린다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(get("/admin").session(admin)).andExpect(status().is3xxRedirection());
		mvc.perform(get("/admin/members").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/admin/members/new").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/admin/departments").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/admin/codes/grade").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/admin/codes/duty").session(admin)).andExpect(status().isOk());
		mvc.perform(get("/admin/codes/unknown").session(admin)).andExpect(status().isNotFound());
	}

	@Test
	void 부서와_직급으로_사용자를_만들면_로그인은_되지만_관리자화면은_못_연다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");

		mvc.perform(post("/admin/departments").session(admin).with(csrf())
				.param("name", "개발팀").param("sortOrder", "1"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(post("/admin/codes/grade").session(admin).with(csrf())
				.param("name", "대리").param("sortOrder", "2"))
			.andExpect(status().is3xxRedirection());

		Long deptId = departmentRepository.findAll().stream()
			.filter(d -> d.getName().equals("개발팀")).findFirst().orElseThrow().getId();
		Long gradeId = hrCodeRepository.findByTypeOrderBySortOrderAscNameAsc(HrCodeType.GRADE).stream()
			.filter(c -> c.getName().equals("대리")).findFirst().orElseThrow().getId();

		mvc.perform(post("/admin/members").session(admin).with(csrf())
				.param("loginId", "hong").param("password", "pw1234").param("name", "홍길동")
				.param("employeeNo", "E001").param("departmentId", deptId.toString())
				.param("gradeId", gradeId.toString()).param("role", "USER").param("enabled", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", "사용자를 등록했습니다."));

		mvc.perform(get("/admin/members").session(admin).param("q", "홍"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("개발팀")))
			.andExpect(content().string(containsString("대리")));

		// 일반 사용자는 로그인 가능, 관리자 페이지는 403
		MockHttpSession user = login("hong", "pw1234");
		mvc.perform(get("/admin/members").session(user)).andExpect(status().isForbidden());

		// 소속 인원이 있는 부서는 삭제 불가
		mvc.perform(post("/admin/departments/{id}/delete", deptId).session(admin).with(csrf()))
			.andExpect(flash().attribute("error", containsString("소속 인원")));
		// 사용 중인 직급은 삭제 불가
		mvc.perform(post("/admin/codes/grade/{id}/delete", gradeId).session(admin).with(csrf()))
			.andExpect(flash().attribute("error", containsString("사용 중인")));
	}

	@Test
	void 중복_아이디는_거부된다() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(post("/admin/members").session(admin).with(csrf())
				.param("loginId", "sysadmin").param("password", "x").param("name", "중복")
				.param("role", "USER").param("enabled", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이미 사용 중인 아이디")));
	}

	@Test
	void 아이디_사번_이메일의_중복확인_버튼과_저장_규칙() throws Exception {
		MockHttpSession admin = login("sysadmin", "a");
		mvc.perform(post("/admin/members").session(admin).with(csrf())
				.param("loginId", "dupcheck").param("password", "pw").param("name", "중복원본")
				.param("employeeNo", "D-001").param("email", "dup@th-company.local")
				.param("role", "USER").param("enabled", "true"))
			.andExpect(status().is3xxRedirection());
		Long ownerId = memberRepository.findByLoginId("dupcheck").orElseThrow().getId();

		// [중복확인] 버튼 (대소문자 무시)
		mvc.perform(get("/admin/members/check").session(admin).param("field", "loginId").param("value", "DupCheck"))
			.andExpect(content().json("{\"available\":false,\"message\":\"이미 사용 중인 아이디입니다.\"}"));
		mvc.perform(get("/admin/members/check").session(admin).param("field", "email").param("value", "DUP@th-company.local"))
			.andExpect(content().json("{\"available\":false}"));
		mvc.perform(get("/admin/members/check").session(admin).param("field", "employeeNo").param("value", "D-999"))
			.andExpect(content().json("{\"available\":true,\"message\":\"사용할 수 있는 사번입니다.\"}"));
		// 수정 중인 본인 값은 중복 아님
		mvc.perform(get("/admin/members/check").session(admin).param("field", "email")
				.param("value", "dup@th-company.local").param("excludeId", ownerId.toString()))
			.andExpect(content().json("{\"available\":true}"));
		mvc.perform(get("/admin/members/check").session(admin).param("field", "password").param("value", "x"))
			.andExpect(status().isBadRequest());

		// 저장 시에도 막힘: 다른 사람이 쓰는 이메일로 등록 → 이메일 칸 아래 오류
		mvc.perform(post("/admin/members").session(admin).with(csrf())
				.param("loginId", "dupcheck2").param("password", "pw").param("name", "중복시도")
				.param("email", "Dup@th-company.local").param("role", "USER").param("enabled", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이미 사용 중인 이메일입니다.")));
		assertThat(memberRepository.findByLoginId("dupcheck2")).isEmpty();

		// 수정 시: 다른 사람의 사번으로 변경 불가, 본인 값 그대로 저장은 가능
		Member other = memberRepository.findByLoginId("sysadmin").orElseThrow();
		mvc.perform(post("/admin/members/{id}", other.getId()).session(admin).with(csrf())
				.param("loginId", "sysadmin").param("name", "시스템관리자").param("employeeNo", "D-001")
				.param("role", "ADMIN").param("enabled", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이미 사용 중인 사번입니다.")));
		mvc.perform(post("/admin/members/{id}", ownerId).session(admin).with(csrf())
				.param("loginId", "dupcheck").param("name", "중복원본").param("employeeNo", "D-001")
				.param("email", "dup@th-company.local").param("role", "USER").param("enabled", "true"))
			.andExpect(status().is3xxRedirection());

		// 일반 사용자는 중복확인 API 사용 불가
		mvc.perform(get("/admin/members/check").session(login("dupcheck", "pw")).param("field", "loginId").param("value", "x"))
			.andExpect(status().isForbidden());
	}

	@Test
	void 부서를_자기_하위부서_밑으로_옮길_수_없다() throws Exception {
		Department parent = departmentRepository.save(new Department("본부A", null, 0));
		Department child = departmentRepository.save(new Department("팀A", parent, 0));
		MockHttpSession admin = login("sysadmin", "a");

		mvc.perform(post("/admin/departments").session(admin).with(csrf())
				.param("id", parent.getId().toString()).param("name", "본부A")
				.param("parentId", child.getId().toString()).param("sortOrder", "0").param("enabled", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("하위 부서를 상위 부서로 지정할 수 없습니다")));
	}

	@Test
	void 관리자는_자기_계정을_사용중지할_수_없다() throws Exception {
		Member sysadmin = memberRepository.findByLoginId("sysadmin").orElseThrow();
		MockHttpSession admin = login("sysadmin", "a");

		mvc.perform(post("/admin/members/{id}", sysadmin.getId()).session(admin).with(csrf())
				.param("loginId", "sysadmin").param("name", "시스템관리자")
				.param("role", "ADMIN").param("enabled", "false"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("본인 계정은 사용 중지")));

		assertThat(memberRepository.findByLoginId("sysadmin").orElseThrow().isEnabled()).isTrue();
	}

	@Test
	void 중복_코드이름은_거부된다() throws Exception {
		hrCodeRepository.save(new HrCode(HrCodeType.DUTY, "팀장", 1));
		MockHttpSession admin = login("sysadmin", "a");

		mvc.perform(post("/admin/codes/duty").session(admin).with(csrf())
				.param("name", "팀장").param("sortOrder", "1"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("이미 등록된 직책")));
	}
}
